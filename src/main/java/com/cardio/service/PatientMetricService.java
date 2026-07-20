package com.cardio.service;

import com.cardio.dto.MedicalMetricsDTO;
import com.cardio.model.AIRiskPrediction;
import com.cardio.model.ConsultationRecord;
import com.cardio.model.HeartClinicalMetrics;
import com.cardio.model.PatientProfile;
import com.cardio.repository.AIRiskRepository;
import com.cardio.repository.ConsultationRepository;
import com.cardio.repository.HeartClinicalMetricsRepository;
import com.cardio.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientMetricService {

    private final PatientRepository patientRepository;
    private final ConsultationRepository consultationRepository;
    private final HeartClinicalMetricsRepository heartClinicalMetricsRepository;
    private final AIRiskRepository aiRiskRepository;
    private final AIService aiService;

    @Transactional
    public Map<String, Object> saveMetricsAndPredict(MedicalMetricsDTO dto) {
        // 1. Find patient
        Integer patientId = Integer.parseInt(dto.getPatientId());
        PatientProfile patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bệnh nhân với ID: " + patientId));

        // 2. Update gender if provided and different
        if (dto.getGender() != null && (patient.getGender() == null || !dto.getGender().equalsIgnoreCase(patient.getGender()))) {
            patient.setGender(dto.getGender());
            patientRepository.save(patient);
        }

        // 3. Create ConsultationRecord for this self-report
        ConsultationRecord record = new ConsultationRecord();
        record.setPatient(patient);
        record.setDoctor(null); // Self-reported, no doctor assigned
        record.setVisitDate(LocalDateTime.now());
        record.setConsultationNotes("Bệnh nhân tự khai báo chỉ số qua ứng dụng di động.");
        record.setStatus("SELF_REPORTED"); // Use a specific status for self-reports
        consultationRepository.save(record);

        // 4. Create HeartClinicalMetrics from DTO
        HeartClinicalMetrics metrics = new HeartClinicalMetrics();
        metrics.setRecord(record);
        metrics.setAge(dto.getAge());
        metrics.setSex(patient.getGender()); // Use the gender from profile for consistency
        metrics.setChestPainType(dto.getChestPainType());
        metrics.setRestingBP(dto.getRestingBloodPressure());
        metrics.setCholesterol(dto.getCholesterol());
        metrics.setMaxHeartRate(dto.getMaxHeartRate());
        metrics.setRecordedAt(LocalDateTime.now());
        heartClinicalMetricsRepository.save(metrics);

        // 5. Call FastAPI for prediction
        Map<String, Object> aiPredictResult = aiService.callFastApiPredict(dto, patient.getGender());

        // 6. Process AI result, get advice, and handle alerts
        String riskTier = (String) aiPredictResult.getOrDefault("risk_tier", "UNKNOWN");
        String severity = (String) aiPredictResult.getOrDefault("severity", "UNKNOWN");
        String riskLevel = aiService.mapRiskTierToVietnamese(riskTier);
        Object emergencyWarning = aiPredictResult.get("emergency_warning");

        boolean alertTriggered = emergencyWarning != null || dto.getRestingBloodPressure() > 140;
        if (alertTriggered && (patient.getIsAlert() == null || patient.getIsAlert() == 0)) {
            patient.setIsAlert(1);
            patientRepository.save(patient);
            log.info("Alert triggered for patient {} due to high-risk metrics.", patient.getPatientId());
        }

        Map<String, String> adviceResult = aiService.getGeminiAdviceWithFallback(dto, riskLevel, severity, aiPredictResult);

        // 7. Save AI Prediction result to database
        AIRiskPrediction prediction = new AIRiskPrediction();
        prediction.setRecord(record);
        prediction.setRiskLevel(riskLevel);
        prediction.setRiskExplanation((String) aiPredictResult.get("disclaimer"));
        prediction.setHealthAdvice(adviceResult.get("health_advice"));
        prediction.setDietaryAdvice(adviceResult.get("nutrition_advice")); // Map nutrition to dietary
        aiRiskRepository.save(prediction);

        // 8. Build response map for the controller
        aiPredictResult.put("risk_level", riskLevel);
        aiPredictResult.putAll(adviceResult);
        return aiPredictResult;
    }
}