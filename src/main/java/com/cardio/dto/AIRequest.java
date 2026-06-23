package com.cardio.dto;

import lombok.Data;

// ── DTO gửi sang FastAPI ──────────────────────────────
@Data
public class AIRequest {
    private Double age;
    private String sex;
    private String cp;
    private Double trestbps;
    private Double chol;
    private String fbs;
    private String restecg;
    private Double thalch;
    private String exang;
    private Double oldpeak;
    private String slope;
    private Double ca;
    private String thal;
}
