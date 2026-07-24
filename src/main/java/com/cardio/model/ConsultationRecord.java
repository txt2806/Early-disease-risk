package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Consultation_Record", indexes = {
    @Index(name = "idx_record_date", columnList = "VisitDate")
})
public class ConsultationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RecordID")
    private Integer recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PatientID")
    private PatientProfile patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DoctorID")
    private DoctorProfile doctor;

    @Column(name = "VisitDate", nullable = false)
    private LocalDateTime visitDate;

    @Column(name = "ConsultationNotes", columnDefinition = "TEXT")
    private String consultationNotes;

    @Column(name = "TreatmentPlan", columnDefinition = "TEXT")
    private String treatmentPlan;

    @Column(name = "Status")
    private String status;

    @OneToOne(mappedBy = "record", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private HeartClinicalMetrics clinicalMetrics;

    @OneToOne(mappedBy = "record", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AIRiskPrediction aiRiskPrediction;
}
