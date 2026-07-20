package com.cardio.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public interface AppointmentViewDTO {
    String getDoctorName();
    // Đổi tên từ getDepartment() thành getSpecialty() để khớp với DoctorProfile.java
    String getSpecialty();
    LocalDate getScheduledDate();
    LocalTime getTimeSlot();
    String getStatus();
}