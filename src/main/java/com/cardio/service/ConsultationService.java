package com.cardio.service;

import com.cardio.model.*;
import com.cardio.repository.*;
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
}
