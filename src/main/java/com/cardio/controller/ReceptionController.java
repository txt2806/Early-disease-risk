package com.cardio.controller;

import com.cardio.model.*;
import com.cardio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Controller
@RequestMapping("/reception")
@RequiredArgsConstructor
@Slf4j
public class ReceptionController {

    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final SystemLogRepository systemLogRepository;
    private final PasswordEncoder passwordEncoder;

    private StaffProfile getCurrentStaff(UserDetails userDetails) {
        return staffRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .orElseThrow(
                        () -> new RuntimeException("Staff profile not found for user: " + userDetails.getUsername()));
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "dateStr", required = false) String dateStr,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        
        LocalDate filterDate = null;
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                filterDate = LocalDate.parse(dateStr);
            } catch (Exception e) {
                log.error("Invalid date format: " + dateStr);
            }
        }
        
        List<Appointment> allSortedAppointments = sortAppointmentsForQueue(appointmentRepository.findByDateAndPatientNameOrPhone(filterDate, search));
        
        List<Appointment> allReference = appointmentRepository.findAll();
        Appointment.populateQueueNumbers(allSortedAppointments, allReference);

        int totalItems = allSortedAppointments.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        
        int fromIndex = Math.max(0, page * size);
        int toIndex = Math.min(fromIndex + size, totalItems);
        
        List<Appointment> pageAppointments = (fromIndex < totalItems) 
                ? allSortedAppointments.subList(fromIndex, toIndex) 
                : List.of();

        List<DoctorProfile> doctors = doctorRepository.findAll();
        List<PatientProfile> patients = patientRepository.findAll();

        long pendingCount = allReference.stream().filter(a -> "Pending".equalsIgnoreCase(a.getStatus())).count();
        long confirmedCount = allReference.stream().filter(a -> "Confirmed".equalsIgnoreCase(a.getStatus())).count();
        long todayCount = allReference.stream()
                .filter(a -> a.getScheduledDate().equals(LocalDate.now()))
                .count();

        model.addAttribute("staff", staff);
        model.addAttribute("appointments", pageAppointments);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", size);
        model.addAttribute("doctors", doctors);
        model.addAttribute("patients", patients);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("currentDate", LocalDate.now());
        model.addAttribute("selectedDate", dateStr);
        model.addAttribute("search", search);

        return "reception/dashboard";
    }

    @PostMapping("/appointments/assign")
    public String assignAppointment(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("appointmentId") Integer appointmentId,
            @RequestParam(value = "doctorId", required = false) Integer doctorId,
            @RequestParam(value = "roomNumber", required = false) String roomNumber,
            @RequestParam("scheduledDate") String scheduledDateStr,
            @RequestParam(value = "timeSlot", required = false) String timeSlotStr,
            @RequestParam(value = "endTime", required = false) String endTimeStr,
            @RequestParam("status") String status,
            @RequestParam(value = "bookingType", required = false) String bookingType,
            @RequestParam(value = "redirectSource", required = false, defaultValue = "dashboard") String redirectSource,
            @RequestParam(value = "redirectDate", required = false) String redirectDate,
            RedirectAttributes ra) {
        StaffProfile staff = getCurrentStaff(userDetails);

        try {
            Appointment appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            LocalDate date = LocalDate.parse(scheduledDateStr);
            LocalDate targetDate = date;
            boolean wasShifted = false;

            if (doctorId != null) {
                DoctorProfile doctor = doctorRepository.findById(doctorId)
                        .orElseThrow(() -> new RuntimeException("Doctor not found"));

                // Auto-overflow logic: find next consecutive day where doctor has < 8 appointments
                boolean isSameDoctorAndDate = appointment.getDoctor() != null &&
                        appointment.getDoctor().getDoctorId().equals(doctorId) &&
                        appointment.getScheduledDate().equals(targetDate) &&
                        !"Cancelled".equalsIgnoreCase(appointment.getStatus());

                long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled");
                if (bookedCount >= 8 && !isSameDoctorAndDate) {
                    while (true) {
                        targetDate = targetDate.plusDays(1);
                        boolean isSameOnNext = appointment.getDoctor() != null &&
                                appointment.getDoctor().getDoctorId().equals(doctorId) &&
                                appointment.getScheduledDate().equals(targetDate) &&
                                !"Cancelled".equalsIgnoreCase(appointment.getStatus());
                        long nextBookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled");
                        if (nextBookedCount < 8 || isSameOnNext) {
                            break;
                        }
                    }
                    wasShifted = true;
                }
                appointment.setDoctor(doctor);
            } else {
                appointment.setDoctor(null);
            }

            if (appointment.getDoctor() != null && (roomNumber == null || roomNumber.isBlank())) {
                appointment.setRoomNumber(appointment.getDoctor().getRoomNumber());
            } else {
                appointment.setRoomNumber(roomNumber);
            }
            appointment.setScheduledDate(targetDate);
            appointment.setStatus(status);
            if (bookingType != null) {
                appointment.setBookingType(bookingType);
            }

            // Handle Check-in arrival time
            if (timeSlotStr != null && !timeSlotStr.isBlank()) {
                appointment.setTimeSlot(LocalTime.parse(timeSlotStr));
            } else if ("CheckedIn".equalsIgnoreCase(status) && appointment.getTimeSlot() == null) {
                appointment.setTimeSlot(LocalTime.now());
            }

            // Handle Completed end time
            if (endTimeStr != null && !endTimeStr.isBlank()) {
                appointment.setEndTime(LocalTime.parse(endTimeStr));
            } else if ("Completed".equalsIgnoreCase(status) && appointment.getEndTime() == null) {
                appointment.setEndTime(LocalTime.now());
            } else if (!"Completed".equalsIgnoreCase(status)) {
                appointment.setEndTime(null);
            }

            appointmentRepository.save(appointment);

            // Save system log
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(staff.getUsername());
            sysLog.setAction("RECEPTION_ASSIGN_APPOINTMENT");
            sysLog.setDetails("Lễ tân " + staff.getFullName() + " đã cập nhật lịch hẹn #" + appointmentId +
                    ": Bác sĩ: "
                    + (appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Chưa phân công") +
                    ", Phòng: " + (appointment.getRoomNumber() != null ? appointment.getRoomNumber() : "Chưa xếp") +
                    ", Ngày: " + targetDate.toString() +
                    ", Giờ đến: " + (appointment.getTimeSlot() != null ? appointment.getTimeSlot().toString() : "Chưa đến") +
                    ", Giờ về: " + (appointment.getEndTime() != null ? appointment.getEndTime().toString() : "Chưa về") +
                    ", Trạng thái: " + status);
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            if (wasShifted) {
                ra.addFlashAttribute("success", "Đã cập nhật lịch khám! Do bác sĩ đã đầy lịch vào ngày được chọn, ngày khám được tự động chuyển sang ngày " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
            } else {
                ra.addFlashAttribute("success", "Cập nhật và xếp lịch cho bệnh nhân thành công!");
            }
        } catch (Exception e) {
            log.error("Error assigning appointment: ", e);
            String message = e.getMessage();
            if (message != null && (message.contains("unique_doctor_slot") || message.contains("unique_patient_slot") || message.contains("ConstraintViolation") || message.contains("duplicate key"))) {
                ra.addFlashAttribute("error", "Lỗi xếp lịch khám: đã có lịch hẹn được đặt trước đó");
            } else {
                ra.addFlashAttribute("error", "Lỗi xếp lịch khám: " + e.getMessage());
            }
        }
        String finalRedirect = "redirect:/reception/" + redirectSource;
        if (redirectDate != null && !redirectDate.isBlank()) {
            finalRedirect += "?dateStr=" + redirectDate;
        }
        return finalRedirect;
    }

    @GetMapping("/appointments/new")
    public String newAppointmentForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        List<PatientProfile> patients = patientRepository.findAll();
        List<DoctorProfile> doctors = doctorRepository.findAll();

        model.addAttribute("staff", staff);
        model.addAttribute("patients", patients);
        model.addAttribute("doctors", doctors);
        return "reception/appointment-form";
    }

    @PostMapping("/appointments/new/save")
    public String saveNewAppointment(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("patientType") String patientType,
            @RequestParam(value = "patientId", required = false) Integer patientId,
            @RequestParam(value = "newFullName", required = false) String newFullName,
            @RequestParam(value = "newPhone", required = false) String newPhone,
            @RequestParam(value = "newDob", required = false) String newDob,
            @RequestParam(value = "newGender", required = false) String newGender,
            @RequestParam(value = "newAddress", required = false) String newAddress,
            @RequestParam(value = "doctorId", required = false) Integer doctorId,
            @RequestParam("scheduledDate") String scheduledDateStr,
            @RequestParam(value = "timeSlot", required = false) String timeSlotStr,
            @RequestParam(value = "roomNumber", required = false) String roomNumber,
            @RequestParam("preliminaryStatus") String preliminaryStatus,
            @RequestParam("status") String status,
            @RequestParam(value = "bookingType", required = false, defaultValue = "General") String bookingType,
            RedirectAttributes ra) {
        StaffProfile staff = getCurrentStaff(userDetails);

        try {
            PatientProfile patient = null;
            if ("new".equalsIgnoreCase(patientType)) {
                // Validate new patient inputs
                if (newFullName == null || newFullName.isBlank() ||
                        newPhone == null || newPhone.isBlank() ||
                        newDob == null || newDob.isBlank() ||
                        newGender == null || newGender.isBlank()) {
                    throw new RuntimeException("Vui lòng điền đầy đủ các thông tin bắt buộc của bệnh nhân mới.");
                }

                // Check for duplication on phone/username
                String username = newPhone.trim();
                boolean phoneExists = patientRepository.findByUsernameIgnoreCase(username).isPresent();
                if (!phoneExists) {
                    phoneExists = patientRepository.findAll().stream()
                            .anyMatch(p -> username.equalsIgnoreCase(p.getPhone()));
                }

                if (phoneExists) {
                    ra.addFlashAttribute("error",
                            "Số điện thoại/tài khoản này đã tồn tại trên hệ thống. Vui lòng chọn 'Bệnh nhân đã có tài khoản'.");
                    return "redirect:/reception/appointments/new";
                }

                patient = new PatientProfile();
                patient.setUsername(username);
                patient.setPasswordHash(passwordEncoder.encode("123"));
                patient.setFullName(newFullName.trim());
                patient.setDob(LocalDate.parse(newDob.trim()));
                patient.setGender(newGender.trim());
                patient.setPhone(username);
                patient.setAddress(newAddress != null ? newAddress.trim() : "");
                patient.setStatus("ACTIVE");

                patient = patientRepository.save(patient);

                // Save system log for patient registration
                SystemLog patLog = new SystemLog();
                patLog.setUsername(staff.getUsername());
                patLog.setAction("RECEPTION_REGISTER_PATIENT");
                patLog.setDetails("Lễ tân " + staff.getFullName() + " đã đăng ký tài khoản bệnh nhân mới: "
                        + patient.getFullName() + " (SĐT: " + username + ")");
                patLog.setTimestamp(LocalDateTime.now());
                systemLogRepository.save(patLog);

            } else {
                if (patientId == null) {
                    throw new RuntimeException("Chưa chọn bệnh nhân khám.");
                }
                patient = patientRepository.findById(patientId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân trong hệ thống."));
            }

            LocalDate date = LocalDate.parse(scheduledDateStr);
            Appointment appointment = new Appointment();
            appointment.setPatient(patient);

            LocalDate targetDate = date;
            boolean wasShifted = false;

            if (doctorId != null) {
                DoctorProfile doctor = doctorRepository.findById(doctorId)
                        .orElseThrow(() -> new RuntimeException("Doctor not found"));

                // Auto-overflow logic: find next consecutive day where doctor has < 8 appointments
                long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled");
                if (bookedCount >= 8) {
                    while (true) {
                        targetDate = targetDate.plusDays(1);
                        long nextBookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled");
                        if (nextBookedCount < 8) {
                            break;
                        }
                    }
                    wasShifted = true;
                }
                appointment.setDoctor(doctor);
            }

            appointment.setScheduledDate(targetDate);
            
            // Set timeSlot
            if (timeSlotStr != null && !timeSlotStr.isBlank()) {
                appointment.setTimeSlot(LocalTime.parse(timeSlotStr));
            } else if ("CheckedIn".equalsIgnoreCase(status)) {
                appointment.setTimeSlot(LocalTime.now());
            }
            appointment.setBookingType(bookingType);

            if (appointment.getDoctor() != null && (roomNumber == null || roomNumber.isBlank())) {
                appointment.setRoomNumber(appointment.getDoctor().getRoomNumber());
            } else {
                appointment.setRoomNumber(roomNumber);
            }
            appointment.setPreliminaryStatus(preliminaryStatus);
            appointment.setStatus(status);
            appointment.setRequestTime(LocalDateTime.now());

            appointmentRepository.save(appointment);

            // Save system log
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(staff.getUsername());
            sysLog.setAction("RECEPTION_CREATE_APPOINTMENT");
            sysLog.setDetails("Lễ tân " + staff.getFullName() + " tạo lịch hẹn khám mới cho bệnh nhân: "
                    + patient.getFullName() +
                    " (BS: "
                    + (appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Chưa phân công") +
                    ", Phòng: " + (appointment.getRoomNumber() != null ? appointment.getRoomNumber() : "Chưa xếp") +
                    ", Trạng thái: " + status + ")");
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            if (wasShifted) {
                ra.addFlashAttribute("success", "Đã tạo lịch khám thành công! Do bác sĩ đã đầy lịch khám vào ngày được chọn, ngày khám được tự động chuyển sang ngày " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
            } else {
                ra.addFlashAttribute("success", "Đã tạo lịch khám mới thành công cho bệnh nhân " + patient.getFullName());
            }
        } catch (Exception e) {
            log.error("Error saving new appointment by receptionist: ", e);
            String message = e.getMessage();
            if (message != null && (message.contains("unique_doctor_slot") || message.contains("unique_patient_slot") || message.contains("ConstraintViolation") || message.contains("duplicate key"))) {
                ra.addFlashAttribute("error", "Lỗi tạo lịch khám: đã có lịch hẹn được đặt trước đó");
            } else {
                ra.addFlashAttribute("error", "Lỗi tạo lịch khám: " + e.getMessage());
            }
            return "redirect:/reception/appointments/new";
        }

        return "redirect:/reception/dashboard";
    }

    @GetMapping("/patients")
    public String patients(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PatientProfile> patientPage = (search != null && !search.isBlank())
                ? patientRepository.findByFullNameContainingIgnoreCase(search, pageable)
                : patientRepository.findAll(pageable);
        model.addAttribute("staff", staff);
        model.addAttribute("patients", patientPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", patientPage.getTotalPages());
        model.addAttribute("totalItems", patientPage.getTotalElements());
        model.addAttribute("search", search);
        return "reception/patients";
    }

    @GetMapping("/schedule")
    public String schedule(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String dateStr,
            @RequestParam(required = false) Integer doctorId,
            Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        LocalDate date = (dateStr != null && !dateStr.isBlank()) ? LocalDate.parse(dateStr) : LocalDate.now();
        List<DoctorProfile> doctors = doctorRepository.findAll();

        List<Appointment> appointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getScheduledDate().equals(date) && (doctorId == null || (a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doctorId))))
                .sorted((a1, a2) -> {
                    LocalTime t1 = a1.getTimeSlot();
                    LocalTime t2 = a2.getTimeSlot();
                    if (t1 != null && t2 != null) {
                        return t1.compareTo(t2);
                    }
                    if (t1 != null) return -1;
                    if (t2 != null) return 1;

                    LocalDateTime r1 = a1.getRequestTime();
                    LocalDateTime r2 = a2.getRequestTime();
                    if (r1 != null && r2 != null) {
                        return r1.compareTo(r2);
                    }
                    if (r1 != null) return -1;
                    if (r2 != null) return 1;

                    return a1.getAppointmentId().compareTo(a2.getAppointmentId());
                })
                .collect(Collectors.toList());

        List<Appointment> allReference = appointmentRepository.findAll();
        Appointment.populateQueueNumbers(appointments, allReference);

        java.util.Map<Integer, Long> workloads = new java.util.HashMap<>();
        for (DoctorProfile doc : doctors) {
            long count = appointmentRepository.findAll().stream()
                    .filter(a -> a.getScheduledDate().equals(date) && a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doc.getDoctorId())
                            && !"Cancelled".equalsIgnoreCase(a.getStatus()))
                    .count();
            workloads.put(doc.getDoctorId(), count);
        }

        model.addAttribute("staff", staff);
        model.addAttribute("doctors", doctors);
        model.addAttribute("appointments", appointments);
        model.addAttribute("workloads", workloads);
        model.addAttribute("selectedDate", date);
        model.addAttribute("selectedDoctorId", doctorId);
        return "reception/schedule";
    }

    private List<Appointment> sortAppointmentsForQueue(List<Appointment> list) {
        if (list == null) return java.util.Collections.emptyList();
        return list.stream().sorted((a1, a2) -> {
            // Priority 1: Status InProgress
            int p1 = "InProgress".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int p2 = "InProgress".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (p1 != p2) return Integer.compare(p1, p2);

            // Priority 2: Status CheckedIn (FIFO by arrival time / timeSlot)
            int c1 = "CheckedIn".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int c2 = "CheckedIn".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (c1 != c2) return Integer.compare(c1, c2);
            if (c1 == 0) {
                if (a1.getTimeSlot() != null && a2.getTimeSlot() != null) {
                    return a1.getTimeSlot().compareTo(a2.getTimeSlot());
                }
                if (a1.getTimeSlot() != null) return -1;
                if (a2.getTimeSlot() != null) return 1;
            }

            // Priority 3: Status Confirmed
            int f1 = "Confirmed".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int f2 = "Confirmed".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (f1 != f2) return Integer.compare(f1, f2);

            // Priority 4: Status Pending
            int d1 = "Pending".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int d2 = "Pending".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (d1 != d2) return Integer.compare(d1, d2);

            // Priority 5: Completed
            int m1 = "Completed".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int m2 = "Completed".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (m1 != m2) return Integer.compare(m1, m2);

            // Priority 6: Cancelled
            int n1 = "Cancelled".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int n2 = "Cancelled".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (n1 != n2) return Integer.compare(n1, n2);

            // Fallback: ID
            if (a1.getAppointmentId() != null && a2.getAppointmentId() != null) {
                return a1.getAppointmentId().compareTo(a2.getAppointmentId());
            }
            return 0;
        }).collect(Collectors.toList());
    }
}
