package com.cardio.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

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

    // [D.16] Chi tiết kỹ thuật của phân tích xu hướng toàn bộ lịch sử
    // (n_visits, method, slope_per_visit, r_squared, total_change_pct...)
    // Dùng Map<String,Object> vì cấu trúc thay đổi tuỳ nhánh (2 điểm vs
    // hồi quy tuyến tính) ở phía Python — không cố định để tạo class riêng.
    @JsonProperty("trend_detail")
    private Map<String, Object> trend_detail;

    @JsonProperty("clinical_summary")
    private String clinical_summary;

    // [D.15] Cảnh báo độ tin cậy thấp khi bệnh nhân dưới 35 tuổi.
    // null nếu tuổi đủ lớn (không có cảnh báo); nếu có, chứa các key:
    // type, message, age, threshold, training_samples_below_threshold.
    @JsonProperty("age_confidence_warning")
    private Map<String, Object> age_confidence_warning;

    // [D.17] Danh sách cảnh báo khi giá trị lâm sàng nằm ngoài khoảng
    // sinh lý hợp lý. Rỗng [] nếu không có vấn đề gì. Mỗi phần tử có
    // các key: field, label, value, range, message.
    @JsonProperty("physiological_warnings")
    private List<Map<String, Object>> physiological_warnings;

    @Data
    public static class TopFactor {
        private String feature; // tên feature tiếng việt
        private String direction; // tăng hoặc giảm
        private Double impact; //Mức độ ảnh hưởng (SHAP value)
        private String clinical;
        private Object value;
    }

}