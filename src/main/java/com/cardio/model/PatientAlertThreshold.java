package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Patient_Alert_Threshold",
       uniqueConstraints = @UniqueConstraint(columnNames = {"PatientID", "DoctorID"}))
public class PatientAlertThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer thresholdId;

    @ManyToOne
    @JoinColumn(name = "PatientID", nullable = false)
    private PatientProfile patient;

    @ManyToOne
    @JoinColumn(name = "DoctorID", nullable = false)
    private DoctorProfile doctor;

    // Ngưỡng nguy cơ AI (%) — AI cảnh báo khi vượt mức này
    @Column(name = "RiskScoreThreshold")
    private Double riskScoreThreshold = 40.0; // mặc định 40%

    // Nhịp tim tối đa (bpm)
    @Column(name = "MaxBpm")
    private Integer maxBpm = 100;

    // Huyết áp tâm thu (mmHg)
    @Column(name = "MaxSystolicBp")
    private Integer maxSystolicBp = 140;

    // Ghi chú bác sĩ về lý do đặt ngưỡng này
    @Column(name = "Notes", columnDefinition = "TEXT")
    private String notes;
}