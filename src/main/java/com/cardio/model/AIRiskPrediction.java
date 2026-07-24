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
    // khám (Model V1). Lưu lại để bác sĩ xem được giải thích chi tiết khi mở
    // lại hồ sơ cũ, không chỉ riskExplanation chung chung. Parse bằng
    // ObjectMapper khi hiển thị — xem ConsultationService.parseTopFactors().
    @Column(name = "TopFactorsJson", columnDefinition = "TEXT")
    private String topFactorsJson;

    // [A.4] JSON object gộp {trend, trend_message, history} của lần khám đó (Model V1).
    @Column(name = "TrendInfoJson", columnDefinition = "TEXT")
    private String trendInfoJson;

    @Column(name = "IsAlertSent")
    private Boolean isAlertSent;

    @Column(name = "AlertCreatedAt")
    private java.time.LocalDateTime alertCreatedAt;

    // ═══════════════════════════════════════════════════════════
    // [MỚI] Kết quả Model V2 (mức độ nặng) tại lần khám này.
    // TẤT CẢ đều có thể null — nghĩa là lần khám đó chỉ chạy V1, bác sĩ
    // chưa bấm "Chẩn đoán mức độ" trước khi lưu. Việc lưu lại các trường
    // này giúp:
    //   1. patient-detail.html hiển thị đúng mức độ nặng đã kết luận khi
    //      xem lại hồ sơ cũ (trước đây hoàn toàn không lưu, không thể xem lại).
    //   2. Cho phép sau này tính lại xu hướng mức độ nặng từ chính DB thay
    //      vì phải gọi lại FastAPI mỗi lần muốn xem (dù hiện tại trend V2
    //      vẫn đang tính động qua /predict/v2/trend dựa trên
    //      HeartClinicalMetrics của các lần khám cũ).
    // ═══════════════════════════════════════════════════════════

    @Column(name = "SeverityV2")
    private String severityV2; // NO_DISEASE / MILD / MODERATE / SEVERE

    @Column(name = "RiskTierV2")
    private String riskTierV2; // NONE / LOW / MEDIUM / HIGH

    @Column(name = "ConfidenceV2")
    private Double confidenceV2; // 0.0 - 1.0

    // JSON object {"NO_DISEASE":.., "MILD":.., "MODERATE":.., "SEVERE":..}
    @Column(name = "ProbabilitiesV2Json", columnDefinition = "TEXT")
    private String probabilitiesV2Json;

    // JSON array các TopFactor (SHAP "ủng hộ/không ủng hộ") của Model V2
    @Column(name = "TopFactorsV2Json", columnDefinition = "TEXT")
    private String topFactorsV2Json;

    // JSON object gộp {trend, trend_message, prob_severe_history} của Model V2
    @Column(name = "TrendInfoV2Json", columnDefinition = "TEXT")
    private String trendInfoV2Json;
}