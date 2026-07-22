package com.cardio.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * [FIX] Kết quả kiểm tra ngưỡng cảnh báo cá nhân hoá — thay thế cho boolean
 * đơn thuần trước đây. Trước đây exceedsPersonalThreshold() chỉ trả về
 * true/false, bác sĩ thấy badge "⚡ Vượt ngưỡng riêng" nhưng KHÔNG biết
 * đang vượt vì lý do gì (nguy cơ AI cao? nhịp tim bất thường? huyết áp?).
 *
 * Giờ trả về danh sách lý do CỤ THỂ (tiếng Việt, có số liệu), để hiển thị
 * trực tiếp trên alerts.html — bác sĩ nhìn 1 cái biết ngay vấn đề gì.
 */
@Data
public class ThresholdCheckResult {
    private boolean exceeded;
    private List<String> violations = new ArrayList<>();

    public static ThresholdCheckResult notExceeded() {
        return new ThresholdCheckResult();
    }

    public void addViolation(String reason) {
        this.violations.add(reason);
        this.exceeded = true;
    }
}