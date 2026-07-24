package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "Patient_Profile", indexes = {
    @Index(name = "idx_patient_phone", columnList = "Phone"),
    @Index(name = "idx_patient_status", columnList = "Status")
})
public class PatientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PatientID")
    private Integer patientId;

    @Column(name = "Username", nullable = false, unique = true)
    private String username;

    @Column(name = "PasswordHash", nullable = false)
    private String passwordHash;

    @Column(name = "FullName", nullable = false)
    private String fullName;

    @Column(name = "DOB", nullable = false)
    private LocalDate dob;

    @Column(name = "Gender", nullable = false)
    private String gender;

    @Column(name = "Phone")
    private String phone;

    @Column(name = "Address")
    private String address;

    @Column(name = "Status")
    private String status = "ACTIVE";

    @Column(name = "is_alert")
    private Integer isAlert = 0;
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    // Cờ kiểm tra bệnh nhân có cần đổi mật khẩu trong lần đăng nhập đầu tiên không.
    // Mặc định là true cho tài khoản mới.
    @Column(name = "is_first_login")
    private Boolean isFirstLogin = true;

    @Column(name = "fcm_token")
    private String fcmToken;

    public Boolean isFirstLogin() {
        return isFirstLogin != null && isFirstLogin;
    }

    public void setFirstLogin(Boolean firstLogin) {
        this.isFirstLogin = firstLogin;
    }
}
