package com.cardio.controller;

import com.cardio.dto.ChatRequest;
import com.cardio.dto.ChatResponse;
import com.cardio.model.AIRiskPrediction;
import com.cardio.model.ConsultationRecord;
import com.cardio.model.DoctorProfile;
import com.cardio.model.PatientProfile;
import com.cardio.model.StaffProfile;
import com.cardio.repository.DoctorRepository;
import com.cardio.repository.StaffRepository;
import com.cardio.service.ChatService;
import com.cardio.service.ConsultationService;
import com.cardio.service.PatientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

// ── CONTROLLER — Chatbot AI cho Bác sĩ ────────────────
// [D.21][D.22] Route RIÊNG cho bác sĩ (/doctor/chatbot), gọi /chat/doctor ở
// FastAPI. KHÔNG liên quan tới chatbot bệnh nhân (/chat/patient — đó nằm
// ở app Flutter, không có UI nào trong project Doctor Portal này).
//
// [D.22] Hỗ trợ gắn kèm context 1 lần khám cụ thể: khi bác sĩ bấm "Hỏi AI"
// từ trang chi tiết bệnh nhân (patient-detail.html), URL sẽ có
// ?patientId=X&recordId=Y — Controller tra DB lấy đúng AIRiskPrediction của
// lần khám đó, serialize ra JSON string (theo đúng pattern
// topFactorsJsonForForm đã có trong DoctorController), và đưa vào Model để
// chatbot.html gửi kèm predict_context, KHÔNG còn phải gõ tay chỉ số như trước.
@Controller
@RequestMapping("/doctor/chatbot")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final DoctorRepository doctorRepository;
    private final StaffRepository staffRepository;
    private final ConsultationService consultationService;
    private final PatientService patientService;
    private final ObjectMapper objectMapper;

    // Helper lấy doctor đang đăng nhập — giữ đúng pattern getCurrentDoctor()
    private DoctorProfile getCurrentDoctor(UserDetails userDetails) {
        String username = userDetails.getUsername();
        return doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found for username: " + username));
    }

    // ── Hiển thị trang chatbot ─────────────────────────
    @GetMapping
    public String chatbotPage(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer patientId,
            @RequestParam(required = false) Integer recordId,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        model.addAttribute("doctor", doctor);

        // [D.22] Nếu có patientId+recordId (bác sĩ bấm "Hỏi AI" từ hồ sơ bệnh
        // nhân), tra dữ liệu thật để gắn context — KHÔNG tin tưởng mù quáng
        // tham số URL: vẫn kiểm tra quyền truy cập giống DoctorController,
        // tránh bác sĩ A xem được dữ liệu AI của bệnh nhân do bác sĩ B phụ trách.
        if (patientId != null && recordId != null) {
            boolean allowed = doctor.getDoctorId() == null
                    || patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId());

            if (allowed) {
                consultationService.getRecordById(recordId).ifPresent(record -> {
                    // Đảm bảo record này đúng là của patientId trong URL, tránh
                    // truyền nhầm recordId của bệnh nhân khác (dù khác doctorId
                    // đã chặn ở allowed, vẫn nên khớp đúng patientId cho chắc).
                    if (record.getPatient() != null
                            && record.getPatient().getPatientId().equals(patientId)) {
                        model.addAttribute("contextPatient", record.getPatient());
                        try {
                            String predictContextJson = objectMapper
                                    .writeValueAsString(buildPredictContext(record));
                            model.addAttribute("contextPredictContextJson", predictContextJson);
                        } catch (Exception e) {
                            log.warn("Không serialize được predict_context cho chatbot: {}", e.getMessage());
                        }
                    }
                });
            }
        }

        return "doctor/chatbot";
    }

    /**
     * [D.22] Chuyển AIRiskPrediction của 1 record thành Map đơn giản để
     * Thymeleaf serialize ra JSON cho JS gắn vào predict_context khi gọi
     * /chat/doctor. Chỉ lấy các field có ý nghĩa giải thích — KHÔNG đưa
     * toàn bộ entity (tránh lộ thông tin không cần thiết qua HTML).
     */
    private Map<String, Object> buildPredictContext(ConsultationRecord record) {
        Map<String, Object> ctx = new HashMap<>();
        PatientProfile patient = record.getPatient();
        if (patient != null) {
            ctx.put("patient_name", patient.getFullName());
            ctx.put("patient_id", patient.getPatientId());
        }
        ctx.put("visit_date", record.getVisitDate() != null ? record.getVisitDate().toString() : null);

        AIRiskPrediction prediction = record.getAiRiskPrediction();
        if (prediction != null) {
            ctx.put("risk_level", prediction.getRiskLevel());
            ctx.put("risk_score", prediction.getRiskScore());
            ctx.put("risk_explanation", prediction.getRiskExplanation());
            // top_factors/trend đã ở dạng JSON string sẵn (topFactorsJson) —
            // để nguyên dạng string, Gemini vẫn đọc hiểu được nội dung JSON
            // lồng trong text, không cần parse lại thành object ở đây.
            ctx.put("top_factors", prediction.getTopFactorsJson());
            ctx.put("trend_info", prediction.getTrendInfoJson());
        }
        return ctx;
    }

    // ── API gửi tin nhắn (gọi bằng fetch() từ JS phía trang chatbot.html) ──
    // Trả JSON, KHÔNG redirect, để JS cập nhật khung chat ngay lập tức mà
    // không cần reload toàn trang — chatbot cần UX phản hồi nhanh, khác với
    // các trang form-submit truyền thống còn lại trong project.
    @PostMapping("/send")
    @ResponseBody
    public ChatResponse sendMessage(@RequestBody ChatRequest request) {
        return chatService.sendMessage(request);
    }
}