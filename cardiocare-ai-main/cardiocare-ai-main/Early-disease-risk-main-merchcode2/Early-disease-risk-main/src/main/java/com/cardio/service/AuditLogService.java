package com.cardio.service;

import com.cardio.model.SystemLog;
import com.cardio.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

// ── BR03: Audit Log Service ───────────────────────────
// Ghi lại mọi thao tác của bác sĩ vào System_Log
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final SystemLogRepository systemLogRepository;

    // Ghi log chung
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, String action, String details) {
        SystemLog log = new SystemLog();
        log.setUsername(username);
        log.setAction(action);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now());
        systemLogRepository.save(log);
    }

    // Ghi log cập nhật hồ sơ (BR03)
    public void logRecordUpdate(String username, Integer recordId,
                                 String oldNotes, String newNotes,
                                 String oldPlan, String newPlan) {
        StringBuilder details = new StringBuilder();
        details.append("RecordID=").append(recordId).append(" | ");
        if (!equals(oldNotes, newNotes)) {
            details.append("[ConsultationNotes] ").append(truncate(oldNotes))
                   .append(" → ").append(truncate(newNotes)).append(" | ");
        }
        if (!equals(oldPlan, newPlan)) {
            details.append("[TreatmentPlan] ").append(truncate(oldPlan))
                   .append(" → ").append(truncate(newPlan));
        }
        log(username, "UPDATE_RECORD", details.toString());
    }

    // Ghi log nhập chẩn đoán ICD-10 (BR06)
    public void logDiagnosis(String username, Integer recordId,
                              String icdCode, String diseaseName) {
        String details = "RecordID=" + recordId
                + " | ICD=" + icdCode
                + " (" + diseaseName + ")";
        log(username, "ADD_DIAGNOSIS", details);
    }

    // Ghi log thêm bệnh nhân mới
    public void logPatientCreated(String username, String patientName, Integer patientId) {
        log(username, "CREATE_PATIENT",
            "PatientID=" + patientId + " | FullName=" + patientName);
    }

    // Ghi log chạy AI
    public void logAIPrediction(String username, Integer patientId,
                                 String riskLevel, String riskScore) {
        log(username, "AI_PREDICT",
            "PatientID=" + patientId + " | Risk=" + riskLevel + " (" + riskScore + "%)");
    }

    public List<SystemLog> getAll() {
        return systemLogRepository.findAllByOrderByTimestampDesc();
    }

    // Helper
    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private String truncate(String s) {
        if (s == null) return "(null)";
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
