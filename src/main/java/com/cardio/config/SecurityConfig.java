package com.cardio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cấu hình chuỗi filter bảo mật cho các API endpoint (/api/**).
     * Được ưu tiên xử lý trước (Order 1).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**") // Chỉ áp dụng cho các request tới /api/
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll() // Cho phép truy cập public vào API đăng nhập/đăng ký
                .anyRequest().authenticated() // Các API khác yêu cầu xác thực
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // API là stateless
            .csrf(AbstractHttpConfigurer::disable) // Vô hiệu hóa CSRF cho API
            .exceptionHandling(exceptions -> exceptions
                // Khi xác thực thất bại, trả về lỗi 401 thay vì redirect
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );
        return http.build();
    }

    /**
     * Cấu hình chuỗi filter bảo mật cho giao diện web (Doctor Portal).
     * Sẽ được xử lý sau chuỗi API (Order 2).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/login").permitAll() // Tài nguyên tĩnh và trang login
                .anyRequest().authenticated() // Tất cả các trang khác yêu cầu đăng nhập
            )
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/doctor/dashboard", true));
        return http.build();
    }
}