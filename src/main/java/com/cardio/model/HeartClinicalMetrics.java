package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Heart_Clinical_Metrics")
public class HeartClinicalMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MetricID")
    private Integer metricId;

    @OneToOne
    @JoinColumn(name = "RecordID")
    private ConsultationRecord record;

    @Column(name = "RecordedBy_StaffID")
    private Integer recordedByStaffID;

    @Column(name = "ChestPainType")
    private Integer chestPainType;

    @Column(name = "RestingBP")
    private Integer restingBP;

    @Column(name = "Cholesterol")
    private Integer cholesterol;

    @Column(name = "FastingBloodSugar")
    private Boolean fastingBloodSugar;

    @Column(name = "RestingECG")
    private Integer restingECG;

    @Column(name = "MaxHeartRate")
    private Integer maxHeartRate;

    @Column(name = "ExerciseAngina")
    private Boolean exerciseAngina;

    // Additional fields to store all AI inputs
    @Column(name = "Oldpeak")
    private Double oldpeak;

    @Column(name = "Slope")
    private String slope;

    @Column(name = "Ca")
    private Integer ca;

    @Column(name = "Thal")
    private String thal;

    @Column(name = "Age")
    private Integer age;

    @Column(name = "Sex")
    private String sex;

    @Column(name = "RecordedAt")
    private LocalDateTime recordedAt;

    @Column(name = "Temperature")
    private Double temperature;

    @Column(name = "SpO2")
    private Integer spO2;

    @Column(name = "BloodTest", columnDefinition = "TEXT")
    private String bloodTest;

    @Column(name = "UrineTest", columnDefinition = "TEXT")
    private String urineTest;

    @Column(name = "Xray", columnDefinition = "TEXT")
    private String xray;

    @Column(name = "MRI", columnDefinition = "TEXT")
    private String mri;

    @Column(name = "CT", columnDefinition = "TEXT")
    private String ct;

    @Column(name = "Ultrasound", columnDefinition = "TEXT")
    private String ultrasound;
}
