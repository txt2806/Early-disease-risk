package com.cardio.controller;

import com.cardio.model.PatientProfile;
import com.cardio.model.AIRiskPrediction;
import com.cardio.repository.PatientRepository;
import com.cardio.repository.AIRiskRepository;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AIRiskRepository aiRiskRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
                        if (firebasePhone == null || !firebasePhone.equalsIgnoreCase(phoneInput)) {
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
            if (patientRepository.findByUsername(emailInput).isPresent()) {
                httpResponse.setStatus(400);
                model.addAttribute("error", "Email đăng nhập này đã được đăng ký.");
                return "auth/register";
            }
            if (phoneInput != null && !phoneInput.isEmpty()) {
                final String searchPhone = phoneInput;
                // Kiểm tra trùng SĐT trong toàn bộ bảng
                boolean phoneExists = patientRepository.findAll().stream()
                        .anyMatch(p -> searchPhone.equalsIgnoreCase(p.getPhone()));
                if (phoneExists) {
                    httpResponse.setStatus(400);
                    model.addAttribute("error", "Số điện thoại này đã được một tài khoản khác đăng ký.");
                    return "auth/register";
                }
            }

            // 2. Tạo đối tượng Patient Profile (Role Bệnh nhân)
            PatientProfile patient = new PatientProfile();
            patient.setUsername(emailInput); // Username luôn lưu Email để đồng bộ đăng nhập
            patient.setPasswordHash(passwordEncoder.encode(password));
            patient.setFullName(fullName);
            patient.setDob(LocalDate.parse(dob));
            patient.setGender(gender);
            patient.setPhone(phoneInput); // Phone luôn lưu SĐT để đồng bộ đăng nhập
            patient.setAddress(address);

            patientRepository.save(patient);

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
            Model model) {
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
                patientOpt = patientRepository.findByUsername(email);
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
            Model model) {
        Optional<PatientProfile> patientOpt = patientRepository.findByUsername(email);
        if (!patientOpt.isPresent()) {
            // Trông giống số điện thoại? Thử tìm theo Phone
            String phoneNorm1 = email;
            String phoneNorm2 = email;
            if (email.startsWith("0")) {
                phoneNorm1 = "+84" + email.substring(1);
            } else if (email.startsWith("+84")) {
                phoneNorm2 = "0" + email.substring(3);
            }
            final String norm1 = phoneNorm1;
            final String norm2 = phoneNorm2;
            patientOpt = patientRepository.findAll().stream()
                    .filter(p -> p.getPhone() != null &&
                            (p.getPhone().equals(email) ||
                                    p.getPhone().equals(norm1) ||
                                    p.getPhone().equals(norm2)))
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

            try {
                // Lấy danh sách chẩn đoán AI của bệnh nhân này
                java.util.List<AIRiskPrediction> predictions = aiRiskRepository.findByPatient(patient);
                model.addAttribute("predictions", predictions != null ? predictions : new java.util.ArrayList<>());
            } catch (Exception e) {
                System.out.println("Warning: Could not load AI predictions: " + e.getMessage());
                model.addAttribute("predictions", new java.util.ArrayList<>());
            }

            try {
                // Lấy danh sách chỉ số lâm sàng (bác sĩ đo)
                if (patient.getPatientId() != null) {
                    java.util.List<java.util.Map<String, Object>> clinicalMetrics = jdbcTemplate.queryForList(
                            "SELECT m.* FROM Heart_Clinical_Metrics m JOIN Consultation_Record r ON m.RecordID = r.RecordID WHERE r.PatientID = ? ORDER BY r.VisitDate DESC",
                            patient.getPatientId());
                    model.addAttribute("clinicalMetrics", clinicalMetrics);
                } else {
                    model.addAttribute("clinicalMetrics", new java.util.ArrayList<>());
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not load clinical metrics: " + e.getMessage());
                model.addAttribute("clinicalMetrics", new java.util.ArrayList<>());
            }

            try {
                // Lấy danh sách tự theo dõi (bệnh nhân tự ghi nhận)
                if (patient.getPatientId() != null) {
                    java.util.List<java.util.Map<String, Object>> monitoringLogs = jdbcTemplate.queryForList(
                            "SELECT LogID, LogDate, CurrentHeartRate, Symptoms, TriggeredAlert FROM Patient_Self_Monitoring WHERE PatientID = ? ORDER BY LogDate DESC",
                            patient.getPatientId());
                    model.addAttribute("monitoringLogs", monitoringLogs);
                } else {
                    model.addAttribute("monitoringLogs", new java.util.ArrayList<>());
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not load monitoring logs: " + e.getMessage());
                model.addAttribute("monitoringLogs", new java.util.ArrayList<>());
            }
        } else {
            model.addAttribute("isCompleted", false);
            model.addAttribute("email", email);
            model.addAttribute("fullName", "Bệnh nhân mới");
            model.addAttribute("predictions", new java.util.ArrayList<>());
            model.addAttribute("clinicalMetrics", new java.util.ArrayList<>());
            model.addAttribute("monitoringLogs", new java.util.ArrayList<>());
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
            Model model) {

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
            patient.setUsername(email);
            patient.setPasswordHash(passwordEncoder.encode("GOOGLE_OAUTH_FALLBACK_PASSWORD"));
            patient.setFullName(fullName);
            patient.setDob(LocalDate.parse(dob));
            patient.setGender(gender);
            patient.setPhone(phone);
            patient.setAddress(address);

            try {
                // Kiểm tra trùng lặp
                if (patientRepository.findByUsername(email).isPresent()) {
                    model.addAttribute("error", "Email Google này đã tồn tại trên hệ thống.");
                    return "auth/complete-profile";
                }
                patientRepository.save(patient);
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
            Optional<PatientProfile> patientOpt = patientRepository.findByUsername(email);
            if (patientOpt.isPresent()) {
                PatientProfile patient = patientOpt.get();
                patient.setFullName(fullName);
                patient.setDob(LocalDate.parse(dob));
                patient.setGender(gender);
                patient.setPhone(phone);
                patient.setAddress(address);
                patientRepository.save(patient);
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
            Optional<PatientProfile> patientOpt = patientRepository.findByUsername(email);
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
                        "INSERT INTO Patient_Self_Monitoring (PatientID, LogDate, CurrentHeartRate, Symptoms, TriggeredAlert) VALUES (?, GETDATE(), ?, ?, ?)",
                        patient.getPatientId(), heartRate, symptoms, triggeredAlert ? 1 : 0);
            }
        } catch (Exception e) {
            System.err.println("Error adding self-monitoring log: " + e.getMessage());
        }
        return "redirect:/register/complete-profile?email=" + email;
    }
}
