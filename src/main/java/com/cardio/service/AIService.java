package com.cardio.service;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.model.AIRiskPrediction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;

// ── SERVICE gọi FastAPI AI ────────────────────────────
@Service
@Slf4j
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
        } catch (WebClientResponseException.UnprocessableEntity e) {
            // HTTP 422 từ FastAPI: server AI VẪN ĐANG CHẠY BÌNH THƯỜNG,
            // nhưng dữ liệu gửi lên không khớp với encoder đã train
            // (xem InvalidCategoricalValueError trong api_v2.py).
            // KHÁC HẲN bản chất với lỗi mất kết nối — không được gộp chung.
            log.warn("AI từ chối dữ liệu đầu vào (422): {}", e.getResponseBodyAsString());
            return buildInvalidDataFallback(e.getResponseBodyAsString());
        } catch (WebClientRequestException e) {
            // Lỗi kết nối THẬT: không gọi được tới FastAPI (server tắt, sai host/port, timeout...)
            log.error("Không kết nối được tới AI service: {}", e.getMessage());
            return buildConnectionFallback();
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi AI service: {}", e.getMessage());
            return buildConnectionFallback();
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
        } catch (WebClientResponseException.UnprocessableEntity e) {
            log.warn("AI từ chối dữ liệu lịch sử khám (422): {}", e.getResponseBodyAsString());
            return buildInvalidDataFallback(e.getResponseBodyAsString());
        } catch (WebClientRequestException e) {
            log.error("Không kết nối được tới AI service (trend): {}", e.getMessage());
            // Fallback: gọi predict thường với lần mới nhất CHỈ KHI đây thực sự
            // là lỗi kết nối — nếu là lỗi 422 thì đã được catch ở nhánh trên,
            // không nên thử lại vì dữ liệu vẫn sẽ sai như cũ.
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

    /**
     * AI service KHÔNG kết nối được (server tắt, sai cấu hình, timeout...).
     * risk_level = "UNKNOWN" — frontend PHẢI hiển thị trạng thái cảnh báo
     * riêng biệt (không phải màu xanh như LOW) vì đây không phải đánh giá
     * "an toàn", mà là "chưa đánh giá được".
     */
    private AIResponse buildConnectionFallback() {
        AIResponse fallback = new AIResponse();
        fallback.setRisk_level("UNKNOWN");
        fallback.setProbability(0.0);
        fallback.setMessage("Không thể kết nối AI — kiểm tra server FastAPI. "
                + "Đây KHÔNG phải kết quả y tế, bác sĩ cần đánh giá lâm sàng độc lập.");
        fallback.setExplanation("Không thể kết nối server AI");
        return fallback;
    }

    /**
     * AI service VẪN HOẠT ĐỘNG nhưng từ chối dữ liệu đầu vào (HTTP 422) vì
     * có trường không khớp với những gì model đã học lúc train. Phân biệt
     * rõ với lỗi kết nối vì nguyên nhân và cách xử lý khác hẳn nhau: đây là
     * lỗi DỮ LIỆU, cần kiểm tra lại form nhập liệu, không phải lỗi server.
     */
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
}