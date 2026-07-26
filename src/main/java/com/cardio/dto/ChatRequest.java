package com.cardio.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

import java.util.ArrayList;

/**
 * [D.21] DTO gửi sang FastAPI /chat/doctor.
 * Khớp đúng schema DoctorChatRequest trong api_v2.py:
 *   { "message": "...", "history": [...], "predict_context": {...} }
 */
@Data
public class ChatRequest {
    private String message;
    private Integer patientId;
    private List<ChatTurn> history = new ArrayList<>();
    private Map<String, Object> predict_context; // tùy chọn — null nếu không gắn kèm kết quả AI

    @Data
    public static class ChatTurn {
        private String role; // "user" hoặc "model"
        private String text;

        public ChatTurn() {
        }

        public ChatTurn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}