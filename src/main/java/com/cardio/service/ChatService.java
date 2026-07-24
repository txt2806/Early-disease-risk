package com.cardio.service;

import com.cardio.dto.ChatRequest;
import com.cardio.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.extern.slf4j.Slf4j;

// ── SERVICE gọi FastAPI /chat/doctor ──────────────────
// [D.21] Theo đúng pattern của AIService.java (WebClient + phân loại lỗi
// connection vs lỗi dữ liệu vs lỗi server) để nhất quán trong toàn bộ
// codebase. Đây là chatbot RIÊNG cho bác sĩ — KHÁC hoàn toàn với chatbot
// /chat/patient (dùng cho app Flutter bệnh nhân, không liên quan tới
// service này).
@Service
@Slf4j
public class ChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(@Value("${ai.api.chat.url}") String chatUrl) {
        // chatUrl đã là full URL (vd. http://host:8000/chat/doctor), nên
        // dùng trực tiếp làm baseUrl và gọi uri(""), khác với AIService
        // (baseUrl bỏ "/predict" rồi append lại path con cho từng endpoint),
        // vì ChatService chỉ có đúng 1 endpoint duy nhất, không cần tách.
        this.webClient = WebClient.builder()
                .baseUrl(chatUrl)
                .build();
    }

    /**
     * Gửi tin nhắn sang FastAPI /chat/doctor. Trả về ChatResponse nếu thành
     * công, hoặc 1 ChatResponse "fallback" mang nội dung lỗi dễ hiểu nếu
     * thất bại — KHÔNG throw exception ra ngoài, để Controller luôn nhận
     * được object hợp lệ và hiển thị cho bác sĩ một cách rõ ràng.
     */
    public ChatResponse sendMessage(ChatRequest request) {
        try {
            return webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block();
        } catch (WebClientResponseException.ServiceUnavailable e) {
            log.warn("Chatbot chưa được cấu hình ở server AI (503): {}", e.getResponseBodyAsString());
            return buildErrorResponse(
                    "Trợ lý AI chưa được cấu hình trên server (thiếu API key Gemini). "
                    + "Vui lòng liên hệ quản trị hệ thống.");
        } catch (WebClientResponseException.BadGateway e) {
            // [FIX] Trước đây LUÔN hiển thị 1 message chung chung "có thể do hết
            // quota" cho MỌI loại lỗi 502 — kể cả khi FastAPI đã trả về "detail"
            // rất cụ thể (ví dụ: "Câu trả lời bị cắt do vượt giới hạn độ dài...
            // thử lại với câu hỏi ngắn gọn hơn"). Giờ ưu tiên hiển thị đúng
            // "detail" đó cho bác sĩ — hành động khắc phục rõ ràng hơn nhiều
            // so với đoán mò "có thể do hết quota".
            String detail = extractDetail(e.getResponseBodyAsString());
            log.warn("Lỗi khi gọi Gemini qua FastAPI (502): {}", e.getResponseBodyAsString());
            if (detail != null && !detail.isBlank()) {
                return buildErrorResponse("Trợ lý AI gặp sự cố: " + detail);
            }
            return buildErrorResponse(
                    "Trợ lý AI tạm thời không phản hồi được (có thể do hết quota miễn phí). "
                    + "Vui lòng thử lại sau ít phút.");
        } catch (WebClientRequestException e) {
            log.error("Không kết nối được tới AI service (chat): {}", e.getMessage());
            return buildErrorResponse(
                    "Không kết nối được tới server AI. Vui lòng kiểm tra FastAPI đã chạy chưa.");
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi chatbot: {}", e.getMessage());
            return buildErrorResponse("Đã có lỗi xảy ra. Vui lòng thử lại.");
        }
    }

    /**
     * [FIX] Parse field "detail" từ JSON lỗi mà FastAPI trả về
     * (dạng {"detail": "..."} — mặc định của HTTPException). Trả về null
     * nếu không parse được, để caller tự dùng message fallback an toàn.
     */
    private String extractDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode detailNode = node.get("detail");
            return detailNode != null ? detailNode.asText() : null;
        } catch (Exception ex) {
            log.debug("Không parse được response body lỗi từ FastAPI: {}", ex.getMessage());
            return null;
        }
    }

    private ChatResponse buildErrorResponse(String message) {
        ChatResponse fallback = new ChatResponse();
        fallback.setReply(message);
        fallback.setDisclaimer(
                "Câu trả lời từ AI chỉ mang tính hỗ trợ tham khảo, không thay thế "
                + "đánh giá lâm sàng và quyết định chuyên môn của bác sĩ.");
        return fallback;
    }
}