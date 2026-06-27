package com.cardio.controller;

import com.cardio.model.*;
import com.cardio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/patient")
@RequiredArgsConstructor
@Slf4j
public class PatientController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final AIRiskRepository aiRiskRepository;
    private final SystemLogRepository systemLogRepository;
    private final JdbcTemplate jdbcTemplate;

    private PatientProfile getCurrentPatient(UserDetails userDetails) {
        return patientRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Patient profile not found for user: " + userDetails.getUsername()));
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        List<Appointment> appointments = appointmentRepository.findByPatientOrderByScheduledDateDescTimeSlotDesc(patient);
        List<Appointment> allReference = appointmentRepository.findAll();
        Appointment.populateQueueNumbers(appointments, allReference);

        List<AIRiskPrediction> predictions = aiRiskRepository.findByPatient(patient);
        
        // Fetch latest heart rate and alerts from self monitoring
        List<Map<String, Object>> selfLogs = jdbcTemplate.queryForList(
                "SELECT CurrentHeartRate, TriggeredAlert, LogDate, Symptoms FROM Patient_Self_Monitoring WHERE PatientID = ? ORDER BY LogDate DESC LIMIT 5",
                patient.getPatientId()
        );

        Integer latestHeartRate = null;
        boolean hasActiveAlert = false;
        if (!selfLogs.isEmpty()) {
            latestHeartRate = (Integer) selfLogs.get(0).get("CurrentHeartRate");
            hasActiveAlert = Boolean.TRUE.equals(selfLogs.get(0).get("TriggeredAlert"));
        }

        String latestRiskLevel = "N/A";
        java.math.BigDecimal latestRiskScore = java.math.BigDecimal.ZERO;
        if (!predictions.isEmpty()) {
            latestRiskLevel = predictions.get(0).getRiskLevel();
            latestRiskScore = predictions.get(0).getRiskScore();
        }

        model.addAttribute("patient", patient);
        model.addAttribute("appointments", appointments.stream().limit(5).toList());
        model.addAttribute("selfLogs", selfLogs);
        model.addAttribute("latestHeartRate", latestHeartRate != null ? latestHeartRate : 0);
        model.addAttribute("latestRiskLevel", latestRiskLevel);
        model.addAttribute("latestRiskScore", latestRiskScore);
        model.addAttribute("hasActiveAlert", hasActiveAlert);
        model.addAttribute("totalAppointments", appointments.size());
        
        return "patient/dashboard";
    }

    @GetMapping("/self-declare")
    public String selfDeclareForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        
        List<Map<String, Object>> monitoringLogs = jdbcTemplate.queryForList(
                "SELECT LogID AS \"LogID\", LogDate AS \"LogDate\", CurrentHeartRate AS \"CurrentHeartRate\", Symptoms AS \"Symptoms\", TriggeredAlert AS \"TriggeredAlert\" FROM Patient_Self_Monitoring WHERE PatientID = ? ORDER BY LogDate DESC",
                patient.getPatientId()
        );

        model.addAttribute("patient", patient);
        model.addAttribute("monitoringLogs", monitoringLogs);
        return "patient/self-declare";
    }

    @PostMapping("/self-declare/save")
    public String saveSelfDeclare(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestParam("heartRate") Integer heartRate,
                                  @RequestParam("symptoms") String symptoms,
                                  RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);
        
        boolean triggeredAlert = (heartRate > 100 || heartRate < 50);
        String symLower = symptoms != null ? symptoms.toLowerCase() : "";
        if (symLower.contains("đau ngực") || symLower.contains("khó thở") || symLower.contains("ngất")
                || symLower.contains("chóng mặt") || symLower.contains("đau thắt ngực")) {
            triggeredAlert = true;
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO Patient_Self_Monitoring (PatientID, LogDate, CurrentHeartRate, Symptoms, TriggeredAlert) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)",
                    patient.getPatientId(), heartRate, symptoms, triggeredAlert
            );

            // Log action
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(patient.getUsername());
            sysLog.setAction("PATIENT_SELF_MONITORING");
            sysLog.setDetails("Bệnh nhân tự khai báo: Nhịp tim " + heartRate + " BPM, Triệu chứng: " + symptoms + " (Cảnh báo: " + (triggeredAlert ? "Có" : "Không") + ")");
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            ra.addFlashAttribute("success", "Ghi nhận chỉ số sức khỏe thành công!");
        } catch (Exception e) {
            log.error("Error saving self declare: ", e);
            ra.addFlashAttribute("error", "Lỗi lưu khai báo sức khỏe.");
        }
        return "redirect:/patient/self-declare";
    }

    @GetMapping("/records")
    public String medicalRecords(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        
        // Fetch all ConsultationRecords for the patient
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT r.RecordID, r.VisitDate, r.ConsultationNotes, r.TreatmentPlan, r.Status, " +
                "d.FullName AS DoctorName, d.Specialty AS DoctorSpecialty, " +
                "p.RiskScore, p.RiskLevel, p.RiskExplanation, p.HealthAdvice, " +
                "m.ChestPainType, m.RestingBP, m.Cholesterol, m.FastingBloodSugar, " +
                "m.RestingECG, m.MaxHeartRate, m.ExerciseAngina, m.Oldpeak, m.Slope, m.Ca, m.Thal " +
                "FROM Consultation_Record r " +
                "LEFT JOIN Doctor_Profile d ON r.DoctorID = d.DoctorID " +
                "LEFT JOIN AI_Risk_Prediction p ON r.RecordID = p.RecordID " +
                "LEFT JOIN Heart_Clinical_Metrics m ON r.RecordID = m.RecordID " +
                "WHERE r.PatientID = ? ORDER BY r.VisitDate DESC",
                patient.getPatientId()
        );

        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        return "patient/records";
    }

    @GetMapping("/book-appointment")
    public String bookAppointmentForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        List<DoctorProfile> doctors = doctorRepository.findAll();

        model.addAttribute("patient", patient);
        model.addAttribute("doctors", doctors);
        return "patient/book-appointment";
    }

    @PostMapping("/book-appointment/save")
    public String saveAppointment(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestParam("doctorId") Integer doctorId,
                                  @RequestParam("scheduledDate") String scheduledDateStr,
                                  @RequestParam("preliminaryStatus") String preliminaryStatus,
                                  RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);
        
        try {
            DoctorProfile doctor = doctorRepository.findById(doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));

            LocalDate scheduledDate = LocalDate.parse(scheduledDateStr);
            
            // Auto-overflow logic: find next consecutive day where doctor has < 8 appointments
            LocalDate targetDate = scheduledDate;
            boolean wasShifted = false;
            while (appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled") >= 8) {
                targetDate = targetDate.plusDays(1);
                wasShifted = true;
            }

            // Check if patient already has an active appointment with the same doctor on this final date
            final LocalDate finalDate = targetDate;
            boolean alreadyBooked = appointmentRepository.findAll().stream()
                .anyMatch(a -> a.getPatient().getPatientId().equals(patient.getPatientId())
                        && a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                        && a.getScheduledDate().equals(finalDate)
                        && !"Cancelled".equalsIgnoreCase(a.getStatus()));
            if (alreadyBooked) {
                ra.addFlashAttribute("error", "Bạn đã đăng ký lịch hẹn khám với bác sĩ " + doctor.getFullName() + " vào ngày này rồi.");
                return "redirect:/patient/book-appointment";
            }

            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setDoctor(doctor);
            appointment.setScheduledDate(targetDate);
            appointment.setTimeSlot(null); // No time slot until physical check-in
            appointment.setPreliminaryStatus(preliminaryStatus);
            appointment.setStatus("Pending");

            appointmentRepository.save(appointment);

            // Save system log
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(patient.getUsername());
            sysLog.setAction("PATIENT_BOOK_APPOINTMENT");
            sysLog.setDetails("Bệnh nhân đặt lịch hẹn khám với bác sĩ: " + doctor.getFullName() + " vào ngày " + targetDate.toString());
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            if (wasShifted) {
                ra.addFlashAttribute("success", "Do bác sĩ đã đầy lịch khám vào ngày đã chọn (tối đa 8 ca/ngày), lịch hẹn của bạn đã được tự động chuyển sang ngày " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ". Vui lòng chờ tiếp nhận.");
            } else {
                ra.addFlashAttribute("success", "Đặt lịch hẹn khám thành công! Vui lòng chờ phòng khám tiếp nhận.");
            }
        } catch (Exception e) {
            log.error("Error booking appointment: ", e);
            String message = e.getMessage();
            if (message != null && (message.contains("unique_doctor_slot") || message.contains("unique_patient_slot") || message.contains("ConstraintViolation") || message.contains("duplicate key"))) {
                ra.addFlashAttribute("error", "Lỗi đặt lịch hẹn: đã có lịch hẹn được đặt trước đó");
            } else {
                ra.addFlashAttribute("error", "Lỗi đặt lịch hẹn: " + e.getMessage());
            }
        }
        return "redirect:/patient/dashboard";
    }
}
