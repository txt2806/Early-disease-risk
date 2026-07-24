package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Staff_Profile")
public class StaffProfile implements SystemUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "StaffID")
    private Integer staffId;

    @Column(name = "Username", nullable = false, unique = true)
    private String username;

    @Column(name = "PasswordHash", nullable = false)
    private String passwordHash;

    @Column(name = "FullName", nullable = false)
    private String fullName;

    @Column(name = "Role", nullable = false)
    private String role; // "ADMIN", "STAFF", "RECEPTIONIST"

    @Column(name = "Status")
    private String status = "ACTIVE";

    @Override
    public Integer getDoctorId() {
        return this.staffId;
    }

    @Override
    public String getLicenseNumber() {
        return null;
    }

    @Override
    public Integer getAlertThresholdBpm() {
        return null;
    }

    @Override
    public String getAlertThresholdBp() {
        return null;
    }
    @Override
    public String getRoomNumber() {
        return null;
    }
}
