package com.cardio.model;

public interface SystemUser {
    Integer getDoctorId(); // Unified ID for Thymeleaf dashboard
    String getUsername();
    String getPasswordHash();
    String getFullName();
    String getRole();
    String getStatus();
    String getLicenseNumber();
    Integer getAlertThresholdBpm();
    String getAlertThresholdBp();
    String getRoomNumber();
}
