package com.cardio.controller;

import com.cardio.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardRedirectController {

    private final PatientRepository patientRepository;

    @GetMapping("/dashboard-redirect")
    public String redirect(Authentication authentication) {
        try {
            if (authentication == null) {
                return "redirect:/login";
            }
            
            boolean isAdmin = false;
            boolean isPatient = false;
            boolean isReceptionist = false;
            boolean isStaff = false;
            
            if (authentication.getAuthorities() != null) {
                isAdmin = authentication.getAuthorities().stream()
                        .filter(a -> a != null && a.getAuthority() != null)
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                        
                isPatient = authentication.getAuthorities().stream()
                        .filter(a -> a != null && a.getAuthority() != null)
                        .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

                isReceptionist = authentication.getAuthorities().stream()
                        .filter(a -> a != null && a.getAuthority() != null)
                        .anyMatch(a -> a.getAuthority().equals("ROLE_RECEPTIONIST"));

                isStaff = authentication.getAuthorities().stream()
                        .filter(a -> a != null && a.getAuthority() != null)
                        .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
            }

            if (isAdmin) {
                return "redirect:/admin/dashboard";
            }
            
            if (isPatient) {
                // If patient profile is not yet fully completed, redirect to complete-profile, else to dashboard
                boolean profileExists = patientRepository.findByUsernameIgnoreCase(authentication.getName()).isPresent();
                if (!profileExists) {
                    return "redirect:/register/complete-profile?email=" + authentication.getName();
                }
                return "redirect:/patient/dashboard";
            }

            if (isReceptionist) {
                return "redirect:/reception/dashboard";
            }

            if (isStaff) {
                return "redirect:/staff/dashboard";
            }
            
            return "redirect:/doctor/dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/login?error=redirect";
        }
    }
}
