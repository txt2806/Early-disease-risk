package com.cardio.controller;

import com.cardio.model.PatientProfile;
import com.cardio.model.AIRiskPrediction;
import com.cardio.repository.PatientRepository;
import com.cardio.repository.AIRiskRepository;
import com.cardio.model.SystemLog;
import com.cardio.repository.SystemLogRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AIRiskRepository aiRiskRepository;
    private final SystemLogRepository systemLogRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private org.springframework.security.web.context.SecurityContextRepository securityContextRepository =
            new org.springframework.security.web.context.HttpSessionSecurityContextRepository();

    @GetMapping("/register")
    public String showRegisterPage() {
        return "auth/register";
    }

    @PostMapping("/register")
    public String handleRegistration(
            @RequestParam(value = "phoneInput", required = false) String phoneInput,
            @RequestParam(value = "emailInput", required = false) String emailInput,
            @RequestParam("password") String password,
            @RequestParam("fullName") String fullName,
            @RequestParam("dob") String dob,
            @RequestParam("gender") String gender,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam("registerMethod") String registerMethod,
            @RequestParam("firebaseToken") String firebaseToken,
            Model model,
            jakarta.servlet.http.HttpServletResponse httpResponse) {

        try {
            boolean isFirebaseInitialized = !com.google.firebase.FirebaseApp.getApps().isEmpty();

            if (isFirebaseInitialized) {
                try {
                    // 1. Xác thực ID Token gửi từ client qua Firebase Admin SDK
                    com.google.firebase.auth.FirebaseToken decodedToken = FirebaseAuth.getInstance()
                            .verifyIdToken(firebaseToken);
                    if ("phone".equalsIgnoreCase(registerMethod)) {
                        String firebasePhone = (String) decodedToken.getClaims().get("phone_number");
                        String normFirebase = com.cardio.util.AuthUtil.normalizePhone(firebasePhone);
                        String normInput = com.cardio.util.AuthUtil.normalizePhone(phoneInput);
                        if (normFirebase == null || !normFirebase.equals(normInput)) {
                            httpResponse.setStatus(400);
                            model.addAttribute("error", "Số điện thoại xác thực từ Firebase không trùng khớp.");
                            return "auth/register";
                        }
                    } else if ("email".equalsIgnoreCase(registerMethod)) {
                        String firebaseEmail = decodedToken.getEmail();
                        if (firebaseEmail == null || !firebaseEmail.equalsIgnoreCase(emailInput)) {
                            httpResponse.setStatus(400);
                            model.addAttribute("error", "Email xác thực từ Firebase không trùng khớp.");
                            return "auth/register";
                        }
                        boolean isEmailVerified = decodedToken.isEmailVerified();
                        if (!isEmailVerified) {
                            httpResponse.setStatus(400);
                            model.addAttribute("error", "Email của bạn chưa được kích hoạt từ link Firebase.");
                            return "auth/register";
                        }
                    }
                } catch (Exception firebaseEx) {
                    System.err.println("Firebase verification error, falling back to local credentials: "
                            + firebaseEx.getMessage());
                }
            } else {
                System.out.println("Warning: Firebase Admin SDK not initialized. Local dev mode fallback active.");
            }

            // [VALIDATION] Kiểm tra xem trùng sdt hay trùng email của bất kỳ ai trong DB
            // chưa
            if (patientRepository.findByUsernameIgnoreCase(emailInput).isPresent()) {
                httpResponse.setStatus(400);
                model.addAttribute("error", "Email đăng nhập này đã được đăng ký.");
                return "auth/register";
            }
            if (phoneInput != null && !phoneInput.isEmpty()) {
                // Kiểm tra trùng SĐT trong toàn bộ bảng (Tối ưu hóa: Dùng findByPhoneIn hỗ trợ mọi định dạng biến thể)
                java.util.List<String> phoneVariations = com.cardio.util.AuthUtil.getPhoneVariations(phoneInput);
                boolean phoneExists = !patientRepository.findByPhoneIn(phoneVariations).isEmpty();
                if (phoneExists) {
                    httpResponse.setStatus(400);
                    model.addAttribute("error", "Số điện thoại này đã được một tài khoản khác đăng ký.");
                    return "auth/register";
                }
            }

            // 2. Tạo đối tượng Patient Profile (Role Bệnh nhân)
            PatientProfile patient = new PatientProfile();
            patient.setUsername(emailInput != null ? emailInput.trim() : null); // Username luôn lưu Email để đồng bộ đăng nhập
            patient.setPasswordHash(passwordEncoder.encode(password));
            patient.setFullName(fullName);
            patient.setDob(LocalDate.parse(dob));
            patient.setGender(gender);
            patient.setPhone(com.cardio.util.AuthUtil.normalizePhone(phoneInput)); // Phone luôn lưu SĐT để đồng bộ đăng nhập
            patient.setAddress(address);

            patientRepository.save(patient);

            try {
                SystemLog log = new SystemLog();
                log.setUsername(patient.getUsername());
                log.setAction("PATIENT_REGISTER_SUCCESS");
                log.setDetails("Bệnh nhân đăng ký tài khoản mới thành công: " + patient.getFullName() + " (Email: " + patient.getUsername() + ")");
                log.setTimestamp(java.time.LocalDateTime.now());
                systemLogRepository.save(log);
            } catch (Exception ex) {
                System.err.println("Error saving patient register system audit log: " + ex.getMessage());
            }

            model.addAttribute("success",
                    "Chúc mừng! Đăng ký tài khoản bệnh nhân thành công! Bạn có thể chuyển sang đăng nhập.");
            return "auth/register";

        } catch (Exception e) {
            httpResponse.setStatus(400);
            model.addAttribute("error", "Lỗi xác thực hệ thống: " + e.getMessage());
            return "auth/register";
        }
    }

    // --- GOOGLE SIGN-IN & PROFILE COMPLETION FLOW ---

    @PostMapping("/login/profile")
    public String handleGoogleLogin(
            @RequestParam("firebaseToken") String firebaseToken,
            @RequestParam(value = "email", required = false) String clientEmail,
            @RequestParam(value = "fullName", required = false) String clientFullName,
            Model model,
<<<<<<< Updated upstream
            HttpServletRequest request,
            HttpServletResponse response) {
=======
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
>>>>>>> Stashed changes
        try {
            String email = (clientEmail != null && !clientEmail.trim().isEmpty()) ? clientEmail.trim()
                    : "patient@example.com";
            String fullName = (clientFullName != null && !clientFullName.trim().isEmpty()) ? clientFullName.trim()
                    : "Bệnh nhân Google";

            try {
                // Thử xác thực thật trên Firebase
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
                if (decodedToken.getEmail() != null) {
                    email = decodedToken.getEmail();
                }
                if (decodedToken.getName() != null) {
                    fullName = decodedToken.getName();
                }
            } catch (Exception firebaseException) {
                // Fallback nếu offline hoặc không có tệp firebase-service-account.json
                System.out.println("Warning: Firebase verification failed, using client fallback email: " + email);
            }

            if (email == null) {
                model.addAttribute("error", "Không thể lấy thông tin Email từ tài khoản Google.");
                return "auth/login";
            }

            Optional<PatientProfile> patientOpt = Optional.empty();
            try {
                patientOpt = patientRepository.findByUsernameIgnoreCase(email);
            } catch (Exception e) {
                // DB offline
            }

            if (patientOpt.isPresent()) {
                PatientProfile patient = patientOpt.get();
                User principal = new User(patient.getUsername(), "",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_PATIENT")));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

                // Persist security context in HTTP session for Spring Security 6
                new HttpSessionSecurityContextRepository().saveContext(SecurityContextHolder.getContext(), request, response);

                return "redirect:/register/complete-profile?email=" + email;
            } else {
                model.addAttribute("email", email);
                model.addAttribute("fullName", fullName);
                model.addAttribute("firebaseToken", firebaseToken);
                return "auth/complete-profile";
            }

        } catch (Exception e) {
            model.addAttribute("error", "Đăng nhập Google thất bại: " + e.getMessage());
            return "auth/login";
        }
    }

    @GetMapping("/register/complete-profile")
    public String showCompleteProfile(
            @RequestParam(value = "email", required = false, defaultValue = "user@example.com") String email,
            Model model,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        // Prevent patients from viewing other patients' data
        boolean isPatient = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));
        if (isPatient && !authentication.getName().equalsIgnoreCase(email)) {
            return "redirect:/register/complete-profile?email=" + authentication.getName();
        }

        Optional<PatientProfile> patientOpt = patientRepository.findByUsernameIgnoreCase(email);
        if (!patientOpt.isPresent()) {
            // Trông giống số điện thoại? Thử tìm theo Phone (hỗ trợ mọi định dạng biến thể)
            java.util.List<String> phoneVariations = com.cardio.util.AuthUtil.getPhoneVariations(email);
            patientOpt = patientRepository.findByPhoneIn(phoneVariations).stream()
                    .findFirst();
        }

        if (patientOpt.isPresent()) {
            PatientProfile patient = patientOpt.get();
            model.addAttribute("patient", patient);
            model.addAttribute("isCompleted", true);
            model.addAttribute("email", patient.getUsername());
            model.addAttribute("fullName", patient.getFullName());
            model.addAttribute("phone", patient.getPhone());
            model.addAttribute("dob", patient.getDob() != null ? patient.getDob().toString() : "");
            model.addAttribute("gender", patient.getGender());
            model.addAttribute("address", patient.getAddress());

            // Lấy danh sách chẩn đoán AI của bệnh nhân này
            java.util.List<AIRiskPrediction> predictions = aiRiskRepository.findByPatient(patient);
            model.addAttribute("predictions", predictions);

            // Lấy danh sách chỉ số lâm sàng (bác sĩ đo)
            java.util.List<java.util.Map<String, Object>> clinicalMetrics = jdbcTemplate.queryForList(
                    "SELECT m.metricid AS \"MetricID\", m.recordid AS \"RecordID\", m.chestpaintype AS \"ChestPainType\", " +
                    "m.restingbp AS \"RestingBP\", m.cholesterol AS \"Cholesterol\", m.fastingbloodsugar AS \"FastingBloodSugar\", " +
                    "m.restingecg AS \"RestingECG\", m.maxheartrate AS \"MaxHeartRate\", m.exerciseangina AS \"ExerciseAngina\", " +
                    "m.recordedat AS \"RecordedAt\" FROM heart_clinical_metrics m JOIN consultation_record r ON m.recordid = r.recordid WHERE r.patientid = ? ORDER BY r.visitdate DESC",
                    patient.getPatientId());
            model.addAttribute("clinicalMetrics", clinicalMetrics);

            // Lấy danh sách tự theo dõi (bệnh nhân tự ghi nhận)
            java.util.List<java.util.Map<String, Object>> monitoringLogs = jdbcTemplate.queryForList(
                    "SELECT logid AS \"LogID\", logdate AS \"LogDate\", currentheartrate AS \"CurrentHeartRate\", symptoms AS \"Symptoms\", triggeredalert AS \"TriggeredAlert\" FROM patient_self_monitoring WHERE patientid = ? ORDER BY logdate DESC",
                    patient.getPatientId());
            model.addAttribute("monitoringLogs", monitoringLogs);
        } else {
            model.addAttribute("isCompleted", false);
            model.addAttribute("email", email);
            model.addAttribute("fullName", "Bệnh nhân mới");
        }
        model.addAttribute("firebaseToken", "mock-token");
        return "auth/complete-profile";
    }

    @PostMapping("/register/complete-profile")
    public String completeGoogleProfile(
            @RequestParam("email") String email,
            @RequestParam("fullName") String fullName,
            @RequestParam("phone") String phone,
            @RequestParam("dob") String dob,
            @RequestParam("gender") String gender,
            @RequestParam("address") String address,
            @RequestParam("firebaseToken") String firebaseToken,
            Model model,
<<<<<<< Updated upstream
            HttpServletRequest request,
            HttpServletResponse response) {
=======
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
>>>>>>> Stashed changes

        try {
            String firebaseEmail = email; // Fallback default
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
                firebaseEmail = decodedToken.getEmail();
            } catch (Exception firebaseException) {
                System.out.println("Warning: Firebase verification skipped in profile completion");
            }

            // Tạo PatientProfile mới
            PatientProfile patient = new PatientProfile();
            patient.setUsername(email != null ? email.trim() : null);
            patient.setPasswordHash(passwordEncoder.encode("GOOGLE_OAUTH_FALLBACK_PASSWORD"));
            patient.setFullName(fullName);
            patient.setDob(LocalDate.parse(dob));
            patient.setGender(gender);
            patient.setPhone(com.cardio.util.AuthUtil.normalizePhone(phone));
            patient.setAddress(address);

            try {
                // Kiểm tra trùng lặp
                if (patientRepository.findByUsernameIgnoreCase(email).isPresent()) {
                    model.addAttribute("error", "Email Google này đã tồn tại trên hệ thống.");
                    return "auth/complete-profile";
                }
                patientRepository.save(patient);

                try {
                    SystemLog log = new SystemLog();
                    log.setUsername(patient.getUsername());
                    log.setAction("PATIENT_COMPLETE_GOOGLE_PROFILE");
                    log.setDetails("Bệnh nhân hoàn tất hồ sơ Google: " + patient.getFullName() + " (SĐT: " + patient.getPhone() + ")");
                    log.setTimestamp(java.time.LocalDateTime.now());
                    systemLogRepository.save(log);
                } catch (Exception ex) {
                    System.err.println("Error saving patient google profile completion system audit log: " + ex.getMessage());
                }
            } catch (Exception e) {
                // Bỏ qua lỗi DB nếu đang chạy ở chế độ offline và cho phép login vào thẳng
                System.out.println("Warning: DB offline, skipping save to SQL Server");
            }

            // Cho đăng nhập tự động vào hệ thống ngay lập tức
            User principal = new User(email, "",
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_PATIENT")));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

            // Persist security context in HTTP session for Spring Security 6
            new HttpSessionSecurityContextRepository().saveContext(SecurityContextHolder.getContext(), request, response);

            return "redirect:/register/complete-profile?email=" + email; // Cho vào thẳng trang profile

        } catch (Exception e) {
            model.addAttribute("error", "Lỗi hoàn tất hồ sơ: " + e.getMessage());
            return "auth/complete-profile";
        }
    }

    @PostMapping("/patient/update-profile")
    public String updatePatientProfile(
            @RequestParam("email") String email,
            @RequestParam("fullName") String fullName,
            @RequestParam("phone") String phone,
            @RequestParam("dob") String dob,
            @RequestParam("gender") String gender,
            @RequestParam("address") String address,
            Model model) {
        try {
            Optional<PatientProfile> patientOpt = patientRepository.findByUsernameIgnoreCase(email);
            if (patientOpt.isPresent()) {
                PatientProfile patient = patientOpt.get();
                patient.setFullName(fullName);
                patient.setDob(LocalDate.parse(dob));
                patient.setGender(gender);
                patient.setPhone(com.cardio.util.AuthUtil.normalizePhone(phone));
                patient.setAddress(address);
                patientRepository.save(patient);

                try {
                    SystemLog log = new SystemLog();
                    log.setUsername(patient.getUsername());
                    log.setAction("PATIENT_UPDATE_PROFILE");
                    log.setDetails("Bệnh nhân cập nhật thông tin cá nhân: " + patient.getFullName() + " (SĐT: " + patient.getPhone() + ")");
                    log.setTimestamp(java.time.LocalDateTime.now());
                    systemLogRepository.save(log);
                } catch (Exception ex) {
                    System.err.println("Error saving patient profile update system audit log: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error updating patient profile: " + e.getMessage());
        }
        return "redirect:/register/complete-profile?email=" + email;
    }

    @PostMapping("/patient/add-monitoring")
    public String addSelfMonitoring(
            @RequestParam("email") String email,
            @RequestParam("heartRate") Integer heartRate,
            @RequestParam("symptoms") String symptoms,
            Model model) {
        try {
            Optional<PatientProfile> patientOpt = patientRepository.findByUsernameIgnoreCase(email);
            if (patientOpt.isPresent()) {
                PatientProfile patient = patientOpt.get();
                // Determine alert threshold (> 100 or < 50 bpm or dangerous symptoms)
                boolean triggeredAlert = (heartRate > 100 || heartRate < 50);
                String symLower = symptoms != null ? symptoms.toLowerCase() : "";
                if (symLower.contains("đau ngực") || symLower.contains("khó thở") || symLower.contains("ngất")
                        || symLower.contains("chóng mặt") || symLower.contains("đau thắt ngực")) {
                    triggeredAlert = true;
                }

                jdbcTemplate.update(
<<<<<<< Updated upstream
                        "INSERT INTO Patient_Self_Monitoring (PatientID, LogDate, CurrentHeartRate, Symptoms, TriggeredAlert) VALUES (?, GETDATE(), ?, ?, ?)",
                        patient.getPatientId(), heartRate, symptoms, triggeredAlert ? 1 : 0);
=======
                        "INSERT INTO Patient_Self_Monitoring (PatientID, LogDate, CurrentHeartRate, Symptoms, TriggeredAlert) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)",
                        patient.getPatientId(), heartRate, symptoms, triggeredAlert);

                // Save to system audit log
                try {
                    SystemLog log = new SystemLog();
                    log.setUsername(email);
                    log.setAction("PATIENT_SELF_MONITORING");
                    log.setDetails("Bệnh nhân tự ghi chỉ số sức khỏe: Nhịp tim " + heartRate + " bpm, Triệu chứng: " + (symptoms != null && !symptoms.trim().isEmpty() ? symptoms : "Không có") + " (Cảnh báo: " + (triggeredAlert ? "Có" : "Không") + ")");
                    log.setTimestamp(java.time.LocalDateTime.now());
                    systemLogRepository.save(log);
                } catch (Exception ex) {
                    System.err.println("Error saving self-monitoring system audit log: " + ex.getMessage());
                }
>>>>>>> Stashed changes
            }
        } catch (Exception e) {
            System.err.println("Error adding self-monitoring log: " + e.getMessage());
        }
        return "redirect:/register/complete-profile?email=" + email;
    }
}
