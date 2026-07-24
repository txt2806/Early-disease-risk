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

    @Column(name = "DietaryAdvice", columnDefinition = "TEXT")
    private String dietaryAdvice;

    // [A.4] JSON array của 3 yếu tố SHAP ảnh hưởng nhiều nhất tại thời điểm
    // khám. Lưu lại để bác sĩ xem được giải thích chi tiết khi mở lại hồ sơ
    // cũ, không chỉ riskExplanation chung chung. Parse bằng ObjectMapper khi
    // hiển thị — xem ConsultationService.parseTopFactors().
    @Column(name = "TopFactorsJson", columnDefinition = "TEXT")
    private String topFactorsJson;

    // [A.4] JSON object gộp {trend, trend_message, history} của lần khám đó.
    @Column(name = "TrendInfoJson", columnDefinition = "TEXT")
    private String trendInfoJson;

    @Column(name = "IsAlertSent")
    private Boolean isAlertSent;

    @Column(name = "AlertCreatedAt")
    private java.time.LocalDateTime alertCreatedAt;
}