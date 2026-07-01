package com.cardio.controller;

import com.cardio.model.SystemLog;
import com.cardio.repository.SystemLogRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sepay")
@RequiredArgsConstructor
@Slf4j
public class SePayWebhookController {

    private final SystemLogRepository systemLogRepository;

    @Value("${sepay.api.key:}")
    private String sepayApiKey;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody SePayWebhookPayload payload,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("Received SePay webhook payload: {}", payload);

            // 1. Kiểm tra API Key (nếu đã cấu hình)
            if (sepayApiKey != null && !sepayApiKey.trim().isEmpty()) {
                String authHeader = request.getHeader("Authorization");
                boolean isValid = false;
                
                if (authHeader != null) {
                    // Định dạng của SePay gửi lên có thể là "Apikey xxx" hoặc chỉ "xxx"
                    String cleanToken = authHeader.replace("Apikey ", "").trim();
                    if (sepayApiKey.trim().equals(cleanToken)) {
                        isValid = true;
                    }
                }
                
                if (!isValid) {
                    log.warn("SePay Webhook unauthorized: Invalid API Key in Authorization header.");
                    response.put("success", false);
                    response.put("error", "Unauthorized: Invalid API Key");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                }
            }

            // 2. Validate payload tối thiểu
            if (payload.getId() == null) {
                log.warn("SePay Webhook: Missing transaction ID in payload.");
                response.put("success", false);
                response.put("error", "Missing transaction ID");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 3. Kiểm tra trùng lặp (Deduplication)
            String targetSignature = "ID giao dịch SePay: " + payload.getId();
            boolean isDuplicate = systemLogRepository.existsByActionAndDetailsContaining(
                    "SEPAY_TRANSACTION", targetSignature);

            if (isDuplicate) {
                log.info("SePay Webhook: Transaction ID {} is already processed (duplicate request).", payload.getId());
                response.put("success", true);
                response.put("message", "Already processed");
                return ResponseEntity.ok(response);
            }

            // 4. Lưu log giao dịch vào cơ sở dữ liệu SystemLog
            SystemLog transactionLog = new SystemLog();
            transactionLog.setUsername("SEPAY_WEBHOOK");
            transactionLog.setAction("SEPAY_TRANSACTION");
            
            StringBuilder details = new StringBuilder();
            details.append("Nhận thông báo chuyển khoản thành công từ SePay.\n");
            details.append("ID giao dịch SePay: ").append(payload.getId()).append("\n");
            details.append("Ngân hàng: ").append(payload.getGateway()).append("\n");
            details.append("Số tài khoản nhận: ").append(payload.getAccountNumber()).append("\n");
            details.append("Số tiền: ").append(payload.getTransferAmount()).append(" VND\n");
            details.append("Nội dung chuyển khoản: ").append(payload.getContent()).append("\n");
            details.append("Mã nhận diện (code): ").append(payload.getCode()).append("\n");
            details.append("Mã giao dịch ngân hàng (referenceCode): ").append(payload.getReferenceCode()).append("\n");
            details.append("Thời gian giao dịch: ").append(payload.getTransactionDate()).append("\n");
            details.append("Loại giao dịch: ").append(payload.getTransferType()).append("\n");
            details.append("Tên người chuyển: ").append(payload.getDescription()).append("\n");
            details.append("Số dư tích lũy: ").append(payload.getAccumulated()).append(" VND");
            
            transactionLog.setDetails(details.toString());
            transactionLog.setTimestamp(LocalDateTime.now());
            
            systemLogRepository.save(transactionLog);
            log.info("SePay Webhook: Successfully processed transaction ID: {}", payload.getId());

            // 5. Trả về đúng định dạng SePay yêu cầu
            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing SePay Webhook: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Data
    public static class SePayWebhookPayload {
        private Long id;
        private String gateway;
        private String transactionDate;
        private String accountNumber;
        private String subAccount;
        private String code;
        private String content;
        private String transferType;
        private String description;
        private Long transferAmount;
        private Long accumulated;
        private String referenceCode;
    }
}
