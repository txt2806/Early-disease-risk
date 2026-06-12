package com.cardio.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardRedirectController {

    @GetMapping("/dashboard-redirect")
    public String redirect(Authentication authentication) {
        try {
            if (authentication == null) {
                return "redirect:/login";
            }
            
            boolean isAdmin = false;
            boolean isPatient = false;
            
            if (authentication.getAuthorities() != null) {
                isAdmin = authentication.getAuthorities().stream()
                        .filter(a -> a != null && a.getAuthority() != null)
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                        
                isPatient = authentication.getAuthorities().stream()
                        .filter(a -> a != null && a.getAuthority() != null)
                        .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));
            }

            if (isAdmin) {
                return "redirect:/admin/dashboard";
            }
            
            if (isPatient) {
                return "redirect:/register/complete-profile?email=" + authentication.getName();
            }
            
            return "redirect:/doctor/dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/login?error=redirect";
        }
    }
}
