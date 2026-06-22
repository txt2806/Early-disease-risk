package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AppointmentID")
    private Integer appointmentId;

    @ManyToOne
    @JoinColumn(name = "PatientID")
    private PatientProfile patient;

    @ManyToOne
    @JoinColumn(name = "DoctorID")
    private DoctorProfile doctor;

    @Column(name = "ScheduledDate", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "TimeSlot")
    private LocalTime timeSlot;

    @Column(name = "EndTime")
    private LocalTime endTime;

    @Column(name = "StartTime")
    private LocalTime startTime;

    @Column(name = "RequestTime")
    private LocalDateTime requestTime = LocalDateTime.now();

    @Column(name = "Status", nullable = false)
    private String status = "Pending"; // Pending, Confirmed, CheckedIn, InProgress, Completed, Cancelled

    @Column(name = "RoomNumber")
    private String roomNumber;

    @Column(name = "PreliminaryStatus")
    private String preliminaryStatus;

    @Column(name = "QueueNumber")
    private Integer queueNumber;
}

