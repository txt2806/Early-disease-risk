package com.cardio.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

// ── DTO nhận từ FastAPI /predict/v2 và /predict/v2/trend ──────────
@Data
public class AIResponseV2 {
    @JsonProperty("predicted_class")
    private Integer predictedClass;

    private String severity;   // NO_DISEASE / MILD / MODERATE / SEVERE

    @JsonProperty("risk_tier")
    private String riskTier;   // NONE / LOW / MEDIUM / HIGH / UNKNOWN / INVALID_DATA

    private Double confidence;

    private Map<String, Double> probabilities; // {"NO_DISEASE":.., "MILD":.., ...}

    private String message;

    // -- Trend fields (khi gọi /predict/v2/trend) --
    private String trend;

    @JsonProperty("trend_message")
    private String trendMessage;

    @JsonProperty("trend_detail")
    private Map<String, Object> trendDetail;

    @JsonProperty("class_history")
    private List<Integer> classHistory;

    @JsonProperty("prob_severe_history")
    private List<Double> probSevereHistory;

    @JsonProperty("age_confidence_warning")
    private Map<String, Object> ageConfidenceWarning;

    @JsonProperty("physiological_warnings")
    private List<Map<String, Object>> physiologicalWarnings;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("n_classes")
    private Integer nClasses;

    @JsonProperty("class_names")
    private List<String> classNames;
}