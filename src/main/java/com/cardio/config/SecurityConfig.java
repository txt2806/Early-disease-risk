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
<<<<<<< Updated upstream
    private final JdbcTemplate jdbcTemplate;
=======
    private final SystemLogRepository systemLogRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
>>>>>>> Stashed changes

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/profile", "/register", "/register/**", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/reception/**").hasAnyRole("RECEPTIONIST", "ADMIN")
                .requestMatchers("/patient/**").hasAnyRole("PATIENT", "ADMIN")
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
<<<<<<< Updated upstream
                // Query app_users table directly
                java.util.List<java.util.Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT * FROM app_users WHERE \"Username\" = ?", username);
                
                if (users.isEmpty()) {
                    // Trông giống số điện thoại? Thử tìm theo Phone
                    String phoneNorm1 = username;
                    String phoneNorm2 = username;
                    if (username.startsWith("0")) {
                        phoneNorm1 = "+84" + username.substring(1);
                    } else if (username.startsWith("+84")) {
                        phoneNorm2 = "0" + username.substring(3);
                    }
                    final String norm1 = phoneNorm1;
                    final String norm2 = phoneNorm2;
                    
                    // Tìm kiếm patient theo phone
                    var patOpt = patientRepository.findAll().stream()
                        .filter(p -> p.getPhone() != null && 
                                    (p.getPhone().equals(username) || 
                                     p.getPhone().equals(norm1) || 
                                     p.getPhone().equals(norm2)))
=======
                // Query app_users table directly (case-insensitive)
                java.util.List<java.util.Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT * FROM app_users WHERE LOWER(\"Username\") = LOWER(?)", username);
                
                if (users.isEmpty()) {
                    // Trông giống số điện thoại? Thử tìm theo Phone (hỗ trợ mọi định dạng biến thể)
                    java.util.List<String> phoneVariations = com.cardio.util.AuthUtil.getPhoneVariations(username);
                    
                    // Tìm kiếm patient theo phone (Tối ưu hóa: Dùng findByPhoneIn thay vì findAll)
                    var patOpt = patientRepository.findByPhoneIn(phoneVariations).stream()
>>>>>>> Stashed changes
                        .findFirst();
                        
                    if (patOpt.isPresent()) {
                        String realUsername = patOpt.get().getUsername();
                        users = jdbcTemplate.queryForList(
                            "SELECT * FROM app_users WHERE \"Username\" = ?", realUsername);
                    }
                }

                if (users.isEmpty()) {
                    throw new UsernameNotFoundException("Không tìm thấy người dùng: " + username);
                }

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

<<<<<<< Updated upstream
=======
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
>>>>>>> Stashed changes
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
}
