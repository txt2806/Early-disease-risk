package com.cardio.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardRedirectController {

    @GetMapping("/dashboard-redirect")
    public String redirect(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
        boolean isPatient = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

        boolean isReceptionist = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RECEPTIONIST"));

        if (isAdmin) {
            return "redirect:/admin/dashboard";
        }
        
        if (isPatient) {
            return "redirect:/patient/dashboard";
        }

        if (isReceptionist) {
            return "redirect:/reception/dashboard";
        }
        
        return "redirect:/doctor/dashboard";
    }
}
