package com.cardio.controller;

import com.cardio.dto.AppointmentViewDTO;
import com.cardio.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.cardio.model.Appointment;
import com.cardio.model.DoctorProfile;
import com.cardio.model.PatientProfile;
import com.cardio.repository.DoctorRepository;
import com.cardio.repository.PatientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/patient")
    public ResponseEntity<?> getAppointments(@RequestParam String patientId) {
        Map<String, Object> response = new HashMap<>();

        Integer pId;
        try {
            pId = Integer.parseInt(patientId);
        } catch (NumberFormatException e) {
            response.put("status", "failed");
            response.put("message", "Patient ID không hợp lệ.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            List<AppointmentViewDTO> rawAppointments = appointmentRepository.findAppointmentDetailsByPatientId(pId);

            List<Map<String, String>> formattedAppointments = new ArrayList<>();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

            for (AppointmentViewDTO appt : rawAppointments) {
                Map<String, String> map = new HashMap<>();

                map.put("doctorName", "BS. " + appt.getDoctorName());
                map.put("department", appt.getSpecialty()); // Sửa để khớp với DTO

                String dateStr = appt.getScheduledDate() != null ? appt.getScheduledDate().format(dateFormatter) : "N/A";
                String timeStr = appt.getTimeSlot() != null ? appt.getTimeSlot().format(timeFormatter) : "";
                map.put("date", dateStr + (timeStr.isEmpty() ? "" : " - " + timeStr));

                // Cải thiện logic xử lý trạng thái
                String statusVN;
                String currentStatus = appt.getStatus();
                if (currentStatus == null) {
                    statusVN = "Chưa xác định";
                } else {
                    switch (currentStatus.toLowerCase()) {
                        case "completed":
                            statusVN = "Đã khám";
                            break;
                        case "cancelled":
                            statusVN = "Đã hủy";
                            break;
                        default: // Pending, Confirmed, CheckedIn, InProgress
                            statusVN = "Sắp tới";
                            break;
                    }
                }
                map.put("status", statusVN);

                formattedAppointments.add(map);
            }

            response.put("status", "success");
            response.put("data", formattedAppointments);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Lỗi khi tải lịch khám cho patientId {}: {}", patientId, e.getMessage());
            response.put("status", "failed");
            response.put("message", "Lỗi máy chủ khi tải lịch khám.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/public-save")
    public ResponseEntity<?> publicSaveAppointment(
            @RequestParam("fullName") String fullName,
            @RequestParam("phone") String phone,
            @RequestParam("dob") String dobStr,
            @RequestParam("gender") String gender,
            @RequestParam("specialty") String specialty,
            @RequestParam(value = "doctor", required = false) String doctorName,
            @RequestParam("date") String dateStr,
            @RequestParam("timeSlot") String timeSlotStr) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 1. Validate inputs
            if (fullName == null || fullName.isBlank() ||
                    phone == null || phone.isBlank() ||
                    dobStr == null || dobStr.isBlank() ||
                    gender == null || gender.isBlank() ||
                    specialty == null || specialty.isBlank() ||
                    dateStr == null || dateStr.isBlank() ||
                    timeSlotStr == null || timeSlotStr.isBlank()) {
                response.put("status", "error");
                response.put("message", "Vui lòng nhập đầy đủ các thông tin bắt buộc.");
                return ResponseEntity.badRequest().body(response);
            }

            // 2. Parse date and timeSlot
            LocalDate scheduledDate = LocalDate.parse(dateStr);
            LocalTime timeSlot = null;
            LocalTime endTime = null;
            if (timeSlotStr.contains("-")) {
                String[] parts = timeSlotStr.split("-");
                timeSlot = LocalTime.parse(parts[0].trim());
                endTime = LocalTime.parse(parts[1].trim());
            } else {
                timeSlot = LocalTime.parse(timeSlotStr.trim());
            }

            // 2.5 Map detailed specialty to General/Specialist and target doctor specialty
            String mappedBookingType = "General";
            String targetSpecialty = "Đa khoa";
            if ("Đo điện tâm đồ (ECG)".equalsIgnoreCase(specialty.trim()) ||
                    "Siêu âm tim Doppler".equalsIgnoreCase(specialty.trim()) ||
                    "Điều trị cao huyết áp".equalsIgnoreCase(specialty.trim())) {
                mappedBookingType = "Specialist";
                targetSpecialty = "Chuyên khoa";
            }

            // 3. Find DoctorProfile by FullName matching doctorName, or fallback to matching specialty
            DoctorProfile doctor = null;
            if (doctorName != null && !doctorName.isBlank()) {
                doctor = doctorRepository.findAll().stream()
                        .filter(d -> d.getFullName().equalsIgnoreCase(doctorName.trim()))
                        .findFirst()
                        .orElse(null);
            }
            if (doctor == null) {
                final String finalTarget = targetSpecialty;
                doctor = doctorRepository.findAll().stream()
                        .filter(d -> d.getSpecialty() != null && d.getSpecialty().equalsIgnoreCase(finalTarget))
                        .findFirst()
                        .orElse(null);
            }
            if (doctor == null) {
                doctor = doctorRepository.findAll().stream().findFirst().orElse(null);
            }

            // 4. Find or create PatientProfile
            String cleanPhone = phone.trim();
            Optional<PatientProfile> patientOpt = patientRepository.findByUsernameIgnoreCase(cleanPhone);
            if (patientOpt.isEmpty()) {
                patientOpt = patientRepository.findAll().stream()
                        .filter(p -> cleanPhone.equalsIgnoreCase(p.getPhone()))
                        .findFirst();
            }

            PatientProfile patient;
            if (patientOpt.isPresent()) {
                patient = patientOpt.get();
            } else {
                patient = new PatientProfile();
                patient.setUsername(cleanPhone);
                patient.setPasswordHash(passwordEncoder.encode("123"));
                patient.setFullName(fullName.trim());
                patient.setDob(LocalDate.parse(dobStr.trim()));
                patient.setGender(gender.trim());
                patient.setPhone(cleanPhone);
                patient.setAddress("");
                patient.setStatus("ACTIVE");
                patient = patientRepository.save(patient);
            }

            // 5. Create and save Appointment
            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setDoctor(doctor);
            appointment.setScheduledDate(scheduledDate);
            appointment.setTimeSlot(timeSlot);
            appointment.setEndTime(endTime);
            appointment.setPreliminaryStatus("Đặt lịch nhanh từ Trang chủ. Dịch vụ: " + specialty.trim());
            appointment.setBookingType(mappedBookingType);
            appointment.setStatus("Pending"); // Set status as Pending for receptionist confirmation

            appointmentRepository.save(appointment);

            response.put("status", "success");
            response.put("message", "🎉 Đăng ký đặt lịch khám thành công! Bộ phận lễ tân sẽ liên hệ xác nhận qua điện thoại trong vài phút.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Lỗi khi đặt lịch hẹn nhanh: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "Có lỗi xảy ra: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}