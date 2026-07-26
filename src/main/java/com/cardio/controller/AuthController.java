package com.cardio.controller;

import com.cardio.dto.ChangePasswordRequestDTO;
import com.cardio.dto.LoginRequestDTO;
import com.cardio.model.PatientProfile;
import com.cardio.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDTO loginRequest) {
        Map<String, Object> response = new HashMap<>();

        String inputAccount = loginRequest.getUsername() != null ? loginRequest.getUsername().trim() : "";

        // 1. Phân loại & Đối soát: Tìm theo Username (hoặc Email nếu Username lưu email)
        Optional<PatientProfile> patientOpt = patientRepository.findByUsernameIgnoreCase(inputAccount);

        // 2. Nếu không thấy theo Username, kiểm tra nếu chuỗi nhập là SĐT (thử các biến thể 0... / +84...)
        if (patientOpt.isEmpty()) {
            java.util.List<String> phoneVariations = getPhoneVariations(inputAccount);
            patientOpt = patientRepository.findByPhoneIn(phoneVariations).stream().findFirst();
        }

        if (patientOpt.isEmpty()) {
            response.put("status", "failed");
            response.put("message", "Tài khoản hoặc mật khẩu không chính xác!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        PatientProfile patient = patientOpt.get();
        if ("LOCKED".equalsIgnoreCase(patient.getStatus())) {
            response.put("status", "failed");
            response.put("message", "Tài khoản của bạn đã bị khóa.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (passwordEncoder.matches(loginRequest.getPassword(), patient.getPasswordHash())) {
            // [FIX] Lấy trạng thái isFirstLogin trực tiếp từ DB thay vì hardcode check.
            // Giả định PatientProfile entity đã có trường `isFirstLogin`.
            boolean isFirstLogin = patient.isFirstLogin();

            response.put("status", "success");
            response.put("message", "Đăng nhập hợp lệ.");
            response.put("patientId", patient.getPatientId()); // Trả về ID kiểu Integer
            response.put("fullName", patient.getFullName());
            response.put("username", patient.getUsername());
            response.put("isFirstLogin", isFirstLogin); // Gửi cờ này về cho Flutter
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "failed");
            response.put("message", "Tài khoản hoặc mật khẩu không chính xác!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequestDTO request) {
        Map<String, Object> response = new HashMap<>();

        Optional<PatientProfile> patientOpt = patientRepository.findByUsernameIgnoreCase(request.getUsername());

        if (patientOpt.isEmpty()) {
            response.put("status", "failed");
            response.put("message", "Không tìm thấy tài khoản người dùng.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        PatientProfile patient = patientOpt.get();

        if (!passwordEncoder.matches(request.getOldPassword(), patient.getPasswordHash())) {
            log.warn("Change password failed for user {}: Incorrect old password", request.getUsername());
            response.put("status", "failed");
            response.put("message", "Mật khẩu hiện tại không chính xác!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        patient.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // [FIX] Sau khi đổi mật khẩu, cập nhật cờ isFirstLogin = false để không hỏi lại.
        // Giả định PatientProfile entity đã có trường `isFirstLogin` và setter.
        patient.setFirstLogin(false);
        patientRepository.save(patient);

        log.info("Password changed successfully for user {}", request.getUsername());
        response.put("status", "success");
        response.put("message", "Đổi mật khẩu thành công!");
        return ResponseEntity.ok(response);
    }

    private static java.util.List<String> getPhoneVariations(String input) {
        java.util.List<String> variations = new java.util.ArrayList<>();
        if (input == null || input.isBlank()) return variations;
        variations.add(input);

        if (input.startsWith("0") && input.length() > 1) {
            variations.add("+84" + input.substring(1));
        } else if (input.startsWith("+84") && input.length() > 3) {
            variations.add("0" + input.substring(3));
        } else if (input.startsWith("84") && input.length() > 2) {
            variations.add("0" + input.substring(2));
        }
        return variations;
    }
}