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
        
        // Kiểm tra xem bệnh nhân có phải mới khám lần đầu (chưa có Consultation_Record)
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Consultation_Record WHERE PatientID = ?", Integer.class, patient.getPatientId());
        boolean isNewPatient = (count == null || count == 0);

        model.addAttribute("patient", patient);
        model.addAttribute("doctors", doctors);
        model.addAttribute("isNewPatient", isNewPatient);
        return "patient/book-appointment";
    }

    @GetMapping("/available-slots")
    @ResponseBody
    public List<String> getAvailableSlots(@RequestParam(value = "doctorId", required = false) Integer doctorId,
                                          @RequestParam("date") String dateStr,
                                          @RequestParam(value = "isGPAuto", required = false, defaultValue = "false") boolean isGPAuto) {
        LocalDate date = LocalDate.parse(dateStr);
        List<LocalTime> CLINIC_SLOTS = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0)
        );

        if (isGPAuto) {
            // Lấy tất cả bác sĩ đa khoa
            List<DoctorProfile> gpDoctors = doctorRepository.findAll().stream()
                    .filter(d -> d.getSpecialty() != null && d.getSpecialty().toLowerCase().contains("đa khoa"))
                    .toList();
            
            // Một khung giờ khả dụng nếu có ít nhất 1 bác sĩ đa khoa rảnh
            return CLINIC_SLOTS.stream()
                    .filter(slot -> gpDoctors.stream().anyMatch(doc -> 
                        !appointmentRepository.existsByDoctorAndScheduledDateAndTimeSlotAndStatusNot(doc, date, slot, "Cancelled")
                    ))
                    .map(slot -> slot.toString())
                    .toList();
        } else if (doctorId != null) {
            DoctorProfile doctor = doctorRepository.findById(doctorId).orElse(null);
            if (doctor == null) return List.of();
            
            return CLINIC_SLOTS.stream()
                    .filter(slot -> !appointmentRepository.existsByDoctorAndScheduledDateAndTimeSlotAndStatusNot(doctor, date, slot, "Cancelled"))
                    .map(slot -> slot.toString())
                    .toList();
        }
        return List.of();
    }

    @PostMapping("/book-appointment/save")
    public String saveAppointment(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestParam(value = "doctorId", required = false) Integer doctorId,
                                  @RequestParam("scheduledDate") String scheduledDateStr,
                                  @RequestParam("timeSlot") String timeSlotStr,
                                  @RequestParam("preliminaryStatus") String preliminaryStatus,
                                  @RequestParam(value = "autoGP", required = false, defaultValue = "false") boolean autoGP,
                                  RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);
        
        try {
            LocalDate scheduledDate = LocalDate.parse(scheduledDateStr);
            LocalTime timeSlot = LocalTime.parse(timeSlotStr);
            
            // Kiểm tra xem bệnh nhân có phải mới khám lần đầu
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Consultation_Record WHERE PatientID = ?", Integer.class, patient.getPatientId());
            boolean isNewPatient = (count == null || count == 0);
            
            DoctorProfile doctor = null;
            if (isNewPatient || autoGP || doctorId == null || doctorId == -1) {
                // Tự động phân cho bác sĩ đa khoa
                List<DoctorProfile> gpDoctors = doctorRepository.findAll().stream()
                        .filter(d -> d.getSpecialty() != null && d.getSpecialty().toLowerCase().contains("đa khoa"))
                        .toList();
                
                // Tìm bác sĩ đa khoa rảnh trong khung giờ đó
                List<DoctorProfile> freeGPs = gpDoctors.stream()
                        .filter(doc -> !appointmentRepository.existsByDoctorAndScheduledDateAndTimeSlotAndStatusNot(doc, scheduledDate, timeSlot, "Cancelled"))
                        .toList();
                
                if (freeGPs.isEmpty()) {
                    ra.addFlashAttribute("error", "Không có Bác sĩ Đa khoa nào trống lịch vào khung giờ này. Vui lòng chọn khung giờ khác.");
                    return "redirect:/patient/book-appointment";
                }
                
                // Chọn bác sĩ có ít lịch hẹn nhất trong ngày để cân bằng tải
                doctor = freeGPs.get(0);
                long minCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, scheduledDate, "Cancelled");
                for (DoctorProfile gp : freeGPs) {
                    long c = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(gp, scheduledDate, "Cancelled");
                    if (c < minCount) {
                        minCount = c;
                        doctor = gp;
                    }
                }
            } else {
                doctor = doctorRepository.findById(doctorId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ"));
                
                // Xác thực xem bác sĩ được chọn có rảnh vào khung giờ đó không
                boolean isBusy = appointmentRepository.existsByDoctorAndScheduledDateAndTimeSlotAndStatusNot(doctor, scheduledDate, timeSlot, "Cancelled");
                if (isBusy) {
                    ra.addFlashAttribute("error", "Bác sĩ đã có lịch hẹn khác vào khung giờ này. Vui lòng chọn khung giờ khác.");
                    return "redirect:/patient/book-appointment";
                }
            }

            // Kiểm tra xem bệnh nhân đã đăng ký khám với bác sĩ này vào ngày này chưa
            boolean alreadyBooked = appointmentRepository.existsByPatientAndDoctorAndScheduledDateAndStatusNot(patient, doctor, scheduledDate, "Cancelled");
            if (alreadyBooked) {
                ra.addFlashAttribute("error", "Bạn đã đăng ký lịch hẹn khám với bác sĩ " + doctor.getFullName() + " vào ngày này rồi.");
                return "redirect:/patient/book-appointment";
            }

            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setDoctor(doctor);
            appointment.setRoomNumber(doctor.getRoomNumber());
            appointment.setScheduledDate(scheduledDate);
            appointment.setTimeSlot(timeSlot);
            appointment.setStartTime(null);
            appointment.setPreliminaryStatus(preliminaryStatus);
            appointment.setStatus("Pending");

            if (appointment.getDoctor() != null && appointment.getScheduledDate() != null) {
                Integer maxQueue = appointmentRepository.findMaxQueueNumberByDoctorAndScheduledDate(appointment.getDoctor(), appointment.getScheduledDate());
                appointment.setQueueNumber(maxQueue == null ? 1 : maxQueue + 1);
            }

            appointmentRepository.save(appointment);

            // Lưu log hệ thống
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(patient.getUsername());
            sysLog.setAction("PATIENT_BOOK_APPOINTMENT");
            sysLog.setDetails("Bệnh nhân đặt lịch khám (STT: " + appointment.getQueueNumber() + ") lúc " + timeSlot.toString() + " với bác sĩ: " + doctor.getFullName());
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            ra.addFlashAttribute("success", "Đặt lịch hẹn khám thành công lúc " + timeSlotStr + " ngày " + scheduledDateStr + "! Vui lòng chờ tiếp nhận.");
        } catch (Exception e) {
            log.error("Error booking appointment: ", e);
            ra.addFlashAttribute("error", "Lỗi đặt lịch hẹn: " + e.getMessage());
        }
        return "redirect:/patient/dashboard";
    }
}
