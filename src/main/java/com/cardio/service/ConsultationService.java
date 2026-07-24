package com.cardio.service;

import com.cardio.model.*;
import com.cardio.repository.*;
import com.cardio.repository.IcdCatalogRepository;
import com.cardio.repository.RecordIcdRepository;
import com.cardio.model.IcdCatalog;
import com.cardio.model.RecordIcd;
import com.cardio.model.RecordIcdKey;
import com.cardio.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

// ── SERVICE tầng nghiệp vụ hồ sơ & AI ───────────────
@Service
@Slf4j
@RequiredArgsConstructor
public class ConsultationService {

    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_PENDING = "Pending";
    public static final String TREND_UNKNOWN = "UNKNOWN";

    public static final String FBS_EXANG_TRUE_1 = "1.0";
    public static final String FBS_EXANG_TRUE_2 = "1";
    public static final String FBS_EXANG_TRUE_3 = "true";

    public static final String CP_TYPICAL = "typical angina";
    public static final String CP_ATYPICAL = "atypical angina";
    public static final String CP_NON_ANGINAL = "non-anginal";
    public static final String CP_ASYMPTOMATIC = "asymptomatic";

    public static final String ECG_NORMAL = "normal";
    public static final String ECG_ABNORMALITY = "st-t abnormality";
    public static final String ECG_HYPERTROPHY = "lv hypertrophy";

    public static final String GENDER_MALE_VN = "Nam";
    public static final String GENDER_MALE_EN = "Male";
    public static final String GENDER_FEMALE_EN = "Female";

    private final ConsultationRepository consultationRepository;
    private final AIRiskRepository aiRiskRepository;
    private final AIService aiService;
    private final HeartClinicalMetricsRepository heartClinicalMetricsRepository;
    private final IcdCatalogRepository icdCatalogRepository;
    private final RecordIcdRepository recordIcdRepository;
    private final PatientAlertThresholdRepository thresholdRepository;
    private final ObjectMapper objectMapper;
    private final LabRequestRepository labRequestRepository;
    private final AppointmentRepository appointmentRepository;

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
        record.setStatus(STATUS_COMPLETED);
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
                if (CP_TYPICAL.equals(aiRequest.getCp())) cpValue = 1;
                else if (CP_ATYPICAL.equals(aiRequest.getCp())) cpValue = 2;
                else if (CP_NON_ANGINAL.equals(aiRequest.getCp())) cpValue = 3;
                else if (CP_ASYMPTOMATIC.equals(aiRequest.getCp())) cpValue = 4;
            }
            Integer restecgValue = null;
            if (aiRequest.getRestecg() != null) {
                if (ECG_NORMAL.equals(aiRequest.getRestecg())) restecgValue = 0;
                else if (ECG_ABNORMALITY.equals(aiRequest.getRestecg())) restecgValue = 1;
                else if (ECG_HYPERTROPHY.equals(aiRequest.getRestecg())) restecgValue = 2;
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
        return FBS_EXANG_TRUE_1.equals(v) || FBS_EXANG_TRUE_2.equals(v) || FBS_EXANG_TRUE_3.equalsIgnoreCase(v);
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
        record.setStatus(STATUS_COMPLETED);
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
                if (CP_TYPICAL.equals(aiRequest.getCp())) cpValue = 1;
                else if (CP_ATYPICAL.equals(aiRequest.getCp())) cpValue = 2;
                else if (CP_NON_ANGINAL.equals(aiRequest.getCp())) cpValue = 3;
                else if (CP_ASYMPTOMATIC.equals(aiRequest.getCp())) cpValue = 4;
            }
            Integer restecgValue = null;
            if (aiRequest.getRestecg() != null) {
                if (ECG_NORMAL.equals(aiRequest.getRestecg())) restecgValue = 0;
                else if (ECG_ABNORMALITY.equals(aiRequest.getRestecg())) restecgValue = 1;
                else if (ECG_HYPERTROPHY.equals(aiRequest.getRestecg())) restecgValue = 2;
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
                                           String newTreatmentPlan,
                                           Integer restingBP,
                                           Integer maxHeartRate,
                                           Double temperature,
                                           Integer spO2,
                                           String bloodTest,
                                           String urineTest,
                                           String xray,
                                           String ultrasound,
                                           String mri,
                                           String ct,
                                           Integer chestPainType,
                                           Integer cholesterol,
                                           Boolean fastingBloodSugar,
                                           Integer restingECG,
                                           Boolean exerciseAngina,
                                           Double oldpeak,
                                           String slope,
                                           Integer ca,
                                           String thal) {
        // BR04: Không xóa dữ liệu cũ - chỉ UPDATE, không DELETE
        ConsultationRecord record = consultationRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Record not found: " + recordId));
        record.setConsultationNotes(newNotes);
        record.setTreatmentPlan(newTreatmentPlan);

        HeartClinicalMetrics metrics = record.getClinicalMetrics();
        if (metrics == null) {
            metrics = new HeartClinicalMetrics();
            metrics.setRecord(record);
            metrics.setRecordedAt(LocalDateTime.now());
        }

        if (metrics.getAge() == null && record.getPatient().getDob() != null) {
            metrics.setAge(java.time.Period.between(record.getPatient().getDob(), java.time.LocalDate.now()).getYears());
        }
        if (metrics.getSex() == null && record.getPatient().getGender() != null) {
            metrics.setSex(GENDER_MALE_VN.equalsIgnoreCase(record.getPatient().getGender()) || GENDER_MALE_EN.equalsIgnoreCase(record.getPatient().getGender()) ? GENDER_MALE_EN : GENDER_FEMALE_EN);
        }

        metrics.setRestingBP(restingBP);
        metrics.setMaxHeartRate(maxHeartRate);
        metrics.setTemperature(temperature);
        metrics.setSpO2(spO2);
        if (bloodTest != null && !bloodTest.isEmpty()) {
            metrics.setBloodTest(bloodTest);
        }
        if (urineTest != null && !urineTest.isEmpty()) {
            metrics.setUrineTest(urineTest);
        }
        if (xray != null && !xray.isEmpty()) {
            metrics.setXray(xray);
        }
        if (ultrasound != null && !ultrasound.isEmpty()) {
            metrics.setUltrasound(ultrasound);
        }
        if (mri != null && !mri.isEmpty()) {
            metrics.setMri(mri);
        }
        if (ct != null && !ct.isEmpty()) {
            metrics.setCt(ct);
        }
        metrics.setChestPainType(chestPainType);
        metrics.setCholesterol(cholesterol);
        metrics.setFastingBloodSugar(fastingBloodSugar);
        metrics.setRestingECG(restingECG);
        metrics.setExerciseAngina(exerciseAngina);
        metrics.setOldpeak(oldpeak);
        metrics.setSlope(slope);
        metrics.setCa(ca);
        metrics.setThal(thal);

        heartClinicalMetricsRepository.save(metrics);
        record.setClinicalMetrics(metrics);
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

    // ── ICD: Xoá một chẩn đoán khỏi hồ sơ ──────────────────────────
    @Transactional
    public void removeDiagnosis(Integer recordId, String icdCode) {
        RecordIcdKey key = new RecordIcdKey();
        key.setRecordId(recordId);
        key.setIcdCode(icdCode);
        if (!recordIcdRepository.existsById(key)) {
            throw new RuntimeException("Không tìm thấy chẩn đoán ICD " + icdCode + " trong hồ sơ này!");
        }
        recordIcdRepository.deleteById(key);
    }

    // ── HeartClinicalMetrics: Cập nhật chỉ số lâm sàng ─────────────
    @Transactional
    public void updateClinicalMetrics(Integer recordId,
            Integer restingBP, Integer cholesterol, Boolean fastingBloodSugar,
            Integer maxHeartRate, Boolean exerciseAngina, Double oldpeak,
            String slope, Integer ca, String thal) {
        List<HeartClinicalMetrics> metricsList =
                heartClinicalMetricsRepository.findByRecordRecordId(recordId);
        if (metricsList.isEmpty()) {
            return; // chưa có metrics thì bỏ qua — chỉ update khi tồn tại
        }
        HeartClinicalMetrics m = metricsList.get(0);
        if (restingBP != null)         m.setRestingBP(restingBP);
        if (cholesterol != null)       m.setCholesterol(cholesterol);
        if (fastingBloodSugar != null) m.setFastingBloodSugar(fastingBloodSugar);
        if (maxHeartRate != null)      m.setMaxHeartRate(maxHeartRate);
        if (exerciseAngina != null)    m.setExerciseAngina(exerciseAngina);
        if (oldpeak != null)           m.setOldpeak(oldpeak);
        if (slope != null && !slope.isBlank())  m.setSlope(slope);
        if (ca != null)                m.setCa(ca);
        if (thal != null && !thal.isBlank())    m.setThal(thal);
        heartClinicalMetricsRepository.save(m);
    }

    // ── HeartClinicalMetrics: Lấy metrics của một record ────────────
    public java.util.Optional<HeartClinicalMetrics> getMetricsByRecord(Integer recordId) {
        List<HeartClinicalMetrics> list =
                heartClinicalMetricsRepository.findByRecordRecordId(recordId);
        return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(list.get(0));
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
        if (p == null) {
            return 0;
        }
        if (p.getIsAlertSent() != null && p.getIsAlertSent()) {
            return 0; // Đã xử lý → ưu tiên = 0
        }
        double base = p.getRiskScore() != null ? p.getRiskScore().doubleValue() : 0.0;
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
            boolean sentA = a != null && a.getIsAlertSent() != null && a.getIsAlertSent();
            boolean sentB = b != null && b.getIsAlertSent() != null && b.getIsAlertSent();
            // Chưa xử lý lên trước
            if (!sentA && sentB) {
                return -1;
            }
            if (sentA && !sentB) {
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
    /**
     * [FIX] Kiểm tra ĐẦY ĐỦ ngưỡng cảnh báo cá nhân hoá — trước đây chỉ kiểm
     * tra riskScoreThreshold, dù maxBpm/maxSystolicBp ĐÃ được lưu và hiển thị
     * trên giao diện thresholds.html từ lâu (bác sĩ đặt ngưỡng nhịp tim/huyết
     * áp nhưng hệ thống KHÔNG BAO GIỜ dùng đến để đánh giá — lỗ hổng có sẵn,
     * không phải do phần mở rộng lần này).
     *
     * Giờ kiểm tra đủ 6 tiêu chí: nguy cơ AI, nhịp tim (min/max), huyết áp
     * tâm thu (min/max), cholesterol (max) — trả về CẢ lý do cụ thể, không
     * chỉ true/false, để bác sĩ biết ngay đang vượt ngưỡng nào.
     *
     * Logic OR: CHỈ CẦN 1 tiêu chí vượt là coi như "vượt ngưỡng" — đúng tinh
     * thần hệ thống cảnh báo an toàn (an toàn hơn khi báo động nhạy, tránh
     * bỏ sót ca bất thường dù chỉ 1 chỉ số có vấn đề).
     */
    public ThresholdCheckResult checkPersonalThreshold(AIRiskPrediction prediction) {
        ThresholdCheckResult result = ThresholdCheckResult.notExceeded();

        if (prediction.getRecord() == null) {
            return result;
        }
        PatientProfile patient = prediction.getRecord().getPatient();
        DoctorProfile doctor = prediction.getRecord().getDoctor();
        if (patient == null || doctor == null) {
            return result;
        }

        PatientAlertThreshold threshold = thresholdRepository
                .findByPatientAndDoctor(patient, doctor)
                .orElse(null);
        // Bác sĩ chưa cấu hình ngưỡng riêng cho bệnh nhân này → không có gì
        // để so sánh, không đánh dấu nổi bật (không suy đoán thay bác sĩ).
        if (threshold == null) {
            return result;
        }

        // ── 1. Nguy cơ AI ─────────────────────────────────
        if (prediction.getRiskScore() != null && threshold.getRiskScoreThreshold() != null) {
            double riskScore = prediction.getRiskScore().doubleValue();
            if (riskScore >= threshold.getRiskScoreThreshold()) {
                result.addViolation(String.format(
                        "Nguy cơ AI %.1f%% ≥ ngưỡng %.0f%%", riskScore, threshold.getRiskScoreThreshold()));
            }
        }

        // ── 2-6. Chỉ số lâm sàng (nhịp tim, huyết áp, cholesterol) ─────
        // Lấy từ HeartClinicalMetrics của ĐÚNG lần khám tạo ra prediction này
        // — không lấy chỉ số cũ/của lần khám khác, đảm bảo đánh giá đúng
        // thời điểm.
        HeartClinicalMetrics metrics = prediction.getRecord().getClinicalMetrics();
        if (metrics != null) {
            Integer heartRate = metrics.getMaxHeartRate();
            if (heartRate != null) {
                if (threshold.getMaxBpm() != null && heartRate > threshold.getMaxBpm()) {
                    result.addViolation(String.format(
                            "Nhịp tim %d bpm > ngưỡng tối đa %d bpm", heartRate, threshold.getMaxBpm()));
                }
                if (threshold.getMinBpm() != null && heartRate < threshold.getMinBpm()) {
                    result.addViolation(String.format(
                            "Nhịp tim %d bpm < ngưỡng tối thiểu %d bpm", heartRate, threshold.getMinBpm()));
                }
            }

            Integer systolicBp = metrics.getRestingBP();
            if (systolicBp != null) {
                if (threshold.getMaxSystolicBp() != null && systolicBp > threshold.getMaxSystolicBp()) {
                    result.addViolation(String.format(
                            "Huyết áp %d mmHg > ngưỡng tối đa %d mmHg", systolicBp, threshold.getMaxSystolicBp()));
                }
                if (threshold.getMinSystolicBp() != null && systolicBp < threshold.getMinSystolicBp()) {
                    result.addViolation(String.format(
                            "Huyết áp %d mmHg < ngưỡng tối thiểu %d mmHg", systolicBp, threshold.getMinSystolicBp()));
                }
            }

            Integer cholesterol = metrics.getCholesterol();
            if (cholesterol != null && threshold.getMaxCholesterol() != null
                    && cholesterol > threshold.getMaxCholesterol()) {
                result.addViolation(String.format(
                        "Cholesterol %d mg/dL > ngưỡng tối đa %d mg/dL", cholesterol, threshold.getMaxCholesterol()));
            }
        }

        return result;
    }

    /**
     * [Tương thích ngược] Giữ chữ ký boolean cũ cho bất kỳ nơi nào khác còn
     * gọi trực tiếp — nay chỉ là lớp bọc mỏng gọi checkPersonalThreshold().
     * Nơi nào cần lý do cụ thể (alerts.html) nên dùng checkPersonalThreshold()
     * trực tiếp thay vì hàm này.
     */
    public boolean exceedsPersonalThreshold(AIRiskPrediction prediction) {
        return checkPersonalThreshold(prediction).isExceeded();
    }

    @Transactional
    public Appointment createReferral(PatientProfile patient, DoctorProfile targetDoctor, String notes) {
        Appointment app = new Appointment();
        app.setPatient(patient);
        app.setDoctor(targetDoctor);
        app.setScheduledDate(java.time.LocalDate.now()); // Hẹn khám chuyên khoa hôm nay/ngay lập tức
        app.setStatus(STATUS_PENDING);
        app.setBookingType("Specialist");
        app.setPreliminaryStatus(notes);
        app.setRequestTime(LocalDateTime.now());
        return appointmentRepository.save(app);
    }

    @Transactional
    public LabRequest createLabRequest(PatientProfile patient, DoctorProfile doctor, String requestNotes) {
        LabRequest req = new LabRequest();
        req.setPatient(patient);
        req.setDoctor(doctor);
        req.setRequestNotes(requestNotes);
        req.setStatus(STATUS_PENDING);
        req.setCreatedAt(LocalDateTime.now());
        return labRequestRepository.save(req);
    }

    @Transactional
    public void completeLabRequest(Integer requestId, HeartClinicalMetrics clinicalMetrics, String resultNotes) {
        labRequestRepository.findById(requestId).ifPresent(req -> {
            req.setStatus(STATUS_COMPLETED);
            req.setCompletedAt(LocalDateTime.now());
            req.setClinicalMetrics(clinicalMetrics);
            req.setResultNotes(resultNotes);
            labRequestRepository.save(req);
        });
    }

    @Transactional
    public void runAutoAIPrediction(ConsultationRecord record, HeartClinicalMetrics vitals) {
        try {
            AIRequest aiRequest = new AIRequest();
            aiRequest.setAge(vitals.getAge() != null ? vitals.getAge().doubleValue() : 50.0);
            aiRequest.setSex(vitals.getSex() != null ? vitals.getSex() : "Male");
            
            // Map cp
            String cpStr = "asymptomatic";
            if (vitals.getChestPainType() != null) {
                switch (vitals.getChestPainType()) {
                    case 1 -> cpStr = CP_TYPICAL;
                    case 2 -> cpStr = CP_ATYPICAL;
                    case 3 -> cpStr = CP_NON_ANGINAL;
                    default -> cpStr = CP_ASYMPTOMATIC;
                }
            }
            aiRequest.setCp(cpStr);

            aiRequest.setTrestbps(vitals.getRestingBP() != null ? vitals.getRestingBP().doubleValue() : 120.0);
            aiRequest.setChol(vitals.getCholesterol() != null ? vitals.getCholesterol().doubleValue() : 200.0);
            aiRequest.setFbs(vitals.getFastingBloodSugar() != null ? (vitals.getFastingBloodSugar() ? "1.0" : "0.0") : "0.0");
            
            // Map restecg
            String restecgStr = "normal";
            if (vitals.getRestingECG() != null) {
                switch (vitals.getRestingECG()) {
                    case 0 -> restecgStr = ECG_NORMAL;
                    case 1 -> restecgStr = ECG_ABNORMALITY;
                    case 2 -> restecgStr = ECG_HYPERTROPHY;
                    default -> restecgStr = ECG_NORMAL;
                }
            }
            aiRequest.setRestecg(restecgStr);

            aiRequest.setThalch(vitals.getMaxHeartRate() != null ? vitals.getMaxHeartRate().doubleValue() : 150.0);
            aiRequest.setExang(vitals.getExerciseAngina() != null ? (vitals.getExerciseAngina() ? "1.0" : "0.0") : "0.0");
            aiRequest.setOldpeak(vitals.getOldpeak() != null ? vitals.getOldpeak() : 0.0);
            aiRequest.setSlope(vitals.getSlope() != null ? vitals.getSlope() : "flat");
            aiRequest.setCa(vitals.getCa() != null ? vitals.getCa().doubleValue() : 0.0);
            aiRequest.setThal(vitals.getThal() != null ? vitals.getThal() : "normal");

            // Chạy AI dự đoán
            AIResponse aiResponse = aiService.predict(aiRequest);

            // Lưu cảnh báo AI
            AIRiskPrediction prediction = new AIRiskPrediction();
            prediction.setRecord(record);
            prediction.setRiskScore(java.math.BigDecimal.valueOf(aiResponse.getProbability() * 100));
            prediction.setRiskLevel(aiResponse.getRisk_level());
            prediction.setRiskExplanation(aiResponse.getMessage());
            prediction.setTopFactorsJson(serializeTopFactors(aiResponse));
            prediction.setTrendInfoJson(serializeTrendInfo(aiResponse));
            prediction.setIsAlertSent(false);
            aiRiskRepository.save(prediction);

            log.info("Tự động chạy AI phân tích nguy cơ cho hồ sơ #{} thành công. Mức nguy cơ: {}, Điểm nguy cơ: {}%", 
                    record.getRecordId(), prediction.getRiskLevel(), prediction.getRiskScore());
        } catch (Exception e) {
            log.error("Lỗi khi tự động chạy AI phân tích nguy cơ: ", e);
        }
    }
}