package com.cardio.controller;

import com.cardio.dto.ChatRequest;
import com.cardio.dto.ChatResponse;
import com.cardio.model.AIRiskPrediction;
import com.cardio.model.Appointment;
import com.cardio.model.ConsultationRecord;
import com.cardio.model.DoctorProfile;
import com.cardio.model.PatientProfile;
import com.cardio.model.StaffProfile;
import com.cardio.repository.AppointmentRepository;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
//
// [FIX] Thêm buildDoctorContext(): số liệu tổng quan phòng khám (số bệnh
// nhân phụ trách, số cảnh báo theo mức độ, lịch hẹn hôm nay) — để chatbot
// trả lời được các câu hỏi dạng "tôi đang phụ trách bao nhiêu bệnh nhân"
// bằng dữ liệu THẬT thay vì phải từ chối trả lời (trước đây luôn nói
// "không có quyền truy cập" dù dữ liệu có sẵn trong hệ thống, chỉ là
// chưa được gửi kèm cho chatbot).
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
    private final AppointmentRepository appointmentRepository;
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

        // [FIX] Username thật (không phải fullName) để dùng làm key lưu
        // lịch sử hội thoại phía trình duyệt (sessionStorage) — tránh 2 tài
        // khoản khác nhau dùng chung máy/tab nhìn thấy lịch sử của nhau.
        model.addAttribute("username", userDetails.getUsername());

        // [FIX] Số liệu tổng quan phòng khám — tính 1 lần khi vào trang,
        // gửi kèm MỌI tin nhắn trong phiên chat này (giống cách predict_context
        // hoạt động), để chatbot trả lời được câu hỏi tổng quan bằng số thật.
        try {
            Map<String, Object> doctorContext = buildDoctorContext(doctor);
            model.addAttribute("doctorContextJson", objectMapper.writeValueAsString(doctorContext));
        } catch (Exception e) {
            log.warn("Không serialize được doctor_context cho chatbot: {}", e.getMessage());
        }

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
     * [FIX] Tính số liệu tổng quan phòng khám của bác sĩ đang đăng nhập —
     * tái dùng ĐÚNG logic đã có ở DoctorController.dashboard() (không viết
     * lại công thức tính alert/patient từ đầu, tránh 2 nơi tính ra 2 kết
     * quả khác nhau cho cùng 1 khái niệm "số cảnh báo HIGH").
     */
    private Map<String, Object> buildDoctorContext(DoctorProfile doctor) {
        Map<String, Object> ctx = new HashMap<>();

        List<PatientProfile> patients;
        List<AIRiskPrediction> alerts;
        try {
            if (doctor.getDoctorId() != null) {
                patients = patientService.getPatientsAssignedToDoctor(doctor.getDoctorId());
                alerts = consultationService.getAlertsByDoctor(doctor.getDoctorId());
            } else {
                patients = patientService.getAllPatients();
                alerts = consultationService.getUnhandledHighAlerts();
            }
        } catch (Exception ex) {
            log.warn("Lỗi khi tính doctor_context: {}", ex.getMessage());
            patients = List.of();
            alerts = List.of();
        }

        long highAlerts = alerts.stream()
                .filter(a -> a != null && "HIGH".equals(a.getRiskLevel()) && !a.getIsAlertSent())
                .count();
        long mediumAlerts = alerts.stream()
                .filter(a -> a != null && "MEDIUM".equals(a.getRiskLevel()) && !a.getIsAlertSent())
                .count();
        long lowAlerts = alerts.stream()
                .filter(a -> a != null && "LOW".equals(a.getRiskLevel()) && !a.getIsAlertSent())
                .count();
        long unhandledTotal = alerts.stream()
                .filter(a -> a != null && !a.getIsAlertSent())
                .count();

        long todayAppointments = 0;
        try {
            if (doctor.getDoctorId() != null) {
                LocalDate today = LocalDate.now();
                todayAppointments = appointmentRepository
                        .findByDoctorOrderByScheduledDateDescTimeSlotDesc(doctor).stream()
                        .filter(a -> a.getScheduledDate() != null && a.getScheduledDate().equals(today))
                        .count();
            }
        } catch (Exception ex) {
            log.warn("Lỗi khi đếm lịch hẹn hôm nay cho doctor_context: {}", ex.getMessage());
        }

        ctx.put("doctor_name", doctor.getFullName());
        ctx.put("total_patients", patients.size());
        ctx.put("high_alerts_unhandled", highAlerts);
        ctx.put("medium_alerts_unhandled", mediumAlerts);
        ctx.put("low_alerts_unhandled", lowAlerts);
        ctx.put("total_alerts_unhandled", unhandledTotal);
        ctx.put("today_appointments_count", todayAppointments);
        ctx.put("today_date", LocalDate.now().toString());
        return ctx;
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

        // Bổ sung các chỉ số sinh tồn thực tế để chatbot bác sĩ có thể phân tích trực tiếp
        com.cardio.model.HeartClinicalMetrics metrics = record.getClinicalMetrics();
        if (metrics == null) {
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