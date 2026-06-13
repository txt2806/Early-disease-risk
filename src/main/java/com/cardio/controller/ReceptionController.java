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
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        List<Appointment> appointments = appointmentRepository.findAllOrderByDateAndTime();
        List<DoctorProfile> doctors = doctorRepository.findAll();
        List<PatientProfile> patients = patientRepository.findAll();

        long pendingCount = appointments.stream().filter(a -> "Pending".equalsIgnoreCase(a.getStatus())).count();
        long confirmedCount = appointments.stream().filter(a -> "Confirmed".equalsIgnoreCase(a.getStatus())).count();
        long todayCount = appointments.stream()
                .filter(a -> a.getScheduledDate().equals(LocalDate.now()))
                .count();

        model.addAttribute("staff", staff);
        model.addAttribute("appointments", appointments);
        model.addAttribute("doctors", doctors);
        model.addAttribute("patients", patients);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("currentDate", LocalDate.now());

        return "reception/dashboard";
    }

    @PostMapping("/appointments/assign")
    public String assignAppointment(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("appointmentId") Integer appointmentId,
            @RequestParam(value = "doctorId", required = false) Integer doctorId,
            @RequestParam(value = "roomNumber", required = false) String roomNumber,
            @RequestParam("scheduledDate") String scheduledDateStr,
            @RequestParam("timeSlot") String timeSlotStr,
            @RequestParam("status") String status,
            @RequestParam(value = "redirectSource", required = false, defaultValue = "dashboard") String redirectSource,
            @RequestParam(value = "redirectDate", required = false) String redirectDate,
            RedirectAttributes ra) {
        StaffProfile staff = getCurrentStaff(userDetails);

        try {
            Appointment appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));

            LocalDate date = LocalDate.parse(scheduledDateStr);

            if (doctorId != null) {
                DoctorProfile doctor = doctorRepository.findById(doctorId)
                        .orElseThrow(() -> new RuntimeException("Doctor not found"));

                // Exclude the current appointment from the count if it's already assigned to
                // this doctor and date
                long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, date,
                        "Cancelled");
                boolean isSameDoctorAndDate = appointment.getDoctor() != null &&
                        appointment.getDoctor().getDoctorId().equals(doctorId) &&
                        appointment.getScheduledDate().equals(date) &&
                        !"Cancelled".equalsIgnoreCase(appointment.getStatus());

                if (bookedCount >= 8 && !isSameDoctorAndDate) {
                    ra.addFlashAttribute("error", "Bác sĩ " + doctor.getFullName()
                            + " đã làm việc đủ 8 tiếng (8 ca khám) trong ngày này. Vui lòng chọn bác sĩ khác.");
                    String errorRedirect = "redirect:/reception/" + redirectSource;
                    if (redirectDate != null && !redirectDate.isBlank()) {
                        errorRedirect += "?dateStr=" + redirectDate;
                    }
                    return errorRedirect;
                }
                appointment.setDoctor(doctor);
            } else {
                appointment.setDoctor(null);
            }

            appointment.setRoomNumber(roomNumber);
            appointment.setScheduledDate(date);
            appointment.setTimeSlot(LocalTime.parse(timeSlotStr));
            appointment.setStatus(status);

            appointmentRepository.save(appointment);

            // Save system log
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(staff.getUsername());
            sysLog.setAction("RECEPTION_ASSIGN_APPOINTMENT");
            sysLog.setDetails("Lễ tân " + staff.getFullName() + " đã cập nhật lịch hẹn #" + appointmentId +
                    ": Bác sĩ: "
                    + (appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Chưa phân công") +
                    ", Phòng: " + (roomNumber != null ? roomNumber : "Chưa xếp") +
                    ", Ngày: " + scheduledDateStr +
                    ", Giờ: " + timeSlotStr +
                    ", Trạng thái: " + status);
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            ra.addFlashAttribute("success", "Cập nhật và xếp lịch cho bệnh nhân thành công!");
        } catch (Exception e) {
            log.error("Error assigning appointment: ", e);
            ra.addFlashAttribute("error", "Lỗi xếp lịch khám: " + e.getMessage());
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
    public String saveAppointment(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("patientType") String patientType,
            @RequestParam(value = "patientId", required = false) Integer patientId,
            @RequestParam(value = "newFullName", required = false) String newFullName,
            @RequestParam(value = "newPhone", required = false) String newPhone,
            @RequestParam(value = "newDob", required = false) String newDob,
            @RequestParam(value = "newGender", required = false) String newGender,
            @RequestParam(value = "newAddress", required = false) String newAddress,
            @RequestParam(value = "doctorId", required = false) Integer doctorId,
            @RequestParam("scheduledDate") String scheduledDateStr,
            @RequestParam("timeSlot") String timeSlotStr,
            @RequestParam(value = "roomNumber", required = false) String roomNumber,
            @RequestParam("preliminaryStatus") String preliminaryStatus,
            @RequestParam("status") String status,
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

            if (doctorId != null) {
                DoctorProfile doctor = doctorRepository.findById(doctorId)
                        .orElseThrow(() -> new RuntimeException("Doctor not found"));

                long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, date,
                        "Cancelled");
                if (bookedCount >= 8) {
                    ra.addFlashAttribute("error", "Bác sĩ " + doctor.getFullName()
                            + " đã làm việc đủ 8 tiếng (8 ca khám) trong ngày này. Vui lòng chọn bác sĩ khác hoặc ngày khác.");
                    return "redirect:/reception/appointments/new";
                }
                appointment.setDoctor(doctor);
            }

            appointment.setScheduledDate(date);
            appointment.setTimeSlot(LocalTime.parse(timeSlotStr));
            appointment.setRoomNumber(roomNumber);
            appointment.setPreliminaryStatus(preliminaryStatus);
            appointment.setStatus(status);

            appointmentRepository.save(appointment);

            // Save system log
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(staff.getUsername());
            sysLog.setAction("RECEPTION_CREATE_APPOINTMENT");
            sysLog.setDetails("Lễ tân " + staff.getFullName() + " tạo lịch hẹn khám mới cho bệnh nhân: "
                    + patient.getFullName() +
                    " (BS: "
                    + (appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Chưa phân công") +
                    ", Phòng: " + (roomNumber != null ? roomNumber : "Chưa xếp") +
                    ", Trạng thái: " + status + ")");
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            ra.addFlashAttribute("success", "Đã tạo lịch khám mới thành công cho bệnh nhân " + patient.getFullName());
        } catch (Exception e) {
            log.error("Error saving new appointment by receptionist: ", e);
            ra.addFlashAttribute("error", "Lỗi tạo lịch khám: " + e.getMessage());
            return "redirect:/reception/appointments/new";
        }

        return "redirect:/reception/dashboard";
    }

    @GetMapping("/patients")
    public String patients(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String search,
            Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        List<PatientProfile> patients;
        if (search != null && !search.isBlank()) {
            patients = patientRepository.findByFullNameContainingIgnoreCase(search);
        } else {
            patients = patientRepository.findAll();
        }

        model.addAttribute("staff", staff);
        model.addAttribute("patients", patients);
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
                .filter(a -> a.getScheduledDate().equals(date))
                .toList();

        java.util.Map<Integer, Long> workloads = new java.util.HashMap<>();
        for (DoctorProfile doc : doctors) {
            long count = appointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doc.getDoctorId())
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
}
