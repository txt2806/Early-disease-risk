package com.cardio.service;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.model.AIRiskPrediction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;

// ── SERVICE gọi FastAPI AI ────────────────────────────
@Service
public class AIService {

    private final WebClient webClient;

    public AIService(@Value("${ai.api.url}") String aiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(aiUrl.replace("/predict", ""))
                .build();
    }

    // ── Dự đoán đơn (có SHAP explanation) ────────────
    public AIResponse predict(AIRequest request) {
        try {
            return webClient.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AIResponse.class)
                    .block();
        } catch (Exception e) {
            return buildFallback();
        }
    }
 
    // ── Dự đoán có xu hướng (Trend) ──────────────────
    // Gọi /predict/trend với danh sách các lần khám cũ + mới
    public AIResponse predictWithTrend(List<AIRequest> visits) {
        try {
            Map<String, Object> body = Map.of("visits", visits);
            return webClient.post()
                    .uri("/predict/trend")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AIResponse.class)
                    .block();
        } catch (Exception e) {
            // Fallback: gọi predict thường với lần mới nhất
            if (!visits.isEmpty()) {
                return predict(visits.get(visits.size() - 1));
            }
            return buildFallback();
        }
    }
 
    private AIResponse buildFallback() {
        AIResponse fallback = new AIResponse();
        fallback.setRisk_level("UNKNOWN");
        fallback.setProbability(0.0);
        fallback.setMessage("Không thể kết nối AI — kiểm tra server FastAPI");
        fallback.setExplanation("Không thể kết nối server AI");
        return fallback;
    }
}
