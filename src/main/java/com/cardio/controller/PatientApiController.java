package com.cardio.controller;

import com.cardio.model.PatientProfile;
import com.cardio.model.SystemLog;
import com.cardio.repository.PatientRepository;
import com.cardio.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

import com.cardio.service.ChatService;
import com.cardio.dto.ChatRequest;
import com.cardio.dto.ChatResponse;

@RestController
@RequestMapping("/api/patient")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class PatientApiController {

    private final PatientRepository patientRepository;
    private final SystemLogRepository systemLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ChatService chatService;
    private final com.cardio.service.AIService aiService;

    private static final String SQL_SELECT_CONSULTATION_RECORDS =
            "SELECT r.RecordID AS \"RecordID\", r.VisitDate AS \"VisitDate\", r.ConsultationNotes AS \"ConsultationNotes\", " +
            "r.TreatmentPlan AS \"TreatmentPlan\", r.Status AS \"Status\", " +
            "d.FullName AS \"DoctorName\", d.Specialty AS \"DoctorSpecialty\", " +
            "p.RiskScore AS \"RiskScore\", p.RiskLevel AS \"RiskLevel\", p.RiskExplanation AS \"RiskExplanation\", " +
            "p.HealthAdvice AS \"HealthAdvice\", p.DietaryAdvice AS \"DietaryAdvice\", " +
            "m.ChestPainType AS \"ChestPainType\", m.RestingBP AS \"RestingBP\", m.Cholesterol AS \"Cholesterol\", " +
            "m.FastingBloodSugar AS \"FastingBloodSugar\", m.RestingECG AS \"RestingECG\", m.MaxHeartRate AS \"MaxHeartRate\", " +
            "m.ExerciseAngina AS \"ExerciseAngina\", m.SpO2 AS \"SpO2\", m.BloodTest AS \"BloodTest\", m.UrineTest AS \"UrineTest\", " +
            "m.Xray AS \"Xray\", m.Ultrasound AS \"Ultrasound\", m.Mri AS \"Mri\", m.Ct AS \"Ct\", " +
            "icd.ICDCode AS \"ICDCode\", icd.DiseaseName AS \"DiseaseName\" " +
            "FROM Consultation_Record r " +
            "LEFT JOIN Doctor_Profile d ON r.DoctorID = d.DoctorID " +
            "LEFT JOIN AI_Risk_Prediction p ON r.RecordID = p.RecordID " +
            "LEFT JOIN Heart_Clinical_Metrics m ON r.RecordID = m.RecordID " +
            "LEFT JOIN Record_ICD ri ON r.RecordID = ri.RecordID " +
            "LEFT JOIN ICD_Catalog icd ON ri.ICDCode = icd.ICDCode " +
            "WHERE r.PatientID = ? ORDER BY r.VisitDate DESC";

    private static final String SQL_SELECT_SELF_MONITORING =
            "SELECT LogID AS \"LogID\", LogDate AS \"LogDate\", CurrentHeartRate AS \"CurrentHeartRate\", Symptoms AS \"Symptoms\", TriggeredAlert AS \"TriggeredAlert\", AIAdvice AS \"AIAdvice\", AIRiskAssessment AS \"AIRiskAssessment\", Duration AS \"Duration\", Notes AS \"Notes\", SeverityScore AS \"SeverityScore\" FROM Patient_Self_Monitoring WHERE PatientID = ? ORDER BY LogDate DESC";

    private static final String SQL_INSERT_SELF_MONITORING =
            "INSERT INTO Patient_Self_Monitoring (PatientID, LogDate, CurrentHeartRate, Symptoms, TriggeredAlert, AIAdvice, AIRiskAssessment, Duration, Notes, SeverityScore) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?)";

    // 1. Get Patient Profile
    @GetMapping("/profile/{patientId}")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable("patientId") Integer patientId) {
        Optional<PatientProfile> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Patient profile not found"));
        }

        PatientProfile p = patientOpt.get();
        Map<String, Object> data = new HashMap<>();
        data.put("patientId", p.getPatientId());
        data.put("fullName", p.getFullName());
        data.put("username", p.getUsername());
        data.put("phone", p.getPhone() != null ? p.getPhone() : "");
        data.put("email", p.getUsername());
        data.put("address", p.getAddress() != null ? p.getAddress() : "");
        data.put("dob", p.getDob() != null ? p.getDob().toString() : "1990-01-01");
        data.put("gender", p.getGender() != null ? p.getGender() : "Nam");
        data.put("emergencyContactName", "Thân nhân (Mặc định)");
        data.put("emergencyContactPhone", p.getPhone() != null ? p.getPhone() : "");
        data.put("bloodType", "O+");

        return ResponseEntity.ok(Map.of("status", "success", "data", data));
    }

    // 2. Update Patient Profile (Cập nhật thông tin cá nhân)
    @PutMapping("/profile/{patientId}")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @PathVariable("patientId") Integer patientId,
            @RequestBody Map<String, String> body) {

        Optional<PatientProfile> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Patient profile not found"));
        }

        PatientProfile p = patientOpt.get();
        if (body.containsKey("fullName") && !body.get("fullName").isBlank()) {
            p.setFullName(body.get("fullName"));
        }
        if (body.containsKey("phone")) {
            p.setPhone(body.get("phone"));
        }
        if (body.containsKey("address")) {
            p.setAddress(body.get("address"));
        }

        patientRepository.save(p);

        // System Log
        SystemLog sysLog = new SystemLog();
        sysLog.setUsername(p.getUsername());
        sysLog.setAction("API_UPDATE_PROFILE");
        sysLog.setDetails("Bệnh nhân cập nhật thông tin cá nhân qua ứng dụng mobile");
        sysLog.setTimestamp(LocalDateTime.now());
        systemLogRepository.save(sysLog);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Cập nhật thông tin cá nhân thành công!"));
    }

    // 3. Get Medical History Records from Supabase DB Consultation_Record
    @GetMapping("/history/{patientId}")
    public ResponseEntity<Map<String, Object>> getMedicalHistory(@PathVariable("patientId") Integer patientId) {
        List<Map<String, Object>> records = new ArrayList<>();
        try {
            records = jdbcTemplate.queryForList(SQL_SELECT_CONSULTATION_RECORDS, patientId);
        } catch (Exception e) {
            log.warn("Query consultation records from Supabase failed: ", e);
        }

        return ResponseEntity.ok(Map.of("status", "success", "total", records.size(), "records", records));
    }

    // 4. Get Health Alerts (Xem cảnh báo bệnh sớm/cấp tính)
    @GetMapping("/alerts/{patientId}")
    public ResponseEntity<Map<String, Object>> getAlerts(@PathVariable("patientId") Integer patientId) {
        List<Map<String, Object>> selfLogs = new ArrayList<>();
        try {
            selfLogs = jdbcTemplate.queryForList(SQL_SELECT_SELF_MONITORING, patientId);
        } catch (Exception e) {
            log.warn("Query self monitoring failed: ", e);
        }

        return ResponseEntity.ok(Map.of("status", "success", "total", selfLogs.size(), "alerts", selfLogs));
    }

    // 5. Submit Symptom Update (Cập nhật triệu chứng mới vào Supabase Patient_Self_Monitoring)
    @PostMapping("/symptom-update")
    public ResponseEntity<Map<String, Object>> submitSymptomUpdate(@RequestBody Map<String, Object> body) {
        Integer patientId = (Integer) body.get("patientId");
        if (patientId == null) patientId = 1001;

        List<?> symptomsList = (List<?>) body.get("symptoms");
        String symptoms = symptomsList != null ? String.join(", ", symptomsList.stream().map(Object::toString).toList()) : "";
        Integer heartRate = body.get("heartRate") != null ? (Integer) body.get("heartRate") : 80;
        Integer severityScore = body.get("severityScore") != null ? (Integer) body.get("severityScore") : 5;
        String duration = (String) body.getOrDefault("duration", "Khoảng 30 phút");
        String notes = (String) body.getOrDefault("notes", "");

        // Call Gemini AI for real-time symptom analysis!
        Map<String, String> aiResult = aiService.getSymptomAdvice(symptoms, severityScore, duration, notes);
        String aiRiskAssessment = aiResult.getOrDefault("aiRiskAssessment", "Nguy cơ Thấp - Trung bình: Tiếp tục theo dõi sức khỏe.");
        String aiAdvice = aiResult.getOrDefault("aiAdvice", "");

        boolean triggeredAlert = severityScore >= 8 ||
                symptoms.contains("Đau ngực") ||
                symptoms.contains("Khó thở") ||
                symptoms.contains("Chóng mặt") ||
                aiRiskAssessment.toUpperCase().contains("CẤP TÍNH") ||
                aiRiskAssessment.toUpperCase().contains("CAO");

        try {
            jdbcTemplate.update(SQL_INSERT_SELF_MONITORING, patientId, heartRate, symptoms, triggeredAlert, aiAdvice, aiRiskAssessment, duration, notes, severityScore);
        } catch (Exception e) {
            log.error("Error inserting self monitoring log into Supabase: ", e);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("triggeredAlert", triggeredAlert);
        response.put("aiRiskAssessment", aiRiskAssessment);
        response.put("aiAdvice", aiAdvice);

        return ResponseEntity.ok(response);
    }

    // 5. Chatbot for Patient
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chatWithAI(@RequestBody ChatRequest request) {
        Integer patientId = request.getPatientId() != null ? request.getPatientId() : 1001;
        Map<String, Object> context = request.getPredict_context();
        if (context == null) {
            context = new HashMap<>();
        }

        try {
            Optional<PatientProfile> pOpt = patientRepository.findById(patientId);
            if (pOpt.isPresent()) {
                PatientProfile p = pOpt.get();
                context.put("Họ và tên bệnh nhân", p.getFullName());
                context.put("Giới tính", p.getGender() != null ? p.getGender() : "Nam");
                if (p.getDob() != null) {
                    int age = java.time.Period.between(p.getDob(), java.time.LocalDate.now()).getYears();
                    context.put("Ngày sinh", p.getDob().toString());
                    context.put("Tuổi hiện tại", age);
                }
                context.put("Số điện thoại", p.getPhone());
                context.put("Địa chỉ", p.getAddress());
                context.put("Nhóm máu", "O+");
            }

            List<Map<String, Object>> records = jdbcTemplate.queryForList(SQL_SELECT_CONSULTATION_RECORDS, patientId);
            if (!records.isEmpty()) {
                Map<String, Object> latest = records.get(0);
                context.put("Ngày khám gần nhất", latest.get("VisitDate"));
                context.put("Bác sĩ phụ trách", latest.get("DoctorName"));
                context.put("Chẩn đoán bệnh (ICD)", latest.get("DiseaseName"));
                context.put("Mức độ nguy cơ tim mạch", latest.get("RiskLevel"));
                context.put("Lời khuyên y tế của bác sĩ", latest.get("HealthAdvice"));
            }
        } catch (Exception e) {
            log.warn("Could not enrich patient context for chat: {}", e.getMessage());
        }

        request.setPredict_context(context);

        String originalMessage = request.getMessage();
        String patientPrompt = 
            "[HỆ THỐNG NHẮC NHỞ CHUYÊN MÔN: Bạn đang là Trợ lý AI Y Tế thông minh hỗ trợ BỆNH NHÂN của phòng khám CardioCare. "
            + "Hãy trả lời một cách thân thiện, dễ hiểu, đồng cảm và trấn an bệnh nhân. Sử dụng thông tin bối cảnh cá nhân của bệnh nhân (tuổi, tên, lịch sử khám) nếu bệnh nhân hỏi. "
            + "Khuyên bệnh nhân đi khám bác sĩ nếu có triệu chứng nguy hiểm. KHÔNG tự ý chẩn đoán hoặc kê đơn thuốc thay bác sĩ. "
            + "Trả lời ngắn gọn, súc tích, bằng tiếng Việt.]\n\n"
            + "[Câu hỏi của Bệnh nhân]: " + originalMessage;
        
        request.setMessage(patientPrompt);
        ChatResponse response = chatService.sendMessage(request, "patient");
        return ResponseEntity.ok(response);
    }
}
