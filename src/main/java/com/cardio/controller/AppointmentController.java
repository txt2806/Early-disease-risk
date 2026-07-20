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

@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;

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
}