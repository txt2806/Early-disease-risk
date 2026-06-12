package com.cardio.controller;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.model.*;
import com.cardio.repository.DoctorRepository;
import com.cardio.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;
import com.cardio.model.IcdCatalog;
import com.cardio.model.RecordIcd;
import com.cardio.model.HeartClinicalMetrics;

// ── CONTROLLER (C trong MVC) ─────────────────────────
@Controller
@RequestMapping("/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorRepository doctorRepository;
    private final PatientService patientService;
    private final ConsultationService consultationService;
    private final AIService aiService;
    private final AuditLogService auditLogService;

    // Helper lấy doctor đang đăng nhập
    private DoctorProfile getCurrentDoctor(UserDetails userDetails) {
        return doctorRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
    }

    // ── DASHBOARD ──────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        List<PatientProfile> patients = patientService.getAllPatients();
        List<AIRiskPrediction> alerts = consultationService.getAlertsByDoctor(doctor.getDoctorId());
        long highAlerts = alerts.stream().filter(a -> "HIGH".equals(a.getRiskLevel())).count();

        // BR02: Gắn mức cảnh báo mới nhất cho từng bệnh nhân
        java.util.Map<Integer, String> patientRiskMap = new java.util.HashMap<>();
        alerts.forEach(a -> patientRiskMap.merge(
                a.getRecord().getPatient().getPatientId(),
                a.getRiskLevel(),
                (existing, newVal) -> "HIGH".equals(existing) ? existing
                : "HIGH".equals(newVal) ? newVal
                : "MEDIUM".equals(existing) ? existing : newVal
        ));

        // BR02: Sắp xếp bệnh nhân theo mức nguy cơ (HIGH trước, MEDIUM, rồi LOW/none)
        List<PatientProfile> sortedPatients = patients.stream()
                .sorted((p1, p2) -> {
                    int s1 = "HIGH".equals(patientRiskMap.get(p1.getPatientId())) ? 2
                            : "MEDIUM".equals(patientRiskMap.get(p1.getPatientId())) ? 1 : 0;
                    int s2 = "HIGH".equals(patientRiskMap.get(p2.getPatientId())) ? 2
                            : "MEDIUM".equals(patientRiskMap.get(p2.getPatientId())) ? 1 : 0;
                    return Integer.compare(s2, s1);
                })
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", sortedPatients);
        model.addAttribute("patientRiskMap", patientRiskMap);
        model.addAttribute("alerts", alerts.stream().limit(5).toList());
        model.addAttribute("totalPatients", patients.size());
        model.addAttribute("highAlertCount", highAlerts);
        model.addAttribute("totalAlerts", alerts.size());
        return "doctor/dashboard";
    }

    // ── DANH SÁCH BỆNH NHÂN ────────────────────────────
    @GetMapping("/patients")
    public String patients(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        Page<PatientProfile> patientPage = (search != null && !search.isBlank())
                ? patientService.searchByName(search, pageable)
                : patientService.getAllPatients(pageable);

        // Gắn mức nguy cơ AI mới nhất cho từng bệnh nhân
        List<AIRiskPrediction> allAlerts = consultationService.getAlertsByDoctor(doctor.getDoctorId());
        java.util.Map<Integer, String> patientRiskMap = new java.util.HashMap<>();
        allAlerts.forEach(a -> patientRiskMap.merge(
                a.getRecord().getPatient().getPatientId(),
                a.getRiskLevel(),
                (existing, newVal) -> "HIGH".equals(existing) ? existing
                : "HIGH".equals(newVal) ? newVal
                : "MEDIUM".equals(existing) ? existing : newVal
        ));

        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patientPage.getContent());
        model.addAttribute("patientRiskMap", patientRiskMap);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", patientPage.getTotalPages());
        model.addAttribute("totalItems", patientPage.getTotalElements());
        model.addAttribute("search", search);
        return "doctor/patients";
    }

    // ── THÊM BỆNH NHÂN ─────────────────────────────────
    @GetMapping("/patients/new")
    public String newPatientForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        model.addAttribute("doctor", getCurrentDoctor(userDetails));
        model.addAttribute("patient", new PatientProfile());
        return "doctor/patient-form";
    }

    @PostMapping("/patients/save")
    public String savePatient(@ModelAttribute PatientProfile patient,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        PatientProfile saved = patientService.save(patient);
        // BR03: Ghi audit log
        auditLogService.logPatientCreated(
                userDetails.getUsername(), saved.getFullName(), saved.getPatientId()
        );
        ra.addFlashAttribute("success", "Đã thêm bệnh nhân " + patient.getFullName());
        return "redirect:/doctor/patients";
    }

    // ── CHI TIẾT BỆNH NHÂN ─────────────────────────────
    @GetMapping("/patients/{id}")
    public String patientDetail(@PathVariable Integer id,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        PatientProfile patient = patientService.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        List<ConsultationRecord> records = consultationService.getByPatient(patient);

        // Lấy danh sách chẩn đoán ICD cho từng record
        java.util.Map<Integer, List<RecordIcd>> recordDiagnosesMap = new java.util.HashMap<>();
        records.forEach(r -> recordDiagnosesMap.put(
                r.getRecordId(),
                consultationService.getDiagnosesByRecord(r.getRecordId())
        ));

        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        model.addAttribute("recordDiagnosesMap", recordDiagnosesMap);
        model.addAttribute("highAlertCount", 0L);
        return "doctor/patient-detail";
    }

    // ── UC02: Cập nhật hồ sơ khám ──────────────────────────
    @PostMapping("/records/{recordId}/update")
    public String updateRecord(@PathVariable Integer recordId,
            @RequestParam String consultationNotes,
            @RequestParam(required = false) String treatmentPlan,
            @RequestParam Integer patientId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        // BR04: Lấy record cũ để audit log
        var oldRecord = consultationService.getRecordById(recordId);
        String oldNotes = oldRecord.map(r -> r.getConsultationNotes()).orElse("");
        String oldPlan = oldRecord.map(r -> r.getTreatmentPlan()).orElse("");

        // Cập nhật
        consultationService.updateRecord(recordId, consultationNotes, treatmentPlan);

        // BR03: Ghi audit log
        auditLogService.logRecordUpdate(
                userDetails.getUsername(), recordId,
                oldNotes, consultationNotes,
                oldPlan, treatmentPlan
        );

        ra.addFlashAttribute("success", "Đã cập nhật hồ sơ khám!");
        return "redirect:/doctor/patients/" + patientId;
    }

    // ── UC02: Thêm chẩn đoán ICD-10 ──────────────────────────
    @PostMapping("/records/{recordId}/diagnosis")
    public String addDiagnosis(@PathVariable Integer recordId,
            @RequestParam String icdCode,
            @RequestParam(required = false) String diagnosisNotes,
            @RequestParam Integer patientId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        try {
            consultationService.addDiagnosis(recordId, icdCode, diagnosisNotes);
            // BR03: Ghi audit log chẩn đoán
            String diseaseName = consultationService.findIcd(icdCode)
                    .map(i -> i.getDiseaseName()).orElse("Unknown");
            auditLogService.logDiagnosis(
                    userDetails.getUsername(), recordId, icdCode, diseaseName
            );
            ra.addFlashAttribute("success", "Đã thêm chẩn đoán " + icdCode + "!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/patients/" + patientId;
    }

    // ── UC02: ICD Autocomplete API ────────────────────────────
    @GetMapping("/icd/search")
    @ResponseBody
    public List<IcdCatalog> searchIcd(@RequestParam String q) {
        if (q == null || q.length() < 1) {
            return List.of();
        }
        return consultationService.searchIcd(q);
    }

    // ── CẢNH BÁO ───────────────────────────────────────
    @GetMapping("/alerts")
    public String alerts(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        List<AIRiskPrediction> alerts = consultationService.getAlertsByDoctor(doctor.getDoctorId());

        model.addAttribute("doctor", doctor);
        model.addAttribute("alerts", alerts);
        model.addAttribute("highCount", alerts.stream().filter(a -> "HIGH".equals(a.getRiskLevel())).count());
        model.addAttribute("medCount", alerts.stream().filter(a -> "MEDIUM".equals(a.getRiskLevel())).count());
        model.addAttribute("lowCount", alerts.stream().filter(a -> "LOW".equals(a.getRiskLevel())).count());
        return "doctor/alerts";
    }

    // ── DỰ ĐOÁN AI (có SHAP + Trend) ──────────────────
    @GetMapping("/ai-predict")
    public String aiPredictForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        model.addAttribute("doctor", getCurrentDoctor(userDetails));
        model.addAttribute("patients", patientService.getAllPatients());
        model.addAttribute("aiRequest", new AIRequest());
        return "doctor/ai-predict";
    }

    @PostMapping("/ai-predict/analyze")
    public String runAIAnalyze(@ModelAttribute AIRequest aiRequest,
            @RequestParam Integer patientId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        PatientProfile patient = patientService.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // Lấy lịch sử các lần khám trước của bệnh nhân để phân tích trend
        List<ConsultationRecord> pastRecords = consultationService.getByPatient(patient);

        com.cardio.dto.AIResponse aiResponse;

        if (pastRecords.size() >= 1) {
            // Có lịch sử → gọi /predict/trend để phân tích xu hướng
            List<AIRequest> visitHistory = pastRecords.stream()
                    .filter(r -> r.getClinicalMetrics() != null)
                    .map(r -> {
                        // Chuyển Heart_Clinical_Metrics → AIRequest
                        var m = r.getClinicalMetrics();
                        AIRequest req = new AIRequest();
                        req.setAge(m.getAge() != null ? m.getAge().doubleValue() : aiRequest.getAge());
                        req.setSex(m.getSex() != null ? m.getSex() : aiRequest.getSex());
                        req.setCp(m.getChestPainType() != null ? mapCpInt(m.getChestPainType()) : aiRequest.getCp());
                        req.setTrestbps(m.getRestingBP() != null ? m.getRestingBP().doubleValue() : null);
                        req.setChol(m.getCholesterol() != null ? m.getCholesterol().doubleValue() : null);
                        req.setFbs(m.getFastingBloodSugar() != null ? (m.getFastingBloodSugar() ? "TRUE" : "FALSE") : null);
                        req.setThalch(m.getMaxHeartRate() != null ? m.getMaxHeartRate().doubleValue() : null);
                        req.setExang(m.getExerciseAngina() != null ? (m.getExerciseAngina() ? "TRUE" : "FALSE") : null);
                        req.setOldpeak(m.getOldpeak() != null ? m.getOldpeak().doubleValue() : null);
                        req.setCa(m.getCa() != null ? m.getCa().doubleValue() : null);
                        // req.setSlope(m.getSlope() != null ? mapSlopeInt(m.getSlope()) : null);
                        // req.setThal(m.getThal() != null ? mapThalInt(m.getThal()) : null);
                        req.setSlope(m.getSlope() != null ? m.getSlope() : null);
                        req.setThal(m.getThal() != null ? m.getThal() : null);
                        return req;
                    })
                    .collect(java.util.stream.Collectors.toList());

            // Thêm lần khám hiện tại vào cuối
            visitHistory.add(aiRequest);

            // Gọi trend nếu có ≥2 lần khám, không thì gọi predict thường
            aiResponse = visitHistory.size() >= 2
                    ? aiService.predictWithTrend(visitHistory)
                    : aiService.predict(aiRequest);
        } else {
            aiResponse = aiService.predict(aiRequest);
        }

        // Build prediction object để hiển thị
        AIRiskPrediction prediction = new AIRiskPrediction();
        prediction.setRiskScore(java.math.BigDecimal.valueOf(aiResponse.getProbability() * 100));
        prediction.setRiskLevel(aiResponse.getRisk_level());
        prediction.setRiskExplanation(aiResponse.getMessage());

        // Audit log
        auditLogService.logAIPrediction(
                userDetails.getUsername(), patientId,
                aiResponse.getRisk_level(),
                String.valueOf(Math.round(aiResponse.getProbability() * 100))
        );

        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patientService.getAllPatients());
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("prediction", prediction);
        model.addAttribute("aiResponse", aiResponse);   // Truyền full response cho SHAP + trend
        model.addAttribute("selectedPatient", patient);
        model.addAttribute("isSaved", false);
        return "doctor/ai-predict";
    }

    // Helper: map int → string cho ChestPainType
    private String mapCpInt(Integer cp) {
        return switch (cp) {
            case 0 ->
                "asymptomatic";
            case 1 ->
                "typical angina";
            case 2 ->
                "atypical angina";
            default ->
                "non-anginal";
        };
    }

    private String mapSlopeInt(Integer slope) {
        return switch (slope) {
            case 0 ->
                "downsloping";
            case 1 ->
                "flat";
            default ->
                "upsloping";
        };
    }

    private String mapThalInt(Integer thal) {
        return switch (thal) {
            case 0 ->
                "normal";
            case 1 ->
                "fixed defect";
            default ->
                "reversable defect";
        };
    }

    @PostMapping("/ai-predict/save")
    public String saveRecordAndPrediction(@ModelAttribute AIRequest aiRequest,
                                          @RequestParam Integer patientId,
                                          @RequestParam String doctorNotes,
                                          @RequestParam(required = false) String treatmentPlan,
                                          @RequestParam java.math.BigDecimal riskScore,
                                          @RequestParam String riskLevel,
                                          @RequestParam String riskExplanation,
                                          @AuthenticationPrincipal UserDetails userDetails,
                                          Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        PatientProfile patient = patientService.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        // Save records and prediction to database
        AIRiskPrediction prediction = consultationService.saveRecordAfterPrediction(
                patient, doctor, doctorNotes, treatmentPlan, riskScore, riskLevel, riskExplanation, aiRequest);
        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patientService.getAllPatients());
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("prediction", prediction);
        model.addAttribute("selectedPatient", patient);
        model.addAttribute("isSaved", false); // Record is now saved
        return "doctor/ai-predict";
    }
    // ── XỬ LÝ CẢNH BÁO ────────────────────────────────────
    @PostMapping("/alerts/{id}/resolve")
    public String resolveAlert(@PathVariable Integer id,
            @RequestParam(defaultValue = "resolved") String action,
            @RequestParam(required = false) String reason,
            RedirectAttributes ra) {
        consultationService.updateAlertStatus(id, action, reason);
        ra.addFlashAttribute("success", "Đã cập nhật trạng thái cảnh báo!");
        return "redirect:/doctor/alerts";
    }

    // ── CẤU HÌNH NGƯỠNG CẢNH BÁO ──────────────────────────
    @GetMapping("/thresholds")
    public String thresholds(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        model.addAttribute("doctor", doctor);
        return "doctor/thresholds";
    }

    @PostMapping("/thresholds/save")
    public String saveThresholds(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer alertThresholdBpm,
            @RequestParam String alertThresholdBp,
            RedirectAttributes ra) {
        // BR12: Validate giới hạn lâm sàng
        if (alertThresholdBpm < 60 || alertThresholdBpm > 250) {
            ra.addFlashAttribute("error", "Ngưỡng nhịp tim phải từ 60–250 bpm!");
            return "redirect:/doctor/thresholds";
        }
        // BR13: Lưu audit log + cập nhật ngưỡng
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        doctor.setAlertThresholdBpm(alertThresholdBpm);
        doctor.setAlertThresholdBp(alertThresholdBp);
        doctorRepository.save(doctor);
        ra.addFlashAttribute("success", "Đã lưu ngưỡng cảnh báo! BPM: " + alertThresholdBpm + ", BP: " + alertThresholdBp);
        return "redirect:/doctor/thresholds";
    }
}
