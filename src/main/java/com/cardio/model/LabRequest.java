package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Lab_Request")
public class LabRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RequestID")
    private Integer requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PatientID", nullable = false)
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DoctorID", nullable = false)
    private DoctorProfile doctor;

    @Column(name = "RequestNotes", columnDefinition = "TEXT")
    private String requestNotes;

    @Column(name = "Status", nullable = false)
    private String status = "Pending"; // Pending, Completed

    @Column(name = "ResultNotes", columnDefinition = "TEXT")
    private String resultNotes;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "CompletedAt")
    private LocalDateTime completedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MetricID")
    private HeartClinicalMetrics clinicalMetrics;
}
