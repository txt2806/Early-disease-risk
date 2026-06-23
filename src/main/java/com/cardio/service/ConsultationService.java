package com.cardio.service;

import com.cardio.model.*;
import com.cardio.repository.*;
import com.cardio.repository.IcdCatalogRepository;
import com.cardio.repository.RecordIcdRepository;
import com.cardio.model.IcdCatalog;
import com.cardio.model.RecordIcd;
import com.cardio.model.RecordIcdKey;
import com.cardio.dto.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

// ── SERVICE tầng nghiệp vụ hồ sơ & AI ───────────────
@Service
@Slf4j
@RequiredArgsConstructor
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final AIRiskRepository aiRiskRepository;
    private final AIService aiService;
    private final HeartClinicalMetricsRepository heartClinicalMetricsRepository;
    private final IcdCatalogRepository icdCatalogRepository;
    private final RecordIcdRepository recordIcdRepository;
    private final PatientAlertThresholdRepository thresholdRepository;
    private final ObjectMapper objectMapper; // Spring tự cấu hình sẵn bean này (Jackson)

    public List<ConsultationRecord> getByPatient(PatientProfile patient) {
        return consultationRepository.findByPatientOrderByVisitDateDesc(patient);
    }
    public List<ConsultationRecord> getByPatientChronological(PatientProfile patient) {
    List<ConsultationRecord> records = consultationRepository.findByPatientOrderByVisitDateDesc(patient);
    java.util.Collections.reverse(records);
    return records;
}

    @Transactional
    public AIRiskPrediction createRecordWithAI(
            PatientProfile patient,
            DoctorProfile doctor,
            String notes,
            String treatmentPlan,
            AIRequest aiRequest) {

        // 1. Lưu hồ sơ khám
        ConsultationRecord record = new ConsultationRecord();
        record.setPatient(patient);
        record.setDoctor(doctor != null && doctor.getDoctorId() != null ? doctor : null);
        record.setVisitDate(LocalDateTime.now());
        record.setConsultationNotes(notes);
        record.setTreatmentPlan(treatmentPlan);
        record.setStatus("Completed");
        consultationRepository.save(record);

        // 2. Gọi AI
        AIResponse aiResponse = aiService.predict(aiRequest);

        // 3. Lưu kết quả AI
        AIRiskPrediction prediction = new AIRiskPrediction();
        prediction.setRecord(record);
        prediction.setRiskScore(BigDecimal.valueOf(aiResponse.getProbability() * 100));
        prediction.setRiskLevel(aiResponse.getRisk_level());
        prediction.setRiskExplanation(aiResponse.getMessage());
        // [A.4] Lưu lại giải thích SHAP + xu hướng để xem được khi mở hồ sơ cũ sau này
        prediction.setTopFactorsJson(serializeTopFactors(aiResponse));
        prediction.setTrendInfoJson(serializeTrendInfo(aiResponse));
        prediction.setIsAlertSent(false);
        aiRiskRepository.save(prediction);

        // 4. Lưu chỉ số lâm sàng vào Heart_Clinical_Metrics
        try {
            boolean fastSugar = parseFbsExangFlag(aiRequest.getFbs());
            boolean exAng = parseFbsExangFlag(aiRequest.getExang());

            Integer cpValue = null;
            if (aiRequest.getCp() != null) {
                switch (aiRequest.getCp()) {
                    case "typical angina" ->
                        cpValue = 1;
                    case "atypical angina" ->
                        cpValue = 2;
                    case "non-anginal" ->
                        cpValue = 3;
                    case "asymptomatic" ->
                        cpValue = 4;
                }
            }
            Integer restecgValue = null;
            if (aiRequest.getRestecg() != null) {
                switch (aiRequest.getRestecg()) {
                    case "normal" ->
                        restecgValue = 0;
                    case "st-t abnormality" ->
                        restecgValue = 1;
                    case "lv hypertrophy" ->
                        restecgValue = 2;
                }
            }

            HeartClinicalMetrics metrics = new HeartClinicalMetrics();
            metrics.setRecord(record);
            metrics.setChestPainType(cpValue);
            metrics.setRestingBP(aiRequest.getTrestbps() != null ? aiRequest.getTrestbps().intValue() : null);
            metrics.setCholesterol(aiRequest.getChol() != null ? aiRequest.getChol().intValue() : null);
            metrics.setFastingBloodSugar(fastSugar);
            metrics.setRestingECG(restecgValue);
            metrics.setMaxHeartRate(aiRequest.getThalch() != null ? aiRequest.getThalch().intValue() : null);
            metrics.setExerciseAngina(exAng);
            metrics.setRecordedAt(LocalDateTime.now());

            metrics.setOldpeak(aiRequest.getOldpeak());
            metrics.setSlope(aiRequest.getSlope());
            metrics.setCa(aiRequest.getCa() != null ? aiRequest.getCa().intValue() : null);
            metrics.setThal(aiRequest.getThal());
            metrics.setAge(aiRequest.getAge() != null ? aiRequest.getAge().intValue() : null);
            metrics.setSex(aiRequest.getSex());

            heartClinicalMetricsRepository.save(metrics);
        } catch (Exception e) {
            log.warn("Failed to save Heart_Clinical_Metrics: " + e.getMessage());
        }

        return prediction;
    }

    /**
     * Chuẩn hoá việc parse cờ true/false cho fbs/exang.
     * Giá trị CHUẨN từ form/AIRequest là "1.0"/"0.0" (khớp đúng LabelEncoder
     * đã train trong heart_model_tuned.pkl). Vẫn chấp nhận "1"/"true"/"TRUE"
     * để tương thích ngược với dữ liệu cũ hoặc nguồn gọi khác, tránh vỡ luồng
     * nếu còn nơi nào chưa kịp cập nhật.
     */
    private boolean parseFbsExangFlag(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim();
        return "1.0".equals(v) || "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    /**
     * [A.4] Chuyển danh sách top_factors (SHAP) từ AIResponse thành JSON
     * string để lưu vào cột TopFactorsJson. Trả về null nếu rỗng/null thay
     * vì chuỗi "null" hay "[]" — để patient-detail.html dễ kiểm tra th:if.
     */
    private String serializeTopFactors(AIResponse aiResponse) {
        if (aiResponse == null || aiResponse.getTop_factors() == null || aiResponse.getTop_factors().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(aiResponse.getTop_factors());
        } catch (Exception e) {
            log.warn("Không serialize được top_factors: {}", e.getMessage());
            return null;
        }
    }

    /**
     * [A.4] Gộp trend + trend_message + history thành 1 JSON object,
     * vì 3 trường này luôn được đọc/ghi cùng nhau cho 1 lần khám cụ thể.
     */
    private String serializeTrendInfo(AIResponse aiResponse) {
        if (aiResponse == null || aiResponse.getTrend() == null || "UNKNOWN".equals(aiResponse.getTrend())) {
            return null;
        }
        try {
            Map<String, Object> trendInfo = Map.of(
                    "trend", aiResponse.getTrend(),
                    "trend_message", aiResponse.getTrend_message() != null ? aiResponse.getTrend_message() : "",
                    "history", aiResponse.getHistory() != null ? aiResponse.getHistory() : List.of()
            );
            return objectMapper.writeValueAsString(trendInfo);
        } catch (Exception e) {
            log.warn("Không serialize được trend info: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public AIRiskPrediction saveRecordAfterPrediction(
            PatientProfile patient,
            DoctorProfile doctor,
            String doctorNotes,
            String treatmentPlan,
            BigDecimal riskScore,
            String riskLevel,
            String riskExplanation,
            AIRequest aiRequest,
            String topFactorsJson,
            String trendInfoJson) {

        // 1. Lưu hồ sơ khám
        ConsultationRecord record = new ConsultationRecord();
        record.setPatient(patient);
        record.setDoctor(doctor != null && doctor.getDoctorId() != null ? doctor : null);
        record.setVisitDate(LocalDateTime.now());
        record.setConsultationNotes(doctorNotes);
        record.setTreatmentPlan(treatmentPlan);
        record.setStatus("Completed");
        consultationRepository.save(record);

        // 2. Lưu kết quả AI (đã phân tích trước đó)
        AIRiskPrediction prediction = new AIRiskPrediction();
        prediction.setRecord(record);
        prediction.setRiskScore(riskScore);
        prediction.setRiskLevel(riskLevel);
        prediction.setRiskExplanation(riskExplanation);
        // [A.4] Lưu lại giải thích SHAP + xu hướng (đã serialize sẵn từ Controller,
        // vì lúc submit form này không còn truy cập được object AIResponse gốc).
        prediction.setTopFactorsJson(topFactorsJson);
        prediction.setTrendInfoJson(trendInfoJson);
        prediction.setIsAlertSent(false);
        aiRiskRepository.save(prediction);

        // 3. Lưu chỉ số lâm sàng vào Heart_Clinical_Metrics
        try {
            boolean fastSugar = parseFbsExangFlag(aiRequest.getFbs());
            boolean exAng = parseFbsExangFlag(aiRequest.getExang());

            Integer cpValue = null;
            if (aiRequest.getCp() != null) {
                switch (aiRequest.getCp()) {
                    case "typical angina" ->
                        cpValue = 1;
                    case "atypical angina" ->
                        cpValue = 2;
                    case "non-anginal" ->
                        cpValue = 3;
                    case "asymptomatic" ->
                        cpValue = 4;
                }
            }
            Integer restecgValue = null;
            if (aiRequest.getRestecg() != null) {
                switch (aiRequest.getRestecg()) {
                    case "normal" ->
                        restecgValue = 0;
                    case "st-t abnormality" ->
                        restecgValue = 1;
                    case "lv hypertrophy" ->
                        restecgValue = 2;
                }
            }

            HeartClinicalMetrics metrics = new HeartClinicalMetrics();
            metrics.setRecord(record);
            metrics.setChestPainType(cpValue);
            metrics.setRestingBP(aiRequest.getTrestbps() != null ? aiRequest.getTrestbps().intValue() : null);
            metrics.setCholesterol(aiRequest.getChol() != null ? aiRequest.getChol().intValue() : null);
            metrics.setFastingBloodSugar(fastSugar);
            metrics.setRestingECG(restecgValue);
            metrics.setMaxHeartRate(aiRequest.getThalch() != null ? aiRequest.getThalch().intValue() : null);
            metrics.setExerciseAngina(exAng);
            metrics.setRecordedAt(LocalDateTime.now());

            metrics.setOldpeak(aiRequest.getOldpeak());
            metrics.setSlope(aiRequest.getSlope());
            metrics.setCa(aiRequest.getCa() != null ? aiRequest.getCa().intValue() : null);
            metrics.setThal(aiRequest.getThal());
            metrics.setAge(aiRequest.getAge() != null ? aiRequest.getAge().intValue() : null);
            metrics.setSex(aiRequest.getSex());

            heartClinicalMetricsRepository.save(metrics);
        } catch (Exception e) {
            log.warn("Failed to save Heart_Clinical_Metrics: " + e.getMessage());
        }

        return prediction;
    }

    public List<AIRiskPrediction> getAlertsByDoctor(Integer doctorId) {
        return aiRiskRepository.findByDoctorId(doctorId);
    }

    public List<AIRiskPrediction> getUnhandledHighAlerts() {
        return aiRiskRepository.findUnhandledHighAlerts();
    }

    // ── UC03: Cập nhật trạng thái cảnh báo ───────────────
    @Transactional
    public void updateAlertStatus(Integer predictionId, String action, String reason) {
        aiRiskRepository.findById(predictionId).ifPresent(prediction -> {
            prediction.setIsAlertSent(true);
            if (reason != null && !reason.isBlank()) {
                String note = "[" + action.toUpperCase() + "] " + reason;
                prediction.setHealthAdvice(note);
            }
            aiRiskRepository.save(prediction);
        });
    }

    // ── UC04: Cập nhật ngưỡng cảnh báo bác sĩ ────────────
    @Transactional
    public void updateDoctorThresholds(DoctorProfile doctor) {
        // Lưu ngưỡng mới vào DB
        // DoctorProfile đã có DoctorID nên save() sẽ UPDATE
    }

    // ── UC02: Lấy record theo ID ─────────────────────────────────
    public java.util.Optional<ConsultationRecord> getRecordById(Integer recordId) {
        return consultationRepository.findById(recordId);
    }

    // ── UC02: Cập nhật hồ sơ khám (BR03 - audit log ở controller) ─
    @Transactional
    public ConsultationRecord updateRecord(Integer recordId,
            String newNotes,
            String newTreatmentPlan) {
        // BR04: Không xóa dữ liệu cũ - chỉ UPDATE, không DELETE
        ConsultationRecord record = consultationRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Record not found: " + recordId));
        record.setConsultationNotes(newNotes);
        record.setTreatmentPlan(newTreatmentPlan);
        return consultationRepository.save(record);
    }

    // ── UC02: Thêm chẩn đoán ICD-10 (BR06 - mỗi lần khám ≥1 ICD) ──
    @Transactional
    public RecordIcd addDiagnosis(Integer recordId, String icdCode, String notes) {
        ConsultationRecord record = consultationRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Record not found: " + recordId));
        IcdCatalog icd = icdCatalogRepository.findById(icdCode)
                .orElseThrow(() -> new RuntimeException("ICD code not found: " + icdCode));

        // BR07: Nếu đã có ICD này thì không thêm trùng
        RecordIcdKey key = new RecordIcdKey();
        key.setRecordId(recordId);
        key.setIcdCode(icdCode);
        if (recordIcdRepository.existsById(key)) {
            throw new RuntimeException("Chẩn đoán ICD " + icdCode + " đã tồn tại trong hồ sơ này!");
        }

        RecordIcd recordIcd = new RecordIcd();
        recordIcd.setId(key);
        recordIcd.setRecord(record);
        recordIcd.setIcdCatalog(icd);
        recordIcd.setNotes(notes);
        return recordIcdRepository.save(recordIcd);
    }

    // ── ICD: Lấy danh sách chẩn đoán của một lần khám ──────────────
    public List<RecordIcd> getDiagnosesByRecord(Integer recordId) {
        return recordIcdRepository.findByRecordRecordId(recordId);
    }

    // ── ICD: Search autocomplete ─────────────────────────────────────
    public List<IcdCatalog> searchIcd(String keyword) {
        return icdCatalogRepository.searchByKeyword(keyword);
    }

    // ── ICD: Lấy thông tin một ICD ──────────────────────────────────
    public java.util.Optional<IcdCatalog> findIcd(String code) {
        return icdCatalogRepository.findById(code);
    }

    public double calcUrgencyScore(AIRiskPrediction p) {
        if (p.getIsAlertSent()) {
            return 0; // Đã xử lý → ưu tiên = 0
        }
        double base = p.getRiskScore().doubleValue();
        long daysUnhandled = 0;

        if (p.getRecord() != null && p.getRecord().getVisitDate() != null) {
            daysUnhandled = java.time.temporal.ChronoUnit.DAYS.between(
                    p.getRecord().getVisitDate().toLocalDate(),
                    java.time.LocalDate.now()
            );
        }

        double bonus = Math.min(daysUnhandled * 2, 30); // tối đa +30 điểm
        return base + bonus;
    }

    /**
     * Sắp xếp alerts: chưa xử lý lên trên, ưu tiên theo urgencyScore giảm dần
     */
    public List<AIRiskPrediction> getAlertsByDoctorSorted(Integer doctorId) {
        List<AIRiskPrediction> all = aiRiskRepository.findByDoctorId(doctorId);
        all.sort((a, b) -> {
            // Chưa xử lý lên trước
            if (!a.getIsAlertSent() && b.getIsAlertSent()) {
                return -1;
            }
            if (a.getIsAlertSent() && !b.getIsAlertSent()) {
                return 1;
            }
            // Cùng trạng thái → sort theo urgencyScore
            return Double.compare(calcUrgencyScore(b), calcUrgencyScore(a));
        });
        return all;
    }
    /**
     * [A.5] Kiểm tra prediction này có VƯỢT ngưỡng cảnh báo RIÊNG (cá nhân
     * hoá theo từng cặp bệnh nhân-bác sĩ, cấu hình ở thresholds.html) hay
     * không. KHÔNG dùng để ẩn/lọc bớt alerts — mọi prediction vẫn hiển thị
     * đầy đủ trong alerts.html (an toàn, tránh bỏ sót), hàm này chỉ dùng để
     * đánh dấu/làm nổi bật riêng những ca đáng chú ý hơn theo từng bệnh nhân.
     *
     * Trước A.5: hàm cũ tên là checkAndCreateAlert(), chỉ làm đúng 1 việc là
     * setIsAlertSent(false) — vốn đã là giá trị mặc định lúc khởi tạo, nên
     * không hề có tác dụng gì dù được gọi hay không. PatientAlertThreshold
     * (UC04) chưa từng thực sự ảnh hưởng tới alerts.html trước khi sửa.
     */
    public boolean exceedsPersonalThreshold(AIRiskPrediction prediction) {
        if (prediction.getRecord() == null) {
            return false;
        }
        PatientProfile patient = prediction.getRecord().getPatient();
        DoctorProfile doctor = prediction.getRecord().getDoctor();
        if (patient == null || doctor == null) {
            return false;
        }

        PatientAlertThreshold threshold = thresholdRepository
                .findByPatientAndDoctor(patient, doctor)
                .orElse(null);
        // Bác sĩ chưa cấu hình ngưỡng riêng cho bệnh nhân này → không có gì
        // để so sánh, không đánh dấu nổi bật (khác với ngưỡng mặc định 40%
        // hard-code, vì đó là quyết định CÓ CHỦ Ý của bác sĩ, không phải
        // suy đoán thay họ).
        if (threshold == null || prediction.getRiskScore() == null) {
            return false;
        }

        double riskScore = prediction.getRiskScore().doubleValue();
        return riskScore >= threshold.getRiskScoreThreshold();
    }
}