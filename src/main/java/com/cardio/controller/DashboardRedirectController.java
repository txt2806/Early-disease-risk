package com.cardio.controller;

import com.cardio.model.SystemLog;
import com.cardio.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class DashboardRedirectController {

    private final SystemLogRepository systemLogRepository;

    @GetMapping("/dashboard-redirect")
    public String redirect(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        try {
            SystemLog log = new SystemLog();
            log.setUsername(authentication.getName());
            log.setAction("LOGIN_SUCCESS");
            log.setDetails("Đăng nhập thành công vào hệ thống.");
            log.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Could not save login success log: " + e.getMessage());
        }
        
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
        boolean isPatient = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

        if (isAdmin) {
            return "redirect:/admin/dashboard";
        }
        
        if (isPatient) {
            return "redirect:/register/complete-profile?email=" + authentication.getName();
        }
        
        return "redirect:/doctor/dashboard";
    }
}
