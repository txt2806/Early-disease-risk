package com.cardio.config;

import com.cardio.repository.DoctorRepository;
import com.cardio.repository.PatientRepository;
import com.cardio.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class SecurityConfig {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;
    private final JdbcTemplate jdbcTemplate;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/profile", "/register", "/register/**", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/reception/**").hasAnyRole("RECEPTIONIST", "ADMIN")
                .requestMatchers("/patient/**").hasAnyRole("PATIENT", "ADMIN")
                .requestMatchers("/doctor/ai-predict/**").hasAnyRole("DOCTOR", "ADMIN")
                .requestMatchers("/doctor/alerts/**").hasAnyRole("DOCTOR", "STAFF", "ADMIN")
                .requestMatchers("/doctor/patients/*/vitals/**").hasAnyRole("STAFF", "DOCTOR", "ADMIN")
                .requestMatchers("/doctor/patients/new", "/doctor/patients/save").hasAnyRole("RECEPTIONIST", "DOCTOR", "ADMIN")
                .requestMatchers("/doctor/appointments/*/details").hasAnyRole("DOCTOR", "STAFF", "ADMIN")
                .requestMatchers("/doctor/appointments/**").hasAnyRole("RECEPTIONIST", "DOCTOR", "STAFF", "ADMIN")
                .requestMatchers("/doctor/**").hasAnyRole("DOCTOR", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard-redirect", true)
                .failureHandler((request, response, exception) -> {
                    if (exception.getCause() instanceof LockedException || exception instanceof LockedException) {
                        response.sendRedirect("/login?error=locked");
                    } else {
                        response.sendRedirect("/login?error=true");
                    }
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            try {
                java.util.List<java.util.Map<String, Object>> users = new java.util.ArrayList<>();
                try {
                    // Query app_users table directly (case-insensitive)
                    users = jdbcTemplate.queryForList(
                        "SELECT * FROM app_users WHERE LOWER(\"Username\") = LOWER(?)", username);
                    
                    if (users.isEmpty()) {
                        // Trông giống số điện thoại? Thử tìm theo Phone (hỗ trợ mọi định dạng biến thể)
                        java.util.List<String> phoneVariations = getPhoneVariations(username);
                        
                        // Tìm kiếm patient theo phone (Tối ưu hóa: Dùng findByPhoneIn thay vì findAll)
                        var patOpt = patientRepository.findByPhoneIn(phoneVariations).stream().findFirst();
                            
                        if (patOpt.isPresent()) {
                            String realUsername = patOpt.get().getUsername();
                            users = jdbcTemplate.queryForList(
                                "SELECT * FROM app_users WHERE LOWER(\"Username\") = LOWER(?)", realUsername);
                        }
                    }
                } catch (Exception sqlEx) {
                    // Log warning/info if SQL view fails, proceed to fallback JPA query
                }

                if (!users.isEmpty()) {
                    var user = users.get(0);
                    String dbUsername = (String) user.get("Username");
                    String passwordHash = (String) user.get("PasswordHash");
                    String role = (String) user.get("Role");
                    String status = (String) user.get("Status");
                    boolean isLocked = "LOCKED".equalsIgnoreCase(status);

                    return User.withUsername(dbUsername)
                        .password(passwordHash)
                        .roles(role)
                        .accountLocked(isLocked)
                        .build();
                }

                // FALLBACK: Nếu không tìm thấy trong app_users hoặc lỗi view, truy vấn trực tiếp từ các bảng Profile
                // 1. Tìm bệnh nhân theo Username/Email hoặc Phone
                var patOpt = patientRepository.findByUsernameIgnoreCase(username);
                if (!patOpt.isPresent()) {
                    java.util.List<String> phoneVariations = getPhoneVariations(username);
                    patOpt = patientRepository.findByPhoneIn(phoneVariations).stream().findFirst();
                }
                if (patOpt.isPresent()) {
                    var patient = patOpt.get();
                    return User.withUsername(patient.getUsername())
                        .password(patient.getPasswordHash())
                        .roles("PATIENT")
                        .accountLocked("LOCKED".equalsIgnoreCase(patient.getStatus()))
                        .build();
                }

                // 2. Tìm bác sĩ theo Username
                var docOpt = doctorRepository.findByUsernameIgnoreCase(username);
                if (docOpt.isPresent()) {
                    var doctor = docOpt.get();
                    return User.withUsername(doctor.getUsername())
                        .password(doctor.getPasswordHash())
                        .roles("DOCTOR")
                        .accountLocked("LOCKED".equalsIgnoreCase(doctor.getStatus()))
                        .build();
                }

                // 3. Tìm nhân sự theo Username
                var staffOpt = staffRepository.findByUsernameIgnoreCase(username);
                if (staffOpt.isPresent()) {
                    var staff = staffOpt.get();
                    return User.withUsername(staff.getUsername())
                        .password(staff.getPasswordHash())
                        .roles(staff.getRole())
                        .accountLocked("LOCKED".equalsIgnoreCase(staff.getStatus()))
                        .build();
                }

                throw new UsernameNotFoundException("Không tìm thấy người dùng: " + username);
            } catch (UsernameNotFoundException ue) {
                throw ue;
            } catch (Exception e) {
                throw new UsernameNotFoundException("Không thể truy cập cơ sở dữ liệu lúc này.", e);
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static java.util.List<String> getPhoneVariations(String username) {
        java.util.List<String> variations = new java.util.ArrayList<>();
        if (username == null) return variations;
        variations.add(username);
        
        if (username.startsWith("0") && username.length() > 1) {
            variations.add("+84" + username.substring(1));
        } else if (username.startsWith("+84") && username.length() > 3) {
            variations.add("0" + username.substring(3));
        } else if (username.startsWith("84") && username.length() > 2) {
            variations.add("0" + username.substring(2));
        }
        return variations;
    }
}
