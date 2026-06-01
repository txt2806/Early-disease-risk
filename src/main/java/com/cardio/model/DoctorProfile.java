package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;

// ── MODEL (M trong MVC) ──────────────────────────────
// Ánh xạ trực tiếp với bảng Doctor_Profile trong SQL Server
@Data
@Entity
@Table(name = "Doctor_Profile")
public class DoctorProfile implements SystemUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DoctorID")
    private Integer doctorId;

    @Column(name = "Username", nullable = false, unique = true)
    private String username;

    @Column(name = "PasswordHash", nullable = false)
    private String passwordHash;

    @Column(name = "FullName", nullable = false)
    private String fullName;

    @Column(name = "Specialty")
    private String specialty;

    @Column(name = "AlertThreshold_BPM")
    private Integer alertThresholdBpm;

    @Column(name = "AlertThreshold_BP")
    private String alertThresholdBp;

    @Transient
    private String role = "DOCTOR";

    @Column(name = "Status")
    private String status = "ACTIVE";

    @Column(name = "LicenseNumber")
    private String licenseNumber;
}
