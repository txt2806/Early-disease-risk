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
import com.cardio.model.PatientProfile;
import com.cardio.model.DoctorProfile;
import com.cardio.repository.PatientRepository;
import com.cardio.repository.DoctorRepository;
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

    @GetMapping("/doctors")
    public ResponseEntity<?> getDoctors() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<DoctorProfile> doctors = doctorRepository.findAll();
            List<Map<String, Object>> result = new ArrayList<>();
            for (DoctorProfile d : doctors) {
                Map<String, Object> map = new HashMap<>();
                map.put("doctorId", d.getDoctorId());
                String name = d.getFullName() != null ? d.getFullName() : "BS. Bác sĩ";
                if (!name.startsWith("BS.") && !name.startsWith("Dr.")) {
                    name = "BS. " + name;
                }
                map.put("fullName", name);
                map.put("specialty", d.getSpecialty() != null && !d.getSpecialty().isBlank() ? d.getSpecialty() : "Tim mạch");
                result.add(map);
            }
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi tải danh sách bác sĩ: {}", e.getMessage());
            response.put("status", "failed");
            response.put("message", "Lỗi máy chủ khi tải danh sách bác sĩ.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/patient")
    public ResponseEntity<?> getAppointments(@RequestParam String patientId) {
        Map<String, Object> response = new HashMap<>();

        Integer pId;
        try {
            pId = Integer.parseInt(patientId);
            if (pId <= 0) pId = 1001;
        } catch (NumberFormatException e) {
            pId = 1001;
        }

        try {
            List<AppointmentViewDTO> rawAppointments = appointmentRepository.findAppointmentDetailsByPatientId(pId);

            List<Map<String, Object>> formattedAppointments = new ArrayList<>();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

            for (AppointmentViewDTO appt : rawAppointments) {
                Map<String, Object> map = new HashMap<>();

                map.put("appointmentId", appt.getAppointmentId());
                String dName = appt.getDoctorName() != null ? appt.getDoctorName() : "CardioCare Doctor";
                if (!dName.startsWith("BS.") && !dName.startsWith("Dr.")) {
                    dName = "BS. " + dName;
                }
                map.put("doctorName", dName);
                map.put("department", appt.getSpecialty() != null ? appt.getSpecialty() : "Tim mạch");

                String dateStr = appt.getScheduledDate() != null ? appt.getScheduledDate().format(dateFormatter) : "N/A";
                String timeStr = appt.getTimeSlot() != null ? appt.getTimeSlot().format(timeFormatter) : "";
                map.put("appointmentDate", dateStr);
                map.put("appointmentTime", timeStr);
                map.put("date", dateStr + (timeStr.isEmpty() ? "" : " - " + timeStr));
                map.put("status", appt.getStatus() != null ? appt.getStatus() : "Pending");

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

    @PostMapping("/book")
    public ResponseEntity<?> bookAppointment(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Object pIdObj = request.get("patientId");
            Integer patientId = pIdObj != null ? Integer.parseInt(pIdObj.toString()) : 1001;
            if (patientId <= 0) patientId = 1001;

            Object dIdObj = request.get("doctorId");
            Integer doctorId = dIdObj != null ? Integer.parseInt(dIdObj.toString()) : null;

            String dateStr = (String) request.get("date"); // YYYY-MM-DD
            String timeStr = (String) request.get("time"); // HH:mm

            if (doctorId == null) {
                response.put("status", "failed");
                response.put("message", "Vui lòng chọn bác sĩ.");
                return ResponseEntity.badRequest().body(response);
            }

            if (dateStr == null || dateStr.isBlank() || timeStr == null || timeStr.isBlank()) {
                response.put("status", "failed");
                response.put("message", "Vui lòng chọn ngày và giờ khám.");
                return ResponseEntity.badRequest().body(response);
            }

            Optional<PatientProfile> patientOpt = patientRepository.findById(patientId);
            PatientProfile patientProfile;
            if (patientOpt.isEmpty()) {
                // If patientId 1001 not found, grab first patient in DB
                List<PatientProfile> allP = patientRepository.findAll();
                if (!allP.isEmpty()) {
                    patientProfile = allP.get(0);
                } else {
                    response.put("status", "failed");
                    response.put("message", "Không tìm thấy hồ sơ bệnh nhân.");
                    return ResponseEntity.badRequest().body(response);
                }
            } else {
                patientProfile = patientOpt.get();
            }

            Optional<DoctorProfile> doctorOpt = doctorRepository.findById(doctorId);
            if (doctorOpt.isEmpty()) {
                response.put("status", "failed");
                response.put("message", "Bác sĩ không tồn tại trong hệ thống.");
                return ResponseEntity.badRequest().body(response);
            }

            Appointment appointment = new Appointment();
            appointment.setPatient(patientProfile);
            appointment.setDoctor(doctorOpt.get());
            appointment.setScheduledDate(LocalDate.parse(dateStr));
            appointment.setTimeSlot(LocalTime.parse(timeStr));
            appointment.setStatus("Pending");
            String docSpecialty = doctorOpt.get().getSpecialty();
            if (docSpecialty != null && !docSpecialty.toLowerCase().contains("đa khoa")) {
                appointment.setBookingType("Specialist");
            } else {
                appointment.setBookingType("General");
            }

            if (appointment.getDbQueueNumber() == null) {
                appointment.setDbQueueNumber(appointmentRepository.findMaxDbQueueNumber() + 1);
            }

            appointmentRepository.save(appointment);

            response.put("status", "success");
            response.put("message", "Đặt lịch khám thành công!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi đặt lịch khám: {}", e.getMessage(), e);
            response.put("status", "failed");
            response.put("message", "Lỗi máy chủ khi đặt lịch: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<Appointment> apptOpt = appointmentRepository.findById(id);
            if (apptOpt.isEmpty()) {
                response.put("status", "failed");
                response.put("message", "Không tìm thấy lịch hẹn.");
                return ResponseEntity.badRequest().body(response);
            }

            Appointment appt = apptOpt.get();
            appt.setStatus("Cancelled");
            appointmentRepository.save(appt);

            response.put("status", "success");
            response.put("message", "Hủy lịch hẹn thành công!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi hủy lịch khám {}: {}", id, e.getMessage());
            response.put("status", "failed");
            response.put("message", "Lỗi máy chủ khi hủy lịch.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}