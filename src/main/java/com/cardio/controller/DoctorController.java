package com.cardio.controller;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.model.*;
import com.cardio.repository.DoctorRepository;
import com.cardio.repository.StaffRepository;
import com.cardio.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.cardio.repository.AppointmentRepository;
import com.cardio.repository.ConsultationRepository;
import com.cardio.repository.HeartClinicalMetricsRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import com.cardio.model.IcdCatalog;
import com.cardio.model.RecordIcd;
import com.cardio.model.HeartClinicalMetrics;

// ── CONTROLLER (C trong MVC) ─────────────────────────
@Controller
@RequestMapping("/doctor")
@RequiredArgsConstructor
@Slf4j
public class DoctorController {

    private final DoctorRepository doctorRepository;
    private final StaffRepository staffRepository;
    private final PatientService patientService;
    private final ConsultationService consultationService;
    private final AIService aiService;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final HeartClinicalMetricsRepository heartClinicalMetricsRepository;
    private final AuditLogService auditLogService;

    // Helper lấy doctor hoặc staff đang đăng nhập
    private DoctorProfile getCurrentDoctor(UserDetails userDetails) {
        String username = userDetails.getUsername();
        var docOpt = doctorRepository.findByUsername(username);
        if (docOpt.isPresent()) {
            return docOpt.get();
        }

        var staffOpt = staffRepository.findByUsername(username);
        if (staffOpt.isPresent()) {
            StaffProfile staff = staffOpt.get();
            DoctorProfile mockDoc = new DoctorProfile();
            mockDoc.setDoctorId(null);
            mockDoc.setUsername(staff.getUsername());
            mockDoc.setFullName(staff.getFullName());

            String roleText = "Nhân viên";
            if ("RECEPTIONIST".equalsIgnoreCase(staff.getRole())) {
                roleText = "Lễ tân";
            } else if ("STAFF".equalsIgnoreCase(staff.getRole())) {
                roleText = "Điều dưỡng";
            } else if ("ADMIN".equalsIgnoreCase(staff.getRole())) {
                roleText = "Quản trị viên";
            }
            mockDoc.setSpecialty(roleText);
            mockDoc.setStatus(staff.getStatus());
            return mockDoc;
        }

        throw new RuntimeException("Doctor or Staff profile not found for username: " + username);
    }

    // ── DASHBOARD ──────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        try {
            DoctorProfile doctor = getCurrentDoctor(userDetails);

            boolean isReceptionist = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_RECEPTIONIST"));
            boolean isStaff = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
            model.addAttribute("isReceptionist", isReceptionist);
            model.addAttribute("isStaff", isStaff);

            List<PatientProfile> patients = List.of();
            try {
                if (doctor.getDoctorId() != null) {
                    patients = patientService.getPatientsAssignedToDoctor(doctor.getDoctorId());
                } else {
                    patients = patientService.getAllPatients();
                }
            } catch (Exception ex) {
                System.err.println("Error querying patients: " + ex.getMessage());
            }

            List<AIRiskPrediction> alerts = List.of();
            try {
                if (doctor.getDoctorId() != null) {
                    alerts = consultationService.getAlertsByDoctor(doctor.getDoctorId());
                } else {
                    alerts = consultationService.getUnhandledHighAlerts(); // Hiện các cảnh báo chung cho nhân viên
                }
            } catch (Exception ex) {
                System.err.println("Error querying alerts: " + ex.getMessage());
            }
            long highAlerts = alerts.stream().filter(a -> a != null && "HIGH".equals(a.getRiskLevel())).count();

            // Truy vấn Lịch hẹn an toàn
            long totalApps = 0;
            List<Appointment> appsList = List.of();
            try {
                totalApps = appointmentRepository.count();
                appsList = appointmentRepository.findAllByOrderByScheduledDateDescTimeSlotAsc().stream().limit(5)
                        .toList();
            } catch (Exception ex) {
                System.err.println("Error querying appointments: " + ex.getMessage());
            }

            // BR02: Gắn mức cảnh báo mới nhất cho từng bệnh nhân
            java.util.Map<Integer, String> patientRiskMap = new java.util.HashMap<>();
            alerts.forEach(a -> {
                if (a != null && a.getRecord() != null && a.getRecord().getPatient() != null) {
                    patientRiskMap.merge(
                            a.getRecord().getPatient().getPatientId(),
                            a.getRiskLevel(),
                            (existing, newVal) -> "HIGH".equals(existing) ? existing
                            : "HIGH".equals(newVal) ? newVal
                            : "MEDIUM".equals(existing) ? existing : newVal
                    );
                }
            });

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
            model.addAttribute("totalAppointments", totalApps);
            model.addAttribute("appointments", appsList);
            return "doctor/dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/login?error=dashboard";
        }
    }

    // ── DANH SÁCH BỆNH NHÂN ────────────────────────────
    @GetMapping("/patients")
    public String patients(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null) {
            return "redirect:/doctor/appointments";
        }
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

    @GetMapping("/assigned-patients")
    public String assignedPatients(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dateStr,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        
        LocalDate date = null;
        if (dateStr != null && !dateStr.isBlank()) {
            date = LocalDate.parse(dateStr);
        }
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        Page<PatientProfile> patientPage = patientService.searchAssignedPatients(doctor.getDoctorId(), search, date, pageable);
        
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
        model.addAttribute("selectedDate", dateStr);
        return "doctor/assigned-patients";
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
            RedirectAttributes ra,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(id, doctor.getDoctorId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền truy cập hồ sơ bệnh án của bệnh nhân này!");
            return "redirect:/doctor/appointments";
        }
        PatientProfile patient = patientService.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        List<ConsultationRecord> records = consultationService.getByPatient(patient);

        // Lấy danh sách chẩn đoán ICD cho từng record
        java.util.Map<Integer, List<RecordIcd>> recordDiagnosesMap = new java.util.HashMap<>();
        records.forEach(r -> recordDiagnosesMap.put(
                r.getRecordId(),
                consultationService.getDiagnosesByRecord(r.getRecordId())
        ));

        boolean hasActiveAppointment = false;
        if (doctor.getDoctorId() != null) {
            hasActiveAppointment = appointmentRepository.findAll().stream().anyMatch(a ->
                a.getPatient().getPatientId().equals(id) &&
                a.getDoctor() != null &&
                a.getDoctor().getDoctorId().equals(doctor.getDoctorId()) &&
                "InProgress".equals(a.getStatus())
            );
        }

        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        model.addAttribute("recordDiagnosesMap", recordDiagnosesMap);
        model.addAttribute("highAlertCount", 0L);
        model.addAttribute("hasActiveAppointment", hasActiveAppointment);
        return "doctor/patient-detail";
    }

    // ── UC02: Cập nhật hồ sơ khám ──────────────────────────
    @PostMapping("/records/{recordId}/update")
    public String updateRecord(@PathVariable Integer recordId,
            @RequestParam String consultationNotes,
            @RequestParam(required = false) String treatmentPlan,
            @RequestParam Integer patientId,
            @RequestParam(required = false) Integer restingBP,
            @RequestParam(required = false) Integer maxHeartRate,
            @RequestParam(required = false) Double temperature,
            @RequestParam(required = false) Integer spO2,
            @RequestParam(value = "bloodTestFile", required = false) MultipartFile bloodTestFile,
            @RequestParam(value = "urineTestFile", required = false) MultipartFile urineTestFile,
            @RequestParam(value = "xrayFile", required = false) MultipartFile xrayFile,
            @RequestParam(value = "ultrasoundFile", required = false) MultipartFile ultrasoundFile,
            @RequestParam(value = "mriFile", required = false) MultipartFile mriFile,
            @RequestParam(value = "ctFile", required = false) MultipartFile ctFile,
            @RequestParam(required = false) Integer chestPainType,
            @RequestParam(required = false) Integer cholesterol,
            @RequestParam(required = false) Boolean fastingBloodSugar,
            @RequestParam(required = false) Integer restingECG,
            @RequestParam(required = false) Boolean exerciseAngina,
            @RequestParam(required = false) Double oldpeak,
            @RequestParam(required = false) String slope,
            @RequestParam(required = false) Integer ca,
            @RequestParam(required = false) String thal,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền thực hiện thao tác trên bệnh nhân này!");
            return "redirect:/doctor/appointments";
        }
        // BR04: Lấy record cũ để audit log
        var oldRecord = consultationService.getRecordById(recordId);
        String oldNotes = oldRecord.map(r -> r.getConsultationNotes()).orElse("");
        String oldPlan = oldRecord.map(r -> r.getTreatmentPlan()).orElse("");

        // Save files if uploaded
        String bloodPath = saveUploadedFile(bloodTestFile, "blood");
        String urinePath = saveUploadedFile(urineTestFile, "urine");
        String xrayPath = saveUploadedFile(xrayFile, "xray");
        String ultrasoundPath = saveUploadedFile(ultrasoundFile, "ultrasound");
        String mriPath = saveUploadedFile(mriFile, "mri");
        String ctPath = saveUploadedFile(ctFile, "ct");

        // Cập nhật
        consultationService.updateRecord(recordId, consultationNotes, treatmentPlan,
                restingBP, maxHeartRate, temperature, spO2, 
                bloodPath, urinePath, xrayPath, ultrasoundPath, mriPath, ctPath,
                chestPainType, cholesterol, fastingBloodSugar, restingECG, exerciseAngina, oldpeak, slope, ca, thal);

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
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền thực hiện thao tác trên bệnh nhân này!");
            return "redirect:/doctor/appointments";
        }
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
        List<AIRiskPrediction> alerts;
        if (doctor.getDoctorId() != null) {
            alerts = consultationService.getAlertsByDoctor(doctor.getDoctorId());
        } else {
            alerts = consultationService.getUnhandledHighAlerts(); // Hiện các cảnh báo chung cho nhân viên
        }

        model.addAttribute("doctor", doctor);
        model.addAttribute("alerts", alerts);
        model.addAttribute("highCount", alerts.stream().filter(a -> "HIGH".equals(a.getRiskLevel())).count());
        model.addAttribute("medCount", alerts.stream().filter(a -> "MEDIUM".equals(a.getRiskLevel())).count());
        model.addAttribute("lowCount", alerts.stream().filter(a -> "LOW".equals(a.getRiskLevel())).count());
        return "doctor/alerts";
    }

    // ── DỰ ĐOÁN AI (có SHAP + Trend) ──────────────────
    @GetMapping("/ai-predict")
    public String aiPredictForm(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer patientId,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        model.addAttribute("doctor", doctor);
        if (doctor.getDoctorId() != null) {
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
        } else {
            model.addAttribute("patients", patientService.getAllPatients());
        }
        if (patientId != null) {
            model.addAttribute("selectedPatientId", patientId);
        }
        model.addAttribute("aiRequest", new AIRequest());
        return "doctor/ai-predict";
    }

    @PostMapping("/ai-predict/analyze")
    public String runAIAnalyze(@ModelAttribute AIRequest aiRequest,
            @RequestParam Integer patientId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId())) {
            model.addAttribute("error", "Bạn không có quyền chạy dự đoán AI cho bệnh nhân này!");
            model.addAttribute("doctor", doctor);
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
            model.addAttribute("aiRequest", aiRequest);
            return "doctor/ai-predict";
        }
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
        if (doctor.getDoctorId() != null) {
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
        } else {
            model.addAttribute("patients", patientService.getAllPatients());
        }
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
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId())) {
            model.addAttribute("error", "Bạn không có quyền lưu hồ sơ bệnh án cho bệnh nhân này!");
            model.addAttribute("doctor", doctor);
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
            model.addAttribute("aiRequest", aiRequest);
            return "doctor/ai-predict";
        }
        PatientProfile patient = patientService.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        // Save records and prediction to database
        AIRiskPrediction prediction = consultationService.saveRecordAfterPrediction(
                patient, doctor, doctorNotes, treatmentPlan, riskScore, riskLevel, riskExplanation, aiRequest);
        model.addAttribute("doctor", doctor);
        if (doctor.getDoctorId() != null) {
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
        } else {
            model.addAttribute("patients", patientService.getAllPatients());
        }
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("prediction", prediction);
        model.addAttribute("selectedPatient", patient);
        model.addAttribute("isSaved", false); // Record is now saved
        return "doctor/ai-predict";
    }

    // ── LỊCH HẸN (APPOINTMENTS) ──────────────────────────
    @GetMapping("/appointments")
    public String appointments(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam(required = false) String dateStr,
                               Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        LocalDate date = (dateStr != null && !dateStr.isBlank()) ? LocalDate.parse(dateStr) : LocalDate.now();

        List<Appointment> appointments;
        List<DoctorProfile> doctors;

        if (doctor.getDoctorId() != null) {
            // Doctors only view their own schedule
            appointments = appointmentRepository.findAll().stream()
                    .filter(a -> a.getScheduledDate().equals(date) &&
                                 a.getDoctor() != null &&
                                 a.getDoctor().getDoctorId().equals(doctor.getDoctorId()))
                    .sorted((a1, a2) -> {
                        LocalTime t1 = a1.getTimeSlot();
                        LocalTime t2 = a2.getTimeSlot();
                        if (t1 != null && t2 != null) {
                            return t1.compareTo(t2);
                        }
                        if (t1 != null) return -1;
                        if (t2 != null) return 1;

                        LocalDateTime r1 = a1.getRequestTime();
                        LocalDateTime r2 = a2.getRequestTime();
                        if (r1 != null && r2 != null) {
                            return r1.compareTo(r2);
                        }
                        if (r1 != null) return -1;
                        if (r2 != null) return 1;

                        return a1.getAppointmentId().compareTo(a2.getAppointmentId());
                    })
                    .toList();
            doctors = List.of(doctor);
        } else {
            // Staff / Receptionists see all
            appointments = appointmentRepository.findAll().stream()
                    .filter(a -> a.getScheduledDate().equals(date))
                    .sorted((a1, a2) -> {
                        LocalTime t1 = a1.getTimeSlot();
                        LocalTime t2 = a2.getTimeSlot();
                        if (t1 != null && t2 != null) {
                            return t1.compareTo(t2);
                        }
                        if (t1 != null) return -1;
                        if (t2 != null) return 1;

                        LocalDateTime r1 = a1.getRequestTime();
                        LocalDateTime r2 = a2.getRequestTime();
                        if (r1 != null && r2 != null) {
                            return r1.compareTo(r2);
                        }
                        if (r1 != null) return -1;
                        if (r2 != null) return 1;

                        return a1.getAppointmentId().compareTo(a2.getAppointmentId());
                    })
                    .toList();
            doctors = doctorRepository.findAll();
        }

        List<Appointment> allReference = appointmentRepository.findAll();
        Appointment.populateQueueNumbers(appointments, allReference);

        // Calculate workload, group appointments, and check for active InProgress check-up
        java.util.Map<Integer, Long> workloads = new java.util.HashMap<>();
        java.util.Map<Integer, Boolean> doctorHasInProgress = new java.util.HashMap<>();
        java.util.Map<Integer, List<Appointment>> appointmentsByDoctor = new java.util.HashMap<>();

        for (DoctorProfile doc : doctors) {
            long count = appointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doc.getDoctorId()) && !"Cancelled".equalsIgnoreCase(a.getStatus()))
                    .count();
            workloads.put(doc.getDoctorId(), count);

            List<Appointment> docApps = appointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doc.getDoctorId()))
                    .collect(java.util.stream.Collectors.toList());
            appointmentsByDoctor.put(doc.getDoctorId(), docApps);

            boolean hasInProgress = docApps.stream().anyMatch(a -> "InProgress".equalsIgnoreCase(a.getStatus()));
            doctorHasInProgress.put(doc.getDoctorId(), hasInProgress);
        }

        model.addAttribute("doctor", doctor);
        model.addAttribute("doctors", doctors);
        model.addAttribute("appointments", appointments);
        model.addAttribute("workloads", workloads);
        model.addAttribute("doctorHasInProgress", doctorHasInProgress);
        model.addAttribute("appointmentsByDoctor", appointmentsByDoctor);
        model.addAttribute("selectedDate", date);
        model.addAttribute("patients", doctor.getDoctorId() != null ? patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()) : patientService.getAllPatients());
        return "doctor/appointments";
    }

    @GetMapping("/appointments/{id}/details")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> getAppointmentDetails(@PathVariable Integer id) {
        Appointment app = appointmentRepository.findById(id).orElse(null);
        if (app == null) return org.springframework.http.ResponseEntity.notFound().build();

        List<ConsultationRecord> records = consultationService.getByPatient(app.getPatient());
        List<java.util.Map<String, Object>> historyList = new java.util.ArrayList<>();
        
        for (ConsultationRecord r : records) {
            java.util.Map<String, Object> rMap = new java.util.HashMap<>();
            rMap.put("visitDate", r.getVisitDate() != null ? r.getVisitDate().toString() : "--");
            rMap.put("doctorName", r.getDoctor() != null ? r.getDoctor().getFullName() : "Chưa rõ");
            rMap.put("notes", r.getConsultationNotes() != null ? r.getConsultationNotes() : "Chưa có ghi chú");
            rMap.put("treatmentPlan", r.getTreatmentPlan() != null ? r.getTreatmentPlan() : "Chưa có phác đồ");
            
            List<RecordIcd> diagnoses = consultationService.getDiagnosesByRecord(r.getRecordId());
            List<String> icdList = diagnoses.stream()
                    .map(d -> d.getIcdCatalog().getIcdCode() + " - " + d.getIcdCatalog().getDiseaseName() + (d.getNotes() != null && !d.getNotes().isEmpty() ? " (" + d.getNotes() + ")" : ""))
                    .toList();
            rMap.put("diagnoses", icdList);
            
            if (r.getClinicalMetrics() != null) {
                var metrics = r.getClinicalMetrics();
                rMap.put("metrics", java.util.Map.of(
                    "bp", metrics.getRestingBP() != null ? metrics.getRestingBP() : "--",
                    "hr", metrics.getMaxHeartRate() != null ? metrics.getMaxHeartRate() : "--",
                    "temp", metrics.getTemperature() != null ? metrics.getTemperature() : "--",
                    "spo2", metrics.getSpO2() != null ? metrics.getSpO2() : "--"
                ));
            } else {
                rMap.put("metrics", java.util.Map.of());
            }
            historyList.add(rMap);
        }
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("found", true);
        response.put("patientName", app.getPatient().getFullName());
        response.put("doctorName", app.getDoctor() != null ? app.getDoctor().getFullName() : "Chưa phân công");
        response.put("history", historyList);

        return org.springframework.http.ResponseEntity.ok(response);
    }

    @PostMapping("/appointments/save")
    public String saveAppointment(@RequestParam Integer patientId,
            @RequestParam Integer doctorId,
            @RequestParam String scheduledDate,
            RedirectAttributes ra) {
        try {
            Appointment app = new Appointment();
            PatientProfile patient = patientService.findById(patientId).orElseThrow(() -> new RuntimeException("Patient not found"));
            app.setPatient(patient);
            DoctorProfile doctor = doctorRepository.findById(doctorId).orElseThrow(() -> new RuntimeException("Doctor not found"));
            LocalDate date = LocalDate.parse(scheduledDate);
            
            LocalDate targetDate = date;
            boolean wasShifted = false;
            while (appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled") >= 8) {
                targetDate = targetDate.plusDays(1);
                wasShifted = true;
            }

            final LocalDate finalDate = targetDate;
            boolean alreadyBooked = appointmentRepository.findAll().stream()
                .anyMatch(a -> a.getPatient().getPatientId().equals(patient.getPatientId())
                        && a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                        && a.getScheduledDate().equals(finalDate)
                        && !"Cancelled".equalsIgnoreCase(a.getStatus()));
            if (alreadyBooked) {
                ra.addFlashAttribute("error", "Bệnh nhân " + patient.getFullName() + " đã có lịch hẹn khám với bác sĩ " + doctor.getFullName() + " vào ngày này rồi.");
                return "redirect:/doctor/appointments";
            }

            app.setDoctor(doctor);
            if (doctor.getRoomNumber() != null) {
                app.setRoomNumber(doctor.getRoomNumber());
            }
            app.setScheduledDate(targetDate);
            app.setTimeSlot(null);
            app.setStatus("Pending");
            appointmentRepository.save(app);

            if (wasShifted) {
                ra.addFlashAttribute("success", "Đã đặt lịch hẹn khám thành công! Do bác sĩ đã đầy lịch khám vào ngày đã chọn (tối đa 8 ca/ngày), lịch hẹn đã được tự động chuyển sang ngày " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
            } else {
                ra.addFlashAttribute("success", "Đã đặt lịch hẹn thành công!");
            }
        } catch (Exception e) {
            log.error("Error saving appointment by doctor: ", e);
            String message = e.getMessage();
            if (message != null && (message.contains("unique_doctor_slot") || message.contains("unique_patient_slot") || message.contains("ConstraintViolation") || message.contains("duplicate key"))) {
                ra.addFlashAttribute("error", "Lỗi đặt lịch hẹn: đã có lịch hẹn được đặt trước đó");
            } else {
                ra.addFlashAttribute("error", "Lỗi đặt lịch hẹn: " + e.getMessage());
            }
        }
        return "redirect:/doctor/appointments";
    }

    @PostMapping("/appointments/update-status")
    public String updateAppointmentStatus(@RequestParam Integer appointmentId,
            @RequestParam String status,
            @RequestParam(required = false) String scheduledDate,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) String timeSlot,
            @RequestParam(required = false) String endTime,
            RedirectAttributes ra) {
        try {
            Appointment app = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));
            
            LocalDate date = (scheduledDate != null && !scheduledDate.isBlank()) ? LocalDate.parse(scheduledDate) : app.getScheduledDate();
            DoctorProfile doctor = app.getDoctor();
            LocalDate targetDate = date;
            boolean wasShifted = false;

            if (doctor != null && !"Cancelled".equalsIgnoreCase(status)) {
                // Auto-overflow logic: find next consecutive day where doctor has < 8 appointments
                boolean isSameDoctorAndDate = app.getDoctor() != null &&
                        app.getDoctor().getDoctorId().equals(doctor.getDoctorId()) &&
                        app.getScheduledDate().equals(targetDate) &&
                        !"Cancelled".equalsIgnoreCase(app.getStatus());

                long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled");
                if (bookedCount >= 8 && !isSameDoctorAndDate) {
                    while (true) {
                        targetDate = targetDate.plusDays(1);
                        boolean isSameOnNext = app.getDoctor() != null &&
                                app.getDoctor().getDoctorId().equals(doctor.getDoctorId()) &&
                                app.getScheduledDate().equals(targetDate) &&
                                !"Cancelled".equalsIgnoreCase(app.getStatus());
                        long nextBookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled");
                        if (nextBookedCount < 8 || isSameOnNext) {
                            break;
                        }
                    }
                    wasShifted = true;
                }
            }

            if (doctor != null && !"Cancelled".equalsIgnoreCase(status)) {
                final LocalDate finalDate = targetDate;
                boolean alreadyBooked = appointmentRepository.findAll().stream()
                    .anyMatch(a -> !a.getAppointmentId().equals(appointmentId)
                            && a.getPatient().getPatientId().equals(app.getPatient().getPatientId())
                            && a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                            && a.getScheduledDate().equals(finalDate)
                            && !"Cancelled".equalsIgnoreCase(a.getStatus()));
                if (alreadyBooked) {
                    ra.addFlashAttribute("error", "Bệnh nhân đã có lịch hẹn khám với bác sĩ này vào ngày " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
                    return "redirect:/doctor/appointments";
                }
            }

            if ("InProgress".equalsIgnoreCase(status)) {
                if (doctor != null && appointmentRepository.existsByDoctorAndScheduledDateAndStatusAndAppointmentIdNot(doctor, targetDate, "InProgress", appointmentId)) {
                    ra.addFlashAttribute("error", "Bác sĩ đang có một ca khám chưa hoàn thành. Vui lòng hoàn thành ca khám hiện tại trước khi bắt đầu tiếp nhận bệnh nhân tiếp theo!");
                    return "redirect:/doctor/appointments";
                }
            }
            
            app.setStatus(status);
            if (roomNumber != null && !roomNumber.isBlank()) {
                app.setRoomNumber(roomNumber);
            } else if (app.getRoomNumber() == null && app.getDoctor() != null) {
                app.setRoomNumber(app.getDoctor().getRoomNumber());
            }
            app.setScheduledDate(targetDate);

            // Handle Check-in arrival time
            if (timeSlot != null && !timeSlot.isBlank()) {
                app.setTimeSlot(LocalTime.parse(timeSlot));
            } else if ("CheckedIn".equalsIgnoreCase(status) && app.getTimeSlot() == null) {
                app.setTimeSlot(LocalTime.now());
            } else if (!"CheckedIn".equalsIgnoreCase(status) && !"InProgress".equalsIgnoreCase(status) && !"Completed".equalsIgnoreCase(status)) {
                app.setTimeSlot(null); // Reset arrival time if moved back
            }

            // Handle Completed end time
            if (endTime != null && !endTime.isBlank()) {
                app.setEndTime(LocalTime.parse(endTime));
            } else if ("Completed".equalsIgnoreCase(status) && app.getEndTime() == null) {
                app.setEndTime(LocalTime.now());
            } else if (!"Completed".equalsIgnoreCase(status)) {
                app.setEndTime(null);
            }

            appointmentRepository.save(app);

            if (wasShifted) {
                ra.addFlashAttribute("success", "Đã cập nhật lịch khám! Do bác sĩ đã đầy lịch khám vào ngày được chọn, ngày khám được tự động chuyển sang ngày " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
            } else {
                ra.addFlashAttribute("success", "Đã cập nhật trạng thái lịch hẹn!");
            }
        } catch (Exception e) {
            log.error("Error updating appointment status: ", e);
            String message = e.getMessage();
            if (message != null && (message.contains("unique_doctor_slot") || message.contains("unique_patient_slot") || message.contains("ConstraintViolation") || message.contains("duplicate key"))) {
                ra.addFlashAttribute("error", "Lỗi cập nhật lịch hẹn: đã có lịch hẹn được đặt trước đó");
            } else {
                ra.addFlashAttribute("error", "Lỗi cập nhật lịch hẹn: " + e.getMessage());
            }
        }
        return "redirect:/doctor/appointments";
    }

    // ── GHI NHẬN CHỈ SỐ SINH TỒN & XÉT NGHIỆM (MEDICAL STAFF) ────
    @GetMapping("/patients/{id}/vitals")
    public String vitalsForm(@PathVariable Integer id,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(id, doctor.getDoctorId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền nhập chỉ số cho bệnh nhân này!");
            return "redirect:/doctor/appointments";
        }
        PatientProfile patient = patientService.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("vitals", new HeartClinicalMetrics());
        return "doctor/vitals-form";
    }

    @PostMapping("/patients/{id}/vitals/save")
    public String saveVitals(@PathVariable Integer id,
            @ModelAttribute HeartClinicalMetrics vitals,
            @RequestParam(required = false) String consultationNotes,
            @RequestParam(required = false) String treatmentPlan,
            @RequestParam(required = false) String status,
            @RequestParam(value = "bloodTestFile", required = false) MultipartFile bloodTestFile,
            @RequestParam(value = "urineTestFile", required = false) MultipartFile urineTestFile,
            @RequestParam(value = "xrayFile", required = false) MultipartFile xrayFile,
            @RequestParam(value = "ultrasoundFile", required = false) MultipartFile ultrasoundFile,
            @RequestParam(value = "mriFile", required = false) MultipartFile mriFile,
            @RequestParam(value = "ctFile", required = false) MultipartFile ctFile,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(id, doctor.getDoctorId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền nhập chỉ số cho bệnh nhân này!");
            return "redirect:/doctor/appointments";
        }
        String username = userDetails.getUsername();
        StaffProfile staff = staffRepository.findByUsername(username).orElse(null);

        PatientProfile patient = patientService.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // Save uploaded files
        if (bloodTestFile != null && !bloodTestFile.isEmpty()) {
            vitals.setBloodTest(saveUploadedFile(bloodTestFile, "blood"));
        }
        if (urineTestFile != null && !urineTestFile.isEmpty()) {
            vitals.setUrineTest(saveUploadedFile(urineTestFile, "urine"));
        }
        if (xrayFile != null && !xrayFile.isEmpty()) {
            vitals.setXray(saveUploadedFile(xrayFile, "xray"));
        }
        if (ultrasoundFile != null && !ultrasoundFile.isEmpty()) {
            vitals.setUltrasound(saveUploadedFile(ultrasoundFile, "ultrasound"));
        }
        if (mriFile != null && !mriFile.isEmpty()) {
            vitals.setMri(saveUploadedFile(mriFile, "mri"));
        }
        if (ctFile != null && !ctFile.isEmpty()) {
            vitals.setCt(saveUploadedFile(ctFile, "ct"));
        }

        // 1. Lưu hồ sơ khám (ConsultationRecord)
        ConsultationRecord record = new ConsultationRecord();
        record.setPatient(patient);
        if (doctor != null && doctor.getDoctorId() != null) {
            record.setDoctor(doctor);
        } else {
            record.setDoctor(null);
        }
        record.setVisitDate(LocalDateTime.now());
        record.setConsultationNotes(consultationNotes != null && !consultationNotes.trim().isEmpty() ? consultationNotes : 
                ("Nhập chỉ số sinh tồn & xét nghiệm bởi " + (staff != null ? staff.getFullName() : (doctor != null ? doctor.getFullName() : "Nhân viên y tế"))));
        record.setTreatmentPlan(treatmentPlan != null && !treatmentPlan.trim().isEmpty() ? treatmentPlan : "Chờ khám chuyên khoa.");
        record.setStatus(status != null && !status.trim().isEmpty() ? status : "Completed");
        consultationRepository.save(record);

        // 2. Thiết lập liên kết và lưu chỉ số (HeartClinicalMetrics)
        vitals.setRecord(record);
        if (patient.getDob() != null) {
            vitals.setAge(java.time.Period.between(patient.getDob(), java.time.LocalDate.now()).getYears());
        }
        if (patient.getGender() != null) {
            vitals.setSex("Nam".equalsIgnoreCase(patient.getGender()) || "Male".equalsIgnoreCase(patient.getGender()) ? "Male" : "Female");
        }
        if (staff != null) {
            vitals.setRecordedByStaffID(staff.getStaffId());
        }
        vitals.setRecordedAt(LocalDateTime.now());
        heartClinicalMetricsRepository.save(vitals);

        ra.addFlashAttribute("success", "Đã ghi nhận bệnh án và các chỉ số sức khỏe của bệnh nhân!");
        return "redirect:/doctor/patients/" + id;
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
    public String thresholds(@AuthenticationPrincipal UserDetails userDetails, Model model, RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() == null) {
            ra.addFlashAttribute("error", "Chỉ bác sĩ mới có quyền cấu hình ngưỡng cảnh báo!");
            return "redirect:/doctor/dashboard";
        }
        model.addAttribute("doctor", doctor);
        return "doctor/thresholds";
    }

    @PostMapping("/thresholds/save")
    public String saveThresholds(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer alertThresholdBpm,
            @RequestParam String alertThresholdBp,
            RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() == null) {
            ra.addFlashAttribute("error", "Chỉ bác sĩ mới có quyền cấu hình ngưỡng cảnh báo!");
            return "redirect:/doctor/dashboard";
        }
        // BR12: Validate giới hạn lâm sàng
        if (alertThresholdBpm < 60 || alertThresholdBpm > 250) {
            ra.addFlashAttribute("error", "Ngưỡng nhịp tim phải từ 60–250 bpm!");
            return "redirect:/doctor/thresholds";
        }
        // BR13: Lưu audit log + cập nhật ngưỡng
        doctor.setAlertThresholdBpm(alertThresholdBpm);
        doctor.setAlertThresholdBp(alertThresholdBp);
        doctorRepository.save(doctor);
        ra.addFlashAttribute("success", "Đã lưu ngưỡng cảnh báo! BPM: " + alertThresholdBpm + ", BP: " + alertThresholdBp);
        return "redirect:/doctor/thresholds";
    }

    // ── BẮT ĐẦU CA KHÁM (START CONSULTATION) ─────────────────
    @PostMapping("/appointments/{id}/start")
    public String startConsultation(@PathVariable Integer id,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        Appointment app = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (doctor.getDoctorId() != null) {
            if (app.getDoctor() == null || !app.getDoctor().getDoctorId().equals(doctor.getDoctorId())) {
                ra.addFlashAttribute("error", "Lịch hẹn này không được phân công cho bạn!");
                return "redirect:/doctor/appointments";
            }
            if (appointmentRepository.existsByDoctorAndScheduledDateAndStatusAndAppointmentIdNot(doctor, app.getScheduledDate(), "InProgress", id)) {
            if (appointmentRepository.existsByDoctorAndScheduledDateAndStatusAndAppointmentIdNot(app.getDoctor(), app.getScheduledDate(), "InProgress", id)) {
                ra.addFlashAttribute("error", "Bác sĩ đang có một ca khám chưa hoàn thành. Vui lòng hoàn thành ca khám hiện tại trước khi bắt đầu tiếp nhận bệnh nhân tiếp theo!");
                return "redirect:/doctor/appointments";
            }
        }

        app.setStatus("InProgress");
        appointmentRepository.save(app);

        ra.addFlashAttribute("success", "Bắt đầu ca khám cho bệnh nhân " + app.getPatient().getFullName());
        return "redirect:/doctor/patients/" + app.getPatient().getPatientId();
    }

    // ── HOÀN THÀNH CA KHÁM (COMPLETE CONSULTATION) ───────────
    @PostMapping("/patients/{id}/complete-appointment")
    public String completeAppointment(@PathVariable Integer id,
                                      @AuthenticationPrincipal UserDetails userDetails,
                                      RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null) {
            var appOpt = appointmentRepository.findAll().stream()
                    .filter(a -> a.getPatient().getPatientId().equals(id) &&
                                 a.getDoctor() != null &&
                                 a.getDoctor().getDoctorId().equals(doctor.getDoctorId()) &&
                                 "InProgress".equals(a.getStatus()))
                    .findFirst();
            if (appOpt.isPresent()) {
                Appointment app = appOpt.get();
                app.setStatus("Completed");
                appointmentRepository.save(app);
                ra.addFlashAttribute("success", "Đã hoàn thành khám cho bệnh nhân " + app.getPatient().getFullName());
            } else {
                ra.addFlashAttribute("error", "Không tìm thấy ca khám đang thực hiện cho bệnh nhân này.");
            }
        }
        return "redirect:/doctor/appointments";
    }

    private List<Appointment> sortAppointmentsForQueue(List<Appointment> list) {
        if (list == null) return java.util.Collections.emptyList();
        return list.stream().sorted((a1, a2) -> {
            // Priority 1: Status InProgress
            int p1 = "InProgress".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int p2 = "InProgress".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (p1 != p2) return Integer.compare(p1, p2);

            // Priority 2: Status CheckedIn (FIFO by arrival time / timeSlot)
            int c1 = "CheckedIn".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int c2 = "CheckedIn".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (c1 != c2) return Integer.compare(c1, c2);
            if (c1 == 0) {
                if (a1.getTimeSlot() != null && a2.getTimeSlot() != null) {
                    return a1.getTimeSlot().compareTo(a2.getTimeSlot());
                }
                if (a1.getTimeSlot() != null) return -1;
                if (a2.getTimeSlot() != null) return 1;
            }

            // Priority 3: Status Confirmed
            int f1 = "Confirmed".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int f2 = "Confirmed".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (f1 != f2) return Integer.compare(f1, f2);

            // Priority 4: Status Pending
            int d1 = "Pending".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int d2 = "Pending".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (d1 != d2) return Integer.compare(d1, d2);

            // Priority 5: Completed
            int m1 = "Completed".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int m2 = "Completed".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (m1 != m2) return Integer.compare(m1, m2);

            // Priority 6: Cancelled
            int n1 = "Cancelled".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int n2 = "Cancelled".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (n1 != n2) return Integer.compare(n1, n2);

            // Fallback: ID
            if (a1.getAppointmentId() != null && a2.getAppointmentId() != null) {
                return a1.getAppointmentId().compareTo(a2.getAppointmentId());
            }
            return 0;
        }).collect(java.util.stream.Collectors.toList());
    }

    private String saveUploadedFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String projectPath = new java.io.File(".").getAbsolutePath();
            if (projectPath.endsWith(".")) {
                projectPath = projectPath.substring(0, projectPath.length() - 1);
            }
            projectPath = projectPath.replace("\\", "/");
            if (!projectPath.endsWith("/")) {
                projectPath += "/";
            }
            String uploadPathStr = projectPath + "uploads/" + subDir;
            java.io.File uploadDir = new java.io.File(uploadPathStr);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename().replace(" ", "_");
            java.nio.file.Path filePath = java.nio.file.Paths.get(uploadPathStr, fileName);
            java.nio.file.Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + subDir + "/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
>>>>>>> PhucQuynh_medicalstaff,-receptionist
    }
}
