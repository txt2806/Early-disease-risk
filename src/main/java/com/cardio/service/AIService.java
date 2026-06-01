package com.cardio.service;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

// ── SERVICE gọi FastAPI AI ────────────────────────────
@Service
public class AIService {

    private final WebClient webClient;

    public AIService(@Value("${ai.api.url}") String aiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(aiUrl.replace("/predict", ""))
                .build();
    }

    public AIResponse predict(AIRequest request) {
        try {
            return webClient.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AIResponse.class)
                    .block();
        } catch (Exception e) {
            // Trả về fallback nếu server AI tắt
            AIResponse fallback = new AIResponse();
            fallback.setRisk_level("UNKNOWN");
            fallback.setProbability(0.0);
            fallback.setMessage("Không thể kết nối AI — kiểm tra server FastAPI");
            return fallback;
        }
    }
}
