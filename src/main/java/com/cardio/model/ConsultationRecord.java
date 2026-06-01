package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Consultation_Record")
public class ConsultationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RecordID")
    private Integer recordId;

    @ManyToOne
    @JoinColumn(name = "PatientID")
    private PatientProfile patient;

    @ManyToOne
    @JoinColumn(name = "DoctorID")
    private DoctorProfile doctor;

    @Column(name = "VisitDate", nullable = false)
    private LocalDateTime visitDate;

    @Column(name = "ConsultationNotes", columnDefinition = "NVARCHAR(MAX)")
    private String consultationNotes;

    @Column(name = "TreatmentPlan", columnDefinition = "NVARCHAR(MAX)")
    private String treatmentPlan;

    @Column(name = "Status")
    private String status;
}
