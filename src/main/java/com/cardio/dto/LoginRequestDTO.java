package com.cardio.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {
    // Đổi tên từ patientId thành username để khớp với field dùng để đăng nhập trong PatientProfile.java
    private String username;
    private String password;
}