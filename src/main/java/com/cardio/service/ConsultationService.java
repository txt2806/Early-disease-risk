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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public List<ConsultationRecord> getByDoctor(DoctorProfile doctor) {
        return consultationRepository.findByDoctorOrderByVisitDateDesc(doctor);
    }

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
        record.setDoctor(doctor);
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
            int fastSugar = "1".equals(aiRequest.getFbs()) || "true".equalsIgnoreCase(aiRequest.getFbs()) ? 1 : 0;
            int exAng = "1".equals(aiRequest.getExang()) || "true".equalsIgnoreCase(aiRequest.getExang()) ? 1 : 0;
            
            jdbcTemplate.update(
                "INSERT INTO Heart_Clinical_Metrics (RecordID, ChestPainType, RestingBP, Cholesterol, FastingBloodSugar, RestingECG, MaxHeartRate, ExerciseAngina, RecordedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, GETDATE())",
                record.getRecordId(),
                aiRequest.getCp() != null && !aiRequest.getCp().isBlank() ? Integer.parseInt(aiRequest.getCp()) : null,
                aiRequest.getTrestbps() != null ? aiRequest.getTrestbps().intValue() : null,
                aiRequest.getChol() != null ? aiRequest.getChol().intValue() : null,
                fastSugar,
                aiRequest.getRestecg() != null && !aiRequest.getRestecg().isBlank() ? Integer.parseInt(aiRequest.getRestecg()) : null,
                aiRequest.getThalch() != null ? aiRequest.getThalch().intValue() : null,
                exAng
            );
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
