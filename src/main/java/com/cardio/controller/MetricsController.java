package com.cardio.controller;

import com.cardio.dto.MedicalMetricsDTO;
import com.cardio.model.AIRiskPrediction;
import com.cardio.model.ConsultationRecord;
import com.cardio.model.HeartClinicalMetrics;
import com.cardio.repository.ConsultationRepository;
import com.cardio.service.PatientMetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class MetricsController {

    private final PatientMetricService patientMetricService;
    private final ConsultationRepository consultationRepository;

    @PostMapping("/save")
    public ResponseEntity<?> saveMetrics(@RequestBody MedicalMetricsDTO dto) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Basic validation
            if (dto.getGender() == null || !(dto.getGender().equalsIgnoreCase("Male") || dto.getGender().equalsIgnoreCase("Female"))) {
                response.put("status", "failed");
                response.put("message", "Giới tính không hợp lệ. Phải là 'Male' hoặc 'Female'.");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> aiDataMap = patientMetricService.saveMetricsAndPredict(dto);

            response.put("status", "success");
            response.put("ai_data", aiDataMap);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Failed to save metrics: {}", e.getMessage());
            response.put("status", "failed");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (RestClientException e) {
            log.error("[MetricsController] AI Service connection error: {}", e.getMessage());
            response.put("status", "failed");
            response.put("message", "Không thể kết nối đến máy chủ AI để phân tích.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        } catch (Exception e) {
            log.error("System error in saveMetrics for patientId {}: {}", dto.getPatientId(), e.getMessage(), e);
            response.put("status", "failed");
            response.put("message", "Lỗi hệ thống, không thể lưu chỉ số.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(@RequestParam String patientId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Integer pId = Integer.parseInt(patientId);
            // This requires adding the method to ConsultationRepository
            List<ConsultationRecord> records = consultationRepository.findByPatientPatientIdAndStatusOrderByVisitDateDesc(pId, "SELF_REPORTED");

            List<Map<String, Object>> history = records.stream().map(record -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("visit_date", record.getVisitDate());
                if (record.getClinicalMetrics() != null) {
                    HeartClinicalMetrics metrics = record.getClinicalMetrics();
                    entry.put("resting_bp", metrics.getRestingBP());
                    entry.put("cholesterol", metrics.getCholesterol());
                    entry.put("max_heart_rate", metrics.getMaxHeartRate());
                }
                if (record.getAiRiskPrediction() != null) {
                    entry.put("risk_level", record.getAiRiskPrediction().getRiskLevel());
                }
                return entry;
            }).collect(Collectors.toList());

            response.put("status", "success");
            response.put("history", history);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting patient history for ID {}: {}", patientId, e.getMessage(), e);
            response.put("status", "failed");
            response.put("message", "Lỗi hệ thống khi tải lịch sử.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}