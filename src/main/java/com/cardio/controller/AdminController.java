package com.cardio.controller;

import com.cardio.model.DoctorProfile;
import com.cardio.model.PatientProfile;
import com.cardio.model.SystemLog;
import com.cardio.model.SystemUser;
import com.cardio.model.StaffProfile;
import com.cardio.model.AIRiskPrediction;
import com.cardio.repository.DoctorRepository;
import com.cardio.repository.PatientRepository;
import com.cardio.repository.SystemLogRepository;
import com.cardio.repository.AIRiskRepository;
import com.cardio.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final SystemLogRepository systemLogRepository;
    private final AIRiskRepository aiRiskRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    private void saveAuditLog(String actor, String action, String details) {
        try {
            SystemLog log = new SystemLog();
            log.setUsername(actor != null ? actor : "system");
            log.setAction(action);
            log.setDetails(details);
            log.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Could not save audit log: " + e.getMessage());
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<SystemUser> users = new java.util.ArrayList<>();
        List<PatientProfile> patients = new java.util.ArrayList<>();
        try {
            List<DoctorProfile> doctors = doctorRepository.findAll();
            List<StaffProfile> staff = staffRepository.findAll();
            
            users.addAll(doctors);
            users.addAll(staff);
            users.removeIf(u -> "admin@cardio.com".equalsIgnoreCase(u.getUsername()));
            
            patients = patientRepository.findAll();
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi kết nối CSDL: Bạn đang duyệt ở chế độ Offline (không kết nối Database).");
        }

        model.addAttribute("users", users);
        model.addAttribute("patients", patients);
        model.addAttribute("newUser", new DoctorProfile());
        return "admin/admin-dashboard";
    }

    @PostMapping("/user/create")
    public String createUser(@ModelAttribute DoctorProfile user, 
                             @RequestParam("password") String password,
                             Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        
        // 1. Username duplicate check across both Doctor and Staff tables
        if (doctorRepository.findByUsername(user.getUsername()).isPresent() ||
            staffRepository.findByUsername(user.getUsername()).isPresent()) {
            saveAuditLog(actor, "CREATE_USER_FAILED", "Trùng tên đăng nhập: " + user.getUsername());
            return "redirect:/admin/dashboard?error=username_duplicate";
        }

        String hashedPassword = passwordEncoder.encode(password);
        
        if ("DOCTOR".equalsIgnoreCase(user.getRole())) {
            user.setPasswordHash(hashedPassword);
            user.setStatus("ACTIVE");
            user.setSpecialty("Tim mạch");
            if (user.getAlertThresholdBpm() == null) user.setAlertThresholdBpm(100);
            if (user.getAlertThresholdBp() == null) user.setAlertThresholdBp("140/90");
            doctorRepository.save(user);
        } else {
            StaffProfile staff = new StaffProfile();
            staff.setUsername(user.getUsername());
            staff.setPasswordHash(hashedPassword);
            staff.setFullName(user.getFullName());
            staff.setRole(user.getRole());
            staff.setStatus("ACTIVE");
            staffRepository.save(staff);
        }

        saveAuditLog(actor, "CREATE_USER_SUCCESS", "Tạo tài khoản thành công: " + user.getUsername() + " (" + user.getRole() + ")");
        return "redirect:/admin/dashboard?success=ok_created";
    }

    @PostMapping("/user/edit")
    public String editUser(@ModelAttribute DoctorProfile user, 
                           @RequestParam(value = "newPassword", required = false) String newPassword,
                           Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        
        // Find user by ID in both repositories
        if (doctorRepository.findById(user.getDoctorId()).isPresent()) {
            DoctorProfile existing = doctorRepository.findById(user.getDoctorId()).get();
            existing.setFullName(user.getFullName());
            existing.setAlertThresholdBpm(user.getAlertThresholdBpm());
            existing.setAlertThresholdBp(user.getAlertThresholdBp());
            existing.setLicenseNumber(user.getLicenseNumber());
            existing.setRoomNumber(user.getRoomNumber());
            
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                existing.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
            }
            doctorRepository.save(existing);
            saveAuditLog(actor, "EDIT_USER_SUCCESS", "Cập nhật hồ sơ bác sĩ: " + existing.getUsername());
        } else if (staffRepository.findById(user.getDoctorId()).isPresent()) {
            StaffProfile existing = staffRepository.findById(user.getDoctorId()).get();
            existing.setFullName(user.getFullName());
            
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                existing.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
            }
            staffRepository.save(existing);
            saveAuditLog(actor, "EDIT_USER_SUCCESS", "Cập nhật hồ sơ nhân sự: " + existing.getUsername());
        } else {
            throw new IllegalArgumentException("Invalid user ID: " + user.getDoctorId());
        }

        return "redirect:/admin/dashboard?success=ok_updated";
    }

    @PostMapping("/patient/reset-password")
    public String resetPatientPassword(@RequestParam("patientId") Integer patientId, 
                                       @RequestParam("newPassword") String newPassword,
                                       Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        
        PatientProfile existing = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid patient ID: " + patientId));
        
        existing.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        patientRepository.save(existing);
        
        saveAuditLog(actor, "RESET_PASSWORD_PATIENT", "Đổi mật khẩu bệnh nhân: " + existing.getUsername());
        return "redirect:/admin/dashboard?success=ok_reset_pw";
    }

    @PostMapping("/user/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable("id") Integer id, 
                                   @RequestParam("role") String role,
                                   Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        boolean wasLocked = false;
        
        if ("DOCTOR".equalsIgnoreCase(role)) {
            DoctorProfile user = doctorRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid doctor ID: " + id));
            wasLocked = "LOCKED".equalsIgnoreCase(user.getStatus());
            if (wasLocked) {
                user.setStatus("ACTIVE");
                saveAuditLog(actor, "UNLOCK_USER", "Mở khóa tài khoản bác sĩ: " + user.getUsername());
            } else {
                user.setStatus("LOCKED");
                saveAuditLog(actor, "LOCK_USER", "Khóa tài khoản bác sĩ: " + user.getUsername());
            }
            doctorRepository.save(user);
        } else {
            StaffProfile user = staffRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid staff ID: " + id));
            wasLocked = "LOCKED".equalsIgnoreCase(user.getStatus());
            if (wasLocked) {
                user.setStatus("ACTIVE");
                saveAuditLog(actor, "UNLOCK_USER", "Mở khóa tài khoản nhân sự: " + user.getUsername());
            } else {
                user.setStatus("LOCKED");
                saveAuditLog(actor, "LOCK_USER", "Khóa tài khoản nhân sự: " + user.getUsername());
            }
            staffRepository.save(user);
        }
        
        return "redirect:/admin/dashboard?success=" + (wasLocked ? "ok_unlocked" : "ok_locked");
    }

    @PostMapping("/user/delete/{id}")
    public String deleteUser(@PathVariable("id") Integer id, 
                             @RequestParam("role") String role,
                             Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        
        if ("DOCTOR".equalsIgnoreCase(role)) {
            DoctorProfile user = doctorRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid doctor ID: " + id));
            doctorRepository.deleteById(id);
            saveAuditLog(actor, "DELETE_USER", "Xóa tài khoản bác sĩ: " + user.getUsername());
        } else {
            StaffProfile user = staffRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid staff ID: " + id));
            staffRepository.deleteById(id);
            saveAuditLog(actor, "DELETE_USER", "Xóa tài khoản nhân sự: " + user.getUsername());
        }
        
        return "redirect:/admin/dashboard?success=ok_deleted_user";
    }

    @PostMapping("/patient/delete/{id}")
    public String deletePatient(@PathVariable("id") Integer id, Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        
        PatientProfile patient = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid patient ID: " + id));
        
        patientRepository.deleteById(id);
        saveAuditLog(actor, "DELETE_PATIENT", "Xóa hồ sơ bệnh nhân: " + patient.getFullName());
        return "redirect:/admin/dashboard?success=ok_deleted_patient";
    }

    @PostMapping("/patient/toggle-status/{id}")
    public String togglePatientStatus(@PathVariable("id") Integer id, Principal principal) {
        String actor = principal != null ? principal.getName() : "admin";
        
        PatientProfile patient = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid patient ID: " + id));
        
        boolean wasLocked = "LOCKED".equalsIgnoreCase(patient.getStatus());
        if (wasLocked) {
            patient.setStatus("ACTIVE");
            saveAuditLog(actor, "UNLOCK_PATIENT", "Mở khóa tài khoản bệnh nhân: " + patient.getUsername());
        } else {
            patient.setStatus("LOCKED");
            saveAuditLog(actor, "LOCK_PATIENT", "Khóa tài khoản bệnh nhân: " + patient.getUsername());
        }
        
        patientRepository.save(patient);
        return "redirect:/admin/dashboard?success=" + (wasLocked ? "ok_patient_unlocked" : "ok_patient_locked");
    }

    @GetMapping("/reports")
    public String viewReports(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            Model model) {
        long highRiskCount = 0;
        long mediumRiskCount = 0;
        long lowRiskCount = 0;
        long doctorsCount = 0;
        long staffCount = 0;
        long receptionistCount = 0;
        long patientsCount = 0;
        List<SystemLog> allLogs = new java.util.ArrayList<>();
        Map<String, Long> staffActionsFrequency = new HashMap<>();
        
        boolean dateRangeSelected = startDateStr != null && !startDateStr.trim().isEmpty() 
                && endDateStr != null && !endDateStr.trim().isEmpty();

        if (dateRangeSelected) {
            try {
                List<AIRiskPrediction> allPredictions = aiRiskRepository.findAll();
                
                try {
                    java.time.LocalDate startDate = java.time.LocalDate.parse(startDateStr.trim());
                    LocalDateTime startDateTime = startDate.atStartOfDay();
                    allPredictions = allPredictions.stream()
                            .filter(p -> p.getRecord() != null && p.getRecord().getVisitDate() != null && !p.getRecord().getVisitDate().isBefore(startDateTime))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    // Ignore parsing errors
                }

                try {
                    java.time.LocalDate endDate = java.time.LocalDate.parse(endDateStr.trim());
                    LocalDateTime endDateTime = endDate.atTime(23, 59, 59, 999999999);
                    allPredictions = allPredictions.stream()
                            .filter(p -> p.getRecord() != null && p.getRecord().getVisitDate() != null && !p.getRecord().getVisitDate().isAfter(endDateTime))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    // Ignore parsing errors
                }

                highRiskCount = allPredictions.stream()
                        .filter(p -> "HIGH".equalsIgnoreCase(p.getRiskLevel()))
                        .count();

                mediumRiskCount = allPredictions.stream()
                        .filter(p -> "MEDIUM".equalsIgnoreCase(p.getRiskLevel()))
                        .count();

                lowRiskCount = allPredictions.stream()
                        .filter(p -> "LOW".equalsIgnoreCase(p.getRiskLevel()) || "NORMAL".equalsIgnoreCase(p.getRiskLevel()))
                        .count();

                doctorsCount = doctorRepository.count();
                staffCount = staffRepository.findAll().stream().filter(d -> "STAFF".equalsIgnoreCase(d.getRole())).count();
                receptionistCount = staffRepository.findAll().stream().filter(d -> "RECEPTIONIST".equalsIgnoreCase(d.getRole())).count();
                patientsCount = patientRepository.count();

                allLogs = systemLogRepository.findAllByOrderByTimestampDesc();

                // Filter by search query
                if (search != null && !search.trim().isEmpty()) {
                    String searchLower = search.trim().toLowerCase();
                    allLogs = allLogs.stream()
                            .filter(log -> (log.getUsername() != null && log.getUsername().toLowerCase().contains(searchLower))
                                    || (log.getAction() != null && log.getAction().toLowerCase().contains(searchLower))
                                    || (log.getDetails() != null && log.getDetails().toLowerCase().contains(searchLower)))
                            .collect(Collectors.toList());
                }

                // Filter by start date
                try {
                    java.time.LocalDate startDate = java.time.LocalDate.parse(startDateStr.trim());
                    LocalDateTime startDateTime = startDate.atStartOfDay();
                    allLogs = allLogs.stream()
                            .filter(log -> !log.getTimestamp().isBefore(startDateTime))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    // Ignore parsing errors
                }

                // Filter by end date
                try {
                    java.time.LocalDate endDate = java.time.LocalDate.parse(endDateStr.trim());
                    LocalDateTime endDateTime = endDate.atTime(23, 59, 59, 999999999);
                    allLogs = allLogs.stream()
                            .filter(log -> !log.getTimestamp().isAfter(endDateTime))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    // Ignore parsing errors
                }

                staffActionsFrequency = allLogs.stream()
                        .filter(log -> log.getUsername() != null && !"admin@cardio.com".equalsIgnoreCase(log.getUsername()) && !"system".equalsIgnoreCase(log.getUsername()))
                        .collect(Collectors.groupingBy(SystemLog::getUsername, Collectors.counting()));
            } catch (Exception e) {
                model.addAttribute("error", "Lỗi kết nối CSDL: Báo cáo đang hiển thị dữ liệu trống.");
            }
        }

        model.addAttribute("dateRangeSelected", dateRangeSelected);
        model.addAttribute("highRiskCount", highRiskCount);
        model.addAttribute("mediumRiskCount", mediumRiskCount);
        model.addAttribute("lowRiskCount", lowRiskCount);
        model.addAttribute("doctorsCount", doctorsCount);
        model.addAttribute("staffCount", staffCount);
        model.addAttribute("receptionistCount", receptionistCount);
        model.addAttribute("patientsCount", patientsCount);
        model.addAttribute("staffActionsFrequency", staffActionsFrequency);
        model.addAttribute("logs", allLogs);
        model.addAttribute("search", search);
        model.addAttribute("startDate", startDateStr);
        model.addAttribute("endDate", endDateStr);

        return "admin/admin-reports";
    }
}
