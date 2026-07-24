package com.cardio.controller;

import com.cardio.dto.ChatRequest;
import com.cardio.dto.ChatResponse;
import com.cardio.model.AIRiskPrediction;
import com.cardio.model.ConsultationRecord;
import com.cardio.model.PatientProfile;
import com.cardio.model.StaffProfile;
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

@Controller
@RequestMapping("/staff/chatbot")
@RequiredArgsConstructor
@Slf4j
public class StaffChatController {

    private final ChatService chatService;
    private final StaffRepository staffRepository;
    private final ConsultationService consultationService;
    private final PatientService patientService;
    private final ObjectMapper objectMapper;

    private StaffProfile getCurrentStaff(UserDetails userDetails) {
        String username = userDetails.getUsername();
        return staffRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Staff profile not found for username: " + username));
    }

    @GetMapping
    public String chatbotPage(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer patientId,
            @RequestParam(required = false) Integer recordId,
            Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        model.addAttribute("staff", staff);

        // Hỗ trợ gắn kèm context 1 lần khám khi điều dưỡng bấm "Hỏi AI" từ hồ sơ bệnh nhân
        if (patientId != null && recordId != null) {
            consultationService.getRecordById(recordId).ifPresent(record -> {
                if (record.getPatient() != null && record.getPatient().getPatientId().equals(patientId)) {
                    model.addAttribute("contextPatient", record.getPatient());
                    try {
                        String predictContextJson = objectMapper.writeValueAsString(buildPredictContext(record));
                        model.addAttribute("contextPredictContextJson", predictContextJson);
                    } catch (Exception e) {
                        log.warn("Không serialize được predict_context cho chatbot điều dưỡng: {}", e.getMessage());
                    }
                }
            });
        }

        return "staff/chatbot";
    }

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
            ctx.put("top_factors", prediction.getTopFactorsJson());
            ctx.put("trend_info", prediction.getTrendInfoJson());
        }

        // Bổ sung các chỉ số sinh tồn thực tế để chatbot điều dưỡng có thể phân tích trực tiếp
        com.cardio.model.HeartClinicalMetrics metrics = record.getClinicalMetrics();
        if (metrics == null) {
            // Thử lấy từ repo của service nếu rỗng
            metrics = consultationService.getMetricsByRecord(record.getRecordId()).orElse(null);
        }
        if (metrics != null) {
            ctx.put("tuổi", metrics.getAge());
            ctx.put("giới_tính", "Male".equalsIgnoreCase(metrics.getSex()) || "Nam".equalsIgnoreCase(metrics.getSex()) ? "Nam" : "Nữ");
            ctx.put("huyết_áp_lúc_nghỉ", metrics.getRestingBP() != null ? metrics.getRestingBP() + " mmHg" : "chưa ghi nhận");
            ctx.put("cholesterol", metrics.getCholesterol() != null ? metrics.getCholesterol() + " mg/dl" : "chưa ghi nhận");
            ctx.put("đường_huyết_lúc_đói", metrics.getFastingBloodSugar() != null ? (metrics.getFastingBloodSugar() ? "> 120 mg/dl" : "<= 120 mg/dl") : "chưa ghi nhận");
            ctx.put("nhịp_tim_tối_đa", metrics.getMaxHeartRate() != null ? metrics.getMaxHeartRate() + " bpm" : "chưa ghi nhận");
            ctx.put("nhiệt_độ", metrics.getTemperature() != null ? metrics.getTemperature() + " °C" : "chưa ghi nhận");
            ctx.put("spo2", metrics.getSpO2() != null ? metrics.getSpO2() + " %" : "chưa ghi nhận");
            
            // Các chỉ số khác
            String ecgStr = "Bình thường";
            if (metrics.getRestingECG() != null) {
                if (metrics.getRestingECG() == 1) ecgStr = "Bất thường ST-T";
                else if (metrics.getRestingECG() == 2) ecgStr = "Phì đại thất trái";
            }
            ctx.put("kết_quả_ecg", ecgStr);

            String cpStr = "Không triệu chứng";
            if (metrics.getChestPainType() != null) {
                if (metrics.getChestPainType() == 1) cpStr = "Điển hình";
                else if (metrics.getChestPainType() == 2) cpStr = "Không điển hình";
                else if (metrics.getChestPainType() == 3) cpStr = "Không đau ngực";
            }
            ctx.put("loại_đau_ngực", cpStr);
            ctx.put("đau_ngực_gắng_sức", metrics.getExerciseAngina() != null && metrics.getExerciseAngina() ? "Có" : "Không");
        }
        return ctx;
    }

    @PostMapping("/send")
    @ResponseBody
    public ChatResponse sendMessage(@RequestBody ChatRequest request) {
        return chatService.sendMessage(request, "staff");
    }
}
