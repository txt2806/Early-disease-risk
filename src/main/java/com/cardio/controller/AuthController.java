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

        Optional<PatientProfile> patientOpt = patientRepository.findByUsernameIgnoreCase(loginRequest.getUsername());

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
            // Giả định "lần đăng nhập đầu tiên" là khi người dùng vẫn sử dụng mật khẩu mặc định "123"
            boolean isFirstLogin = passwordEncoder.matches("123", patient.getPasswordHash());

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
        patientRepository.save(patient);

        log.info("Password changed successfully for user {}", request.getUsername());
        response.put("status", "success");
        response.put("message", "Đổi mật khẩu thành công!");
        return ResponseEntity.ok(response);
    }
}