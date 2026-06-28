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

    public List<ConsultationRecord> getByPatient(PatientProfile patient) {
        return consultationRepository.findByPatientOrderByVisitDateDesc(patient);
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
        prediction.setIsAlertSent(false);
        aiRiskRepository.save(prediction);

        // 4. Lưu chỉ số lâm sàng vào Heart_Clinical_Metrics
        try {
            boolean fastSugar = "1".equals(aiRequest.getFbs()) || "true".equalsIgnoreCase(aiRequest.getFbs());
            boolean exAng = "1".equals(aiRequest.getExang()) || "true".equalsIgnoreCase(aiRequest.getExang());

            Integer cpValue = null;
            if (aiRequest.getCp() != null) {
                switch (aiRequest.getCp()) {
                    case "typical angina" -> cpValue = 1;
                    case "atypical angina" -> cpValue = 2;
                    case "non-anginal" -> cpValue = 3;
                    case "asymptomatic" -> cpValue = 4;
                }
            }
            Integer restecgValue = null;
            if (aiRequest.getRestecg() != null) {
                switch (aiRequest.getRestecg()) {
                    case "normal" -> restecgValue = 0;
                    case "st-t abnormality" -> restecgValue = 1;
                    case "lv hypertrophy" -> restecgValue = 2;
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

    @Transactional
    public AIRiskPrediction saveRecordAfterPrediction(
            PatientProfile patient,
            DoctorProfile doctor,
            String doctorNotes,
            String treatmentPlan,
            BigDecimal riskScore,
            String riskLevel,
            String riskExplanation,
            AIRequest aiRequest) {

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
        prediction.setIsAlertSent(false);
        aiRiskRepository.save(prediction);

        // 3. Lưu chỉ số lâm sàng vào Heart_Clinical_Metrics
        try {
            boolean fastSugar = "1".equals(aiRequest.getFbs()) || "true".equalsIgnoreCase(aiRequest.getFbs());
            boolean exAng = "1".equals(aiRequest.getExang()) || "true".equalsIgnoreCase(aiRequest.getExang());

            Integer cpValue = null;
            if (aiRequest.getCp() != null) {
                switch (aiRequest.getCp()) {
                    case "typical angina" -> cpValue = 1;
                    case "atypical angina" -> cpValue = 2;
                    case "non-anginal" -> cpValue = 3;
                    case "asymptomatic" -> cpValue = 4;
                }
            }
            Integer restecgValue = null;
            if (aiRequest.getRestecg() != null) {
                switch (aiRequest.getRestecg()) {
                    case "normal" -> restecgValue = 0;
                    case "st-t abnormality" -> restecgValue = 1;
                    case "lv hypertrophy" -> restecgValue = 2;
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
            metrics.setSex("Nam".equalsIgnoreCase(record.getPatient().getGender()) || "Male".equalsIgnoreCase(record.getPatient().getGender()) ? "Male" : "Female");
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

    // ── ICD: Search autocomplete ─────────────────────────────────────
    public List<IcdCatalog> searchIcd(String keyword) {
        return icdCatalogRepository.searchByKeyword(keyword);
    }

    // ── ICD: Lấy thông tin một ICD ──────────────────────────────────
    public java.util.Optional<IcdCatalog> findIcd(String code) {
        return icdCatalogRepository.findById(code);
    }
}