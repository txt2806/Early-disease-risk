package com.cardio.service;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.dto.MedicalMetricsDTO;
import com.cardio.model.AIRiskPrediction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ── SERVICE gọi FastAPI AI ────────────────────────────
@Service
@Slf4j
public class AIService {

    private final WebClient webClient;
    private final WebClient geminiWebClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String aiBaseUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    public AIService(
            @Value("${ai.api.url}") String aiUrl,
            @Value("${gemini.api.base.url:https://generativelanguage.googleapis.com}") String geminiBaseUrl) {
        this.aiBaseUrl = aiUrl.replace("/predict", "");
        this.webClient = WebClient.builder()
                .baseUrl(this.aiBaseUrl)
                .build();
        this.geminiWebClient = WebClient.builder()
                .baseUrl(geminiBaseUrl)
                .build();
        log.info("AIService khởi tạo — baseUrl gọi AI service: {}", this.aiBaseUrl);
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
        } catch (WebClientResponseException.UnprocessableEntity e) {
            log.warn("AI từ chối dữ liệu đầu vào (422): {}", e.getResponseBodyAsString());
            return buildInvalidDataFallback(e.getResponseBodyAsString());
        } catch (WebClientRequestException e) {
            log.error("Không kết nối được tới AI service: {}", e.getMessage());
            return buildConnectionFallback();
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi AI service: {}", e.getMessage());
            return buildConnectionFallback();
        }
    }

    // ── Dự đoán có xu hướng (Trend) ──────────────────
    public AIResponse predictWithTrend(List<AIRequest> visits) {
        try {
            Map<String, Object> body = Map.of("visits", visits);
            return webClient.post()
                    .uri("/predict/trend")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AIResponse.class)
                    .block();
        } catch (WebClientResponseException.UnprocessableEntity e) {
            log.warn("AI từ chối dữ liệu lịch sử khám (422): {}", e.getResponseBodyAsString());
            return buildInvalidDataFallback(e.getResponseBodyAsString());
        } catch (WebClientRequestException e) {
            log.error("Không kết nối được tới AI service (trend): {}", e.getMessage());
            if (!visits.isEmpty()) {
                return predict(visits.get(visits.size() - 1));
            }
            return buildConnectionFallback();
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi AI service (trend): {}", e.getMessage());
            if (!visits.isEmpty()) {
                return predict(visits.get(visits.size() - 1));
            }
            return buildConnectionFallback();
        }
    }

    // =================================================================
    // == Methods for Patient App (from user's merge request)
    // =================================================================

    @SuppressWarnings("unchecked")
    public Map<String, Object> callFastApiPredict(MedicalMetricsDTO dto, String gender) {
        Map<String, Object> body = new HashMap<>();
        body.put("age", dto.getAge());
        body.put("sex", gender);
        body.put("chest_pain_type", dto.getChestPainType());
        body.put("trestbps", dto.getRestingBloodPressure());
        body.put("chol", dto.getCholesterol());
        body.put("thalch", dto.getMaxHeartRate());
        body.put("fbs", "0.0");
        body.put("exang", "0.0");

        try {
            return webClient.post()
                    .uri("/predict/patient")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientRequestException e) {
            log.error("Cannot connect to FastAPI for patient prediction: {}", e.getMessage());
            throw new RestClientException("FastAPI connection error: " + e.getMessage());
        } catch (WebClientResponseException e) {
            log.error("FastAPI returned an error for patient prediction {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RestClientException("FastAPI returned invalid status: " + e.getStatusCode());
        }
    }

    public Map<String, String> getGeminiAdviceWithFallback(MedicalMetricsDTO dto, String riskLevel, String severity, Map<String, Object> aiPredictResult) {
        try {
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                throw new IllegalStateException("Missing GEMINI_API_KEY, cannot get advice. Falling back to FastAPI recommendations.");
            }

            String promptText = String.format(
                    "Đóng vai bác sĩ tim mạch. Phân tích chỉ số: %d tuổi, loại đau ngực mức %d, "
                    + "huyết áp %d mmHg, cholesterol %d mg/dL, nhịp tim tối đa %d bpm. "
                    + "Hệ thống AI ĐÃ XÁC ĐỊNH mức độ rủi ro: '%s' (mã nội bộ: %s). "
                    + "Dựa trên mức rủi ro, trả về CHÍNH XÁC một đối tượng JSON (không markdown):\n"
                    + "{\n"
                    + "  \"health_advice\": \"Lời khuyên vận động.\",\n"
                    + "  \"nutrition_advice\": \"Lời khuyên dinh dưỡng.\"\n"
                    + "}",
                    dto.getAge(), dto.getChestPainType(), dto.getRestingBloodPressure(),
                    dto.getCholesterol(), dto.getMaxHeartRate(), riskLevel, severity
            );

            Map<String, Object> part = new HashMap<>();
            part.put("text", promptText);

            Map<String, Object> content = new HashMap<>();
            content.put("role", "user");
            content.put("parts", List.of(part));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.2);
            generationConfig.put("responseMimeType", "application/json");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));
            requestBody.put("generationConfig", generationConfig);

            String responseBody = geminiWebClient.post()
                    .uri("/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode rootNode = mapper.readTree(responseBody);
            String rawAiText = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();
            
            if (rawAiText.startsWith("```json")) {
                rawAiText = rawAiText.substring(7, rawAiText.lastIndexOf("```")).trim();
            } else if (rawAiText.startsWith("```")) {
                 rawAiText = rawAiText.substring(3, rawAiText.lastIndexOf("```")).trim();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(rawAiText, Map.class);
            Map<String, String> result = new HashMap<>();
            result.put("health_advice", String.valueOf(parsed.getOrDefault("health_advice", "")));
            result.put("nutrition_advice", String.valueOf(parsed.getOrDefault("nutrition_advice", "")));
            return result;

        } catch (Exception e) {
            log.error("[AIService] Gemini API Error, triggering Fallback Logic: {}", e.getMessage());
            return fallbackAdviceFromFastApi(aiPredictResult);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fallbackAdviceFromFastApi(Map<String, Object> aiPredictResult) {
        Map<String, String> result = new HashMap<>();
        Object recsObj = aiPredictResult != null ? aiPredictResult.get("recommendations") : null;
        if (recsObj instanceof Map) {
            Map<String, Object> recs = (Map<String, Object>) recsObj;
            result.put("health_advice", String.valueOf(recs.getOrDefault("exercise", "")) + " " + String.valueOf(recs.getOrDefault("rest", "")));
            result.put("nutrition_advice", String.valueOf(recs.getOrDefault("diet", "")));
        } else {
            result.put("health_advice", "Hệ thống AI tạm thời bận. Vui lòng duy trì vận động nhẹ và hỏi ý kiến bác sĩ.");
            result.put("nutrition_advice", "Hệ thống AI tạm thời bận. Vui lòng ăn uống cân bằng.");
        }
        return result;
    }

    public String mapRiskTierToVietnamese(String riskTier) {
        if (riskTier == null) return "AN TOÀN";
        switch (riskTier.toUpperCase()) {
            case "HIGH":   return "RỦI RO CAO";
            case "MEDIUM": return "CẦN THEO DÕI";
            case "LOW":    return "CẦN THEO DÕI";
            default:       return "AN TOÀN";
        }
    }

    private AIResponse buildConnectionFallback() {
        AIResponse fallback = new AIResponse();
        fallback.setRisk_level("UNKNOWN");
        fallback.setProbability(0.0);
        fallback.setMessage("Không thể kết nối AI — kiểm tra server FastAPI. "
                + "Đây KHÔNG phải kết quả y tế, bác sĩ cần đánh giá lâm sàng độc lập.");
        fallback.setExplanation("Không thể kết nối server AI");
        return fallback;
    }

    private AIResponse buildInvalidDataFallback(String rawBody) {
        AIResponse fallback = new AIResponse();
        fallback.setRisk_level("INVALID_DATA");
        fallback.setProbability(0.0);
        fallback.setMessage("Dữ liệu nhập không hợp lệ — AI từ chối tính toán để tránh "
                + "đưa ra kết quả sai. Vui lòng kiểm tra lại các trường vừa nhập. "
                + "Chi tiết: " + rawBody);
        fallback.setExplanation("Dữ liệu đầu vào không khớp với mô hình AI");
        return fallback;
    }

    // ── Dự đoán MỨC ĐỘ NẶNG (model v2) ────────────────
    public com.cardio.dto.AIResponseV2 predictV2(AIRequest request) {
        try {
            return webClient.post()
                    .uri("/predict/v2")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(com.cardio.dto.AIResponseV2.class)
                    .block();
        } catch (WebClientResponseException.UnprocessableEntity e) {
            log.warn("Model v2 từ chối dữ liệu đầu vào (422): {}", e.getResponseBodyAsString());
            return buildInvalidDataFallbackV2(e.getResponseBodyAsString());
        } catch (WebClientResponseException.ServiceUnavailable e) {
            log.warn("Model v2 chưa load được (503): {}", e.getResponseBodyAsString());
            return buildConnectionFallbackV2();
        } catch (WebClientRequestException e) {
            log.error("Không kết nối được tới AI service (v2): {}", e.getMessage());
            return buildConnectionFallbackV2();
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi AI service (v2): {}", e.getMessage());
            return buildConnectionFallbackV2();
        }
    }

    public com.cardio.dto.AIResponseV2 predictV2WithTrend(List<AIRequest> visits) {
        try {
            Map<String, Object> body = Map.of("visits", visits);
            return webClient.post()
                    .uri("/predict/v2/trend")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(com.cardio.dto.AIResponseV2.class)
                    .block();
        } catch (WebClientResponseException.UnprocessableEntity e) {
            log.warn("Model v2 từ chối dữ liệu lịch sử khám (422): {}", e.getResponseBodyAsString());
            return buildInvalidDataFallbackV2(e.getResponseBodyAsString());
        } catch (WebClientRequestException e) {
            log.error("Không kết nối được tới AI service (v2 trend): {}", e.getMessage());
            if (!visits.isEmpty()) {
                return predictV2(visits.get(visits.size() - 1));
            }
            return buildConnectionFallbackV2();
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi AI service (v2 trend): {}", e.getMessage());
            if (!visits.isEmpty()) {
                return predictV2(visits.get(visits.size() - 1));
            }
            return buildConnectionFallbackV2();
        }
    }

    private com.cardio.dto.AIResponseV2 buildConnectionFallbackV2() {
        com.cardio.dto.AIResponseV2 fallback = new com.cardio.dto.AIResponseV2();
        fallback.setRiskTier("UNKNOWN");
        fallback.setSeverity("UNKNOWN");
        fallback.setMessage("Không thể kết nối AI (model v2) — kiểm tra server FastAPI. "
                + "Đây KHÔNG phải kết quả y tế.");
        return fallback;
    }

    private com.cardio.dto.AIResponseV2 buildInvalidDataFallbackV2(String rawBody) {
        com.cardio.dto.AIResponseV2 fallback = new com.cardio.dto.AIResponseV2();
        fallback.setRiskTier("INVALID_DATA");
        fallback.setSeverity("INVALID_DATA");
        fallback.setMessage("Dữ liệu nhập không hợp lệ cho model v2. Chi tiết: " + rawBody);
        return fallback;
    }

    public Map<String, Object> getModelInfo() {
        log.info("Đang gọi GET {}/model/info ...", aiBaseUrl);
        try {
            Map<String, Object> result = webClient.get()
                    .uri("/model/info")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (result == null) {
                log.warn("GET {}/model/info trả về null (không exception) — "
                        + "có thể response body rỗng hoặc không parse được thành Map.", aiBaseUrl);
                return Map.of();
            }
            log.info("Lấy /model/info thành công — {} key ở top-level: {}",
                    result.size(), result.keySet());
            return result;
        } catch (Exception e) {
            log.warn("Lỗi khi gọi GET {}/model/info — {}: {}",
                    aiBaseUrl, e.getClass().getSimpleName(), e.getMessage());
            return Map.of();
        }
    }
}
