package com.cardio.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public interface AppointmentViewDTO {
    Integer getAppointmentId();
    String getDoctorName();
    String getSpecialty();
    LocalDate getScheduledDate();
    LocalTime getTimeSlot();
    String getStatus();
}