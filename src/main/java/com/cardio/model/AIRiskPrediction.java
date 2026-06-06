package com.cardio.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "AI_Risk_Prediction")
public class AIRiskPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PredictionID")
    private Integer predictionId;

    @OneToOne
    @JoinColumn(name = "RecordID")
    private ConsultationRecord record;

    @Column(name = "RiskScore")
    private BigDecimal riskScore;

    @Column(name = "RiskLevel")
    private String riskLevel;

    @Column(name = "RiskExplanation", columnDefinition = "TEXT")
    private String riskExplanation;

    @Column(name = "HealthAdvice", columnDefinition = "TEXT")
    private String healthAdvice;

    @Column(name = "IsAlertSent")
    private Boolean isAlertSent;
}
