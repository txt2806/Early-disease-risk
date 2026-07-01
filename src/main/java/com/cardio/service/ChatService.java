package com.cardio.service;

import com.cardio.dto.ChatRequest;
import com.cardio.dto.ChatResponse;
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

    public ChatService(@Value("${ai.api.chat.url}") String chatUrl) {
        // chatUrl đã là full URL (vd. http://host:8000/chat/doctor), nên
        // dùng trực tiếp làm baseUrl và gọi uri("") — khác với AIService
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
            // 503 từ FastAPI: chatbot chưa cấu hình (thiếu GEMINI_API_KEY phía server AI)
            log.warn("Chatbot chưa được cấu hình ở server AI (503): {}", e.getResponseBodyAsString());
            return buildErrorResponse(
                    "Trợ lý AI chưa được cấu hình trên server (thiếu API key Gemini). "
                    + "Vui lòng liên hệ quản trị hệ thống.");
        } catch (WebClientResponseException.BadGateway e) {
            // 502 từ FastAPI: lỗi khi gọi Gemini (hết quota, mất mạng tới Google...)
            log.warn("Lỗi khi gọi Gemini qua FastAPI (502): {}", e.getResponseBodyAsString());
            return buildErrorResponse(
                    "Trợ lý AI tạm thời không phản hồi được (có thể do hết quota miễn phí). "
                    + "Vui lòng thử lại sau ít phút.");
        } catch (WebClientRequestException e) {
            // Lỗi kết nối THẬT: không gọi được tới FastAPI (server tắt, sai host/port, timeout...)
            log.error("Không kết nối được tới AI service (chat): {}", e.getMessage());
            return buildErrorResponse(
                    "Không kết nối được tới server AI. Vui lòng kiểm tra FastAPI đã chạy chưa.");
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi chatbot: {}", e.getMessage());
            return buildErrorResponse("Đã có lỗi xảy ra. Vui lòng thử lại.");
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