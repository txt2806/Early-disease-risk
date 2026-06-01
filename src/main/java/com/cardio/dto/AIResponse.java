package com.cardio.dto;

import lombok.Data;

// ── DTO nhận từ FastAPI ───────────────────────────────
@Data
public class AIResponse {
    private Integer prediction;    // 0 hoặc 1
    private Double probability;    // 0.0 – 1.0
    private String risk_level;     // LOW / MEDIUM / HIGH
    private String message;        // mô tả tiếng Việt
}
