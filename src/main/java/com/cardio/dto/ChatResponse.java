package com.cardio.dto;

import lombok.Data;

/**
 * [D.21] Khớp đúng ChatResponse trong api_v2.py: { "reply": "...", "disclaimer": "..." }
 */
@Data
public class ChatResponse {
    private String reply;
    private String disclaimer;
}