package com.cardio.dto;

import lombok.Data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

// ── DTO nhận từ FastAPI ───────────────────────────────
@Data
public class AIResponse {
    private Integer prediction;    // 0 hoặc 1
    private Double probability;    // 0.0 – 1.0
    
    @JsonProperty("risk_level")
    private String risk_level;     // LOW / MEDIUM / HIGH
    private String message;        // mô tả tiếng Việt

// --- SHAP fields (nang cap) ---
    private String explanation;
    
    @JsonProperty("top_factors")
    private List<TopFactor> top_factors;

// -- Trend fields (nang cap) ---
    private String trend;   // INCREASING / DECREASING / STABLE / UNKNOWN
    
    @JsonProperty("trend_message")
    private String trend_message; // Mô tả xu hướng tiếng việt
    private List<Double> history; // Lịch sử xác suất các lần khám

    @Data
    public static class TopFactor{
        private String feature; // tên feature tiếng việt
        private String direction; // tăng hoặc giảm
        private Double impact; //Mức độ ảnh hưởng (SHAP value)
    }

}
