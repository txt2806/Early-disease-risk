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
    private final String chatUrl;

    public ChatService(@Value("${ai.api.chat.url}") String chatUrl) {
        this.chatUrl = chatUrl;
        this.webClient = WebClient.builder().build();
    }

    /**
     * Tương thích ngược cho bác sĩ
     */
    public ChatResponse sendMessage(ChatRequest request) {
        return sendMessage(request, "doctor");
    }

    /**
     * Gửi tin nhắn sang FastAPI với endpoint động dựa theo role.
     */
    public ChatResponse sendMessage(ChatRequest request, String role) {
        String targetUrl = chatUrl;
        
        if ("staff".equalsIgnoreCase(role)) {
            // Do máy chủ AI chạy production từ xa (Render) không có sẵn endpoint /chat/staff và ta không thể tự deploy lên Render của phòng khám,
            // chúng ta sẽ gọi vào endpoint /chat/doctor hiện hữu và chèn thêm chỉ thị vai trò (Prompt Injection) vào tin nhắn để định hình phản hồi của AI.
            String originalMessage = request.getMessage();
            String staffPrompt = 
                "[HỆ THỐNG NHẮC NHỞ CHUYÊN MÔN: Bạn đang trả lời ĐIỀU DƯỠNG / NHÂN VIÊN Y TẾ (Medical Staff) của phòng khám CardioCare, không phải Bác sĩ. "
                + "Hãy điều chỉnh câu trả lời phù hợp: tập trung giải thích chỉ số sinh tồn (huyết áp, nhịp tim, đường huyết), hướng dẫn nhập liệu, nhắc nhở họ KHÔNG tự ý chẩn đoán hoặc kê đơn vì đó là quyền hạn của Bác sĩ. "
                + "Trả lời ngắn gọn, thân thiện, tiếng Việt.]\n\n"
                + "[Câu hỏi của Điều dưỡng]: " + originalMessage;
            request.setMessage(staffPrompt);
        }

        try {
            return webClient.post()
                    .uri(targetUrl)
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
            log.warn("Lỗi khi gọi Gemini qua FastAPI (502): {}", e.getResponseBodyAsString());
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

    private ChatResponse buildErrorResponse(String message) {
        ChatResponse fallback = new ChatResponse();
        fallback.setReply(message);
        fallback.setDisclaimer(
                "Câu trả lời từ AI chỉ mang tính hỗ trợ tham khảo, không thay thế "
                + "đánh giá lâm sàng và quyết định chuyên môn của bác sĩ.");
        return fallback;
    }
}