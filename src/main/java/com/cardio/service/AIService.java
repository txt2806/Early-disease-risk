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
    private final String aiBaseUrl; // [FIX] lưu lại để log ra baseUrl thật đang gọi, dễ đối chiếu

    public AIService(@Value("${ai.api.url}") String aiUrl) {
        this.aiBaseUrl = aiUrl.replace("/predict", "");
        this.webClient = WebClient.builder()
                .baseUrl(this.aiBaseUrl)
                .build();
        // [FIX] Log NGAY lúc khởi động server để xác nhận 100% baseUrl thật
        // sự được dùng — tránh nghi ngờ nhầm giữa file properties và giá trị
        // Spring THỰC SỰ đọc được (profile khác, override bằng biến môi
        // trường/ -D lúc chạy...).
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
     * risk_level = "UNKNOWN" — frontend PHẢI hiển thị trạng thái cảnh báo riêng
     * biệt (không phải màu xanh như LOW) vì đây không phải đánh giá "an toàn",
     * mà là "chưa đánh giá được".
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
     * AI service VẪN HOẠT ĐỘNG nhưng từ chối dữ liệu đầu vào (HTTP 422) vì có
     * trường không khớp với những gì model đã học lúc train. Phân biệt rõ với
     * lỗi kết nối vì nguyên nhân và cách xử lý khác hẳn nhau: đây là lỗi DỮ
     * LIỆU, cần kiểm tra lại form nhập liệu, không phải lỗi server.
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

    // ── Thông tin model (v1 + v2) ─────────────────────
    // [FIX] Trước đây "Thông tin model" ở giao diện bị hardcode cứng trong
    // HTML (luôn ghi "Random Forest (Tuned)"...), không phản ánh việc hệ
    // thống đang dùng 2 model khác thuật toán (v1 Random Forest, v2 XGBoost).
    // Hàm này gọi GET /model/info từ AI service để lấy thông tin THẬT, luôn
    // đúng với model đang thực sự chạy — không cần sửa tay HTML mỗi khi
    // train lại/đổi thuật toán.
    //
    // [FIX 2] Trước đây try/catch chỉ log khi CÓ EXCEPTION — nhưng WebClient
    // với .block() có thể trả về null MÀ KHÔNG NÉM EXCEPTION trong một số
    // trường hợp (vd. response 2xx nhưng body rỗng/không parse được thành
    // Map theo cách mong đợi). Khi đó code cũ chạy thẳng qua try, return
    // null, KHÔNG BAO GIỜ chạm log.warn trong catch — khiến việc debug bế
    // tắc hoàn toàn (đúng tình huống đã xảy ra: giao diện báo lỗi nhưng
    // terminal không in ra bất kỳ dòng nào liên quan). Nay log RÕ RÀNG ở cả
    // 3 nhánh: bắt đầu gọi, gọi xong nhưng null, và có exception — để không
    // bao giờ rơi vào tình trạng "im lặng" như vậy nữa.
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
            return Map.of(); // view sẽ hiển thị "N/A" nếu rỗng, không vỡ trang
        }
    }
}
