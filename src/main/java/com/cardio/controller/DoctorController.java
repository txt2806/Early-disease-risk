package com.cardio.controller;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.model.*;
import com.cardio.repository.DoctorRepository;
import com.cardio.repository.StaffRepository;
import com.cardio.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import com.cardio.model.DoctorProfile;
import java.util.Map;
import java.util.HashMap;
import com.cardio.model.PatientAlertThreshold;
import com.cardio.repository.PatientAlertThresholdRepository;

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
    private final ObjectMapper objectMapper; // [A.4] serialize top_factors/trend cho hidden input
    private final PatientAlertThresholdRepository thresholdRepository;

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
            hasActiveAppointment = appointmentRepository.findAll().stream().anyMatch(a
                    -> a.getPatient().getPatientId().equals(id)
                    && a.getDoctor() != null
                    && a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                    && "InProgress".equals(a.getStatus())
            );
        }

        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        model.addAttribute("recordDiagnosesMap", recordDiagnosesMap);

        // [FIX] Truyền HeartClinicalMetrics của từng record để form sửa có thể
        // hiển thị giá trị hiện tại (huyết áp, cholesterol...) thay vì ô trống
        java.util.Map<Integer, com.cardio.model.HeartClinicalMetrics> recordMetricsMap =
                new java.util.HashMap<>();
        records.forEach(r -> consultationService.getMetricsByRecord(r.getRecordId())
                .ifPresent(m -> recordMetricsMap.put(r.getRecordId(), m)));
        model.addAttribute("recordMetricsMap", recordMetricsMap);

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

    // ── UC02: Xoá chẩn đoán ICD-10 ──────────────────────────────
    @PostMapping("/records/{recordId}/diagnosis/remove")
    public String removeDiagnosis(@PathVariable Integer recordId,
            @RequestParam String icdCode,
            @RequestParam Integer patientId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null
                && !patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền thực hiện thao tác này!");
            return "redirect:/doctor/appointments";
        }
        try {
            consultationService.removeDiagnosis(recordId, icdCode);
            auditLogService.log(userDetails.getUsername(),
                    "REMOVE_ICD", "Record #" + recordId + " — xoá ICD " + icdCode);
            ra.addFlashAttribute("success", "Đã xoá chẩn đoán " + icdCode + "!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/patients/" + patientId;
    }

    // ── UC02: Cập nhật chỉ số lâm sàng (HeartClinicalMetrics) ────
    @PostMapping("/records/{recordId}/metrics/update")
    public String updateClinicalMetrics(@PathVariable Integer recordId,
            @RequestParam Integer patientId,
            @RequestParam(required = false) Integer restingBP,
            @RequestParam(required = false) Integer cholesterol,
            @RequestParam(required = false) String fastingBloodSugar,
            @RequestParam(required = false) Integer maxHeartRate,
            @RequestParam(required = false) String exerciseAngina,
            @RequestParam(required = false) Double oldpeak,
            @RequestParam(required = false) String slope,
            @RequestParam(required = false) Integer ca,
            @RequestParam(required = false) String thal,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null
                && !patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền thực hiện thao tác này!");
            return "redirect:/doctor/appointments";
        }
        // Parse fbs/exang nhất quán với A.1 (chuẩn '1.0'/'0.0')
        Boolean fbs   = fastingBloodSugar != null
                ? ("1.0".equals(fastingBloodSugar) || "true".equalsIgnoreCase(fastingBloodSugar))
                : null;
        Boolean exang = exerciseAngina != null
                ? ("1.0".equals(exerciseAngina) || "true".equalsIgnoreCase(exerciseAngina))
                : null;

        consultationService.updateClinicalMetrics(recordId, restingBP, cholesterol,
                fbs, maxHeartRate, exang, oldpeak, slope, ca, thal);
        auditLogService.log(userDetails.getUsername(),
                "UPDATE_METRICS", "Record #" + recordId + " — cập nhật chỉ số lâm sàng");
        ra.addFlashAttribute("success", "Đã cập nhật chỉ số lâm sàng!");
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
        DoctorProfile doctor = doctorRepository.findByUsername(userDetails.getUsername())
                .orElse(null);
        List<AIRiskPrediction> alerts;

        if (doctor != null) {
            alerts = consultationService.getAlertsByDoctorSorted(doctor.getDoctorId());
        } else {
            alerts = consultationService.getUnhandledHighAlerts();
        }

        // Truyền urgencyScore vào model để hiển thị badge "🔥 Khẩn cấp"
        Map<Integer, Double> urgencyMap = new HashMap<>();
        alerts.forEach(a -> urgencyMap.put(a.getPredictionId(),
                consultationService.calcUrgencyScore(a)));

        // [A.5] Đánh dấu riêng các ca VƯỢT ngưỡng cá nhân hoá
        Map<Integer, Boolean> thresholdExceededMap = new HashMap<>();
        alerts.forEach(a -> thresholdExceededMap.put(a.getPredictionId(),
                consultationService.exceedsPersonalThreshold(a)));

        // [FIX] Giá trị ngưỡng CỤ THỂ (số %) để hiển thị trực tiếp trên badge,
        // tránh bác sĩ phải chạy sang trang Ngưỡng cảnh báo để xem ngưỡng là bao nhiêu.
        Map<Integer, Double> thresholdValueMap = new HashMap<>();
        alerts.forEach(a -> {
            if (a.getRecord() != null && a.getRecord().getPatient() != null
                    && a.getRecord().getDoctor() != null) {
                thresholdRepository
                        .findByPatientAndDoctor(a.getRecord().getPatient(), a.getRecord().getDoctor())
                        .ifPresent(t -> thresholdValueMap.put(a.getPredictionId(), t.getRiskScoreThreshold()));
            }
        });

        // Filter counts (chỉ đếm chưa xử lý)
        List<AIRiskPrediction> unhandled = alerts.stream()
                .filter(a -> !a.getIsAlertSent()).toList();

        model.addAttribute("alerts", alerts);
        model.addAttribute("urgencyMap", urgencyMap);
        model.addAttribute("thresholdExceededMap", thresholdExceededMap);
        model.addAttribute("thresholdValueMap", thresholdValueMap);
        model.addAttribute("highCount", unhandled.stream().filter(a -> "HIGH".equals(a.getRiskLevel())).count());
        model.addAttribute("medCount", unhandled.stream().filter(a -> "MEDIUM".equals(a.getRiskLevel())).count());
        model.addAttribute("lowCount", unhandled.stream().filter(a -> "LOW".equals(a.getRiskLevel())).count());
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
        List<ConsultationRecord> pastRecords = consultationService.getByPatientChronological(patient);

        com.cardio.dto.AIResponse aiResponse;

        if (pastRecords.size() >= 1) {
            // Có lịch sử → gọi /predict/trend để phân tích xu hướng
            List<AIRequest> visitHistory = pastRecords.stream()
        .filter(r -> r.getClinicalMetrics() != null)
        .filter(r -> {
            // Chỉ giữ những bản ghi có ĐỦ field cần cho AI model
            var m = r.getClinicalMetrics();
            return m.getChestPainType() != null
                && m.getOldpeak() != null
                && m.getSlope() != null
                && m.getThal() != null
                && m.getCa() != null;
        })
        .map(r -> {
            var m = r.getClinicalMetrics();
            AIRequest req = new AIRequest();
            req.setAge(m.getAge() != null ? m.getAge().doubleValue() : aiRequest.getAge());
            req.setSex(m.getSex() != null ? m.getSex() : aiRequest.getSex());
            req.setCp(mapCpInt(m.getChestPainType()));
            req.setTrestbps(m.getRestingBP() != null ? m.getRestingBP().doubleValue() : null);
            req.setChol(m.getCholesterol() != null ? m.getCholesterol().doubleValue() : null);
            req.setFbs(m.getFastingBloodSugar() != null ? (m.getFastingBloodSugar() ? "1.0" : "0.0") : null);
            req.setThalch(m.getMaxHeartRate() != null ? m.getMaxHeartRate().doubleValue() : null);
            req.setExang(m.getExerciseAngina() != null ? (m.getExerciseAngina() ? "1.0" : "0.0") : null);
            req.setRestecg(mapRestecgInt(m.getRestingECG()));
            req.setOldpeak(m.getOldpeak().doubleValue());
            req.setCa(m.getCa().doubleValue());
            req.setSlope(m.getSlope());
            req.setThal(m.getThal());
            return req;
        })
        .collect(java.util.stream.Collectors.toList());

            // Thêm lần khám hiện tại vào cuối
            visitHistory.add(aiRequest);

            // Gọi trend nếu có ≥2 lần khám, không thì gọi predict thường
            aiResponse = visitHistory.size() >= 2
                    ? aiService.predictWithTrend(visitHistory)
                    : aiService.predict(aiRequest);
                    System.out.println("====== DEBUG AI RESPONSE ======");
System.out.println("history: " + aiResponse.getHistory());
System.out.println("probability: " + aiResponse.getProbability());
System.out.println("trend_message: " + aiResponse.getTrend_message());
System.out.println("================================");
        } else {
            aiResponse = aiService.predict(aiRequest);
        }

        // Build prediction object để hiển thị
        AIRiskPrediction prediction = new AIRiskPrediction();
        prediction.setRiskScore(java.math.BigDecimal.valueOf(aiResponse.getProbability() * 100));
        prediction.setRiskLevel(aiResponse.getRisk_level());
        prediction.setRiskExplanation(aiResponse.getMessage());

        // [A.4] Serialize sẵn ra JSON string để truyền qua hidden input khi
        // bác sĩ bấm "Lưu vào hồ sơ" (request POST riêng biệt tới /ai-predict/save,
        // không còn truy cập được object aiResponse gốc của request này nữa).
        String topFactorsJsonForForm = null;
        String trendInfoJsonForForm = null;
        try {
            if (aiResponse.getTop_factors() != null && !aiResponse.getTop_factors().isEmpty()) {
                topFactorsJsonForForm = objectMapper.writeValueAsString(aiResponse.getTop_factors());
            }
            if (aiResponse.getTrend() != null && !"UNKNOWN".equals(aiResponse.getTrend())) {
                Map<String, Object> trendInfo = new HashMap<>();
                trendInfo.put("trend", aiResponse.getTrend());
                trendInfo.put("trend_message", aiResponse.getTrend_message());
                trendInfo.put("history", aiResponse.getHistory());
                trendInfoJsonForForm = objectMapper.writeValueAsString(trendInfo);
            }
        } catch (Exception e) {
            log.warn("Không serialize được top_factors/trend cho form: {}", e.getMessage());
        }

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
        model.addAttribute("topFactorsJsonForForm", topFactorsJsonForForm);
        model.addAttribute("trendInfoJsonForForm", trendInfoJsonForForm);
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

    // Helper: map int → string cho RestingECG, đối xứng với restecgValue
    // trong ConsultationService (0=normal, 1=st-t abnormality, 2=lv hypertrophy)
    private String mapRestecgInt(Integer restecg) {
        if (restecg == null) {
            return null;
        }
        return switch (restecg) {
            case 0 ->
                "normal";
            case 1 ->
                "st-t abnormality";
            case 2 ->
                "lv hypertrophy";
            default ->
                null;
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
            @RequestParam(required = false) String topFactorsJson,
            @RequestParam(required = false) String trendInfoJson,
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
        // Phòng vệ tầng server: KHÔNG lưu kết quả UNKNOWN/INVALID_DATA như một
        // chẩn đoán thật, kể cả khi request được gửi thẳng (bỏ qua UI đã ẩn nút).
        // Đây là dữ liệu y tế — sai sót ở đây không thể chấp nhận được.
        if (!"HIGH".equals(riskLevel) && !"MEDIUM".equals(riskLevel) && !"LOW".equals(riskLevel)) {
            model.addAttribute("error", "Không thể lưu: AI chưa trả về kết quả hợp lệ (risk_level=" + riskLevel + "). "
                    + "Vui lòng chạy lại phân tích AI trước khi lưu hồ sơ.");
            model.addAttribute("doctor", doctor);
            if (doctor.getDoctorId() != null) {
                model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
            } else {
                model.addAttribute("patients", patientService.getAllPatients());
            }
            model.addAttribute("aiRequest", aiRequest);
            return "doctor/ai-predict";
        }
        PatientProfile patient = patientService.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        // Save records and prediction to database
        AIRiskPrediction prediction = consultationService.saveRecordAfterPrediction(
                patient, doctor, doctorNotes, treatmentPlan, riskScore, riskLevel, riskExplanation,
                aiRequest, topFactorsJson, trendInfoJson);
        model.addAttribute("doctor", doctor);
        if (doctor.getDoctorId() != null) {
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
        } else {
            model.addAttribute("patients", patientService.getAllPatients());
        }
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("prediction", prediction);
        model.addAttribute("selectedPatient", patient);
        model.addAttribute("isSaved", true); // Record is now saved
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
                    .filter(a -> a.getScheduledDate().equals(date)
                    && a.getDoctor() != null
                    && a.getDoctor().getDoctorId().equals(doctor.getDoctorId()))
                    .toList();
            doctors = List.of(doctor);
        } else {
            // Staff / Receptionists see all
            appointments = appointmentRepository.findAll().stream()
                    .filter(a -> a.getScheduledDate().equals(date))
                    .toList();
            doctors = doctorRepository.findAll();
        }

        // Calculate workload
        java.util.Map<Integer, Long> workloads = new java.util.HashMap<>();
        for (DoctorProfile doc : doctors) {
            long count = appointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doc.getDoctorId()) && !"Cancelled".equalsIgnoreCase(a.getStatus()))
                    .count();
            workloads.put(doc.getDoctorId(), count);
        }

        model.addAttribute("doctor", doctor);
        model.addAttribute("doctors", doctors);
        model.addAttribute("appointments", appointments);
        model.addAttribute("workloads", workloads);
        model.addAttribute("selectedDate", date);
        model.addAttribute("patients", doctor.getDoctorId() != null ? patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()) : patientService.getAllPatients());
        return "doctor/appointments";
    }

    @PostMapping("/appointments/save")
    public String saveAppointment(@RequestParam Integer patientId,
            @RequestParam Integer doctorId,
            @RequestParam String scheduledDate,
            @RequestParam String timeSlot,
            RedirectAttributes ra) {
        Appointment app = new Appointment();
        app.setPatient(patientService.findById(patientId).orElseThrow(() -> new RuntimeException("Patient not found")));
        DoctorProfile doctor = doctorRepository.findById(doctorId).orElseThrow(() -> new RuntimeException("Doctor not found"));
        LocalDate date = LocalDate.parse(scheduledDate);

        long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, date, "Cancelled");
        if (bookedCount >= 8) {
            ra.addFlashAttribute("error", "Bác sĩ " + doctor.getFullName() + " đã làm việc đủ 8 tiếng (8 ca khám) trong ngày này. Vui lòng chọn bác sĩ khác hoặc ngày khác.");
            return "redirect:/doctor/appointments";
        }

        app.setDoctor(doctor);
        app.setScheduledDate(date);
        // Standardize format if it is like "09:00" -> "09:00:00"
        String standardTime = timeSlot;
        if (timeSlot.length() == 5) {
            standardTime += ":00";
        }
        app.setTimeSlot(LocalTime.parse(standardTime));
        app.setStatus("Pending");
        appointmentRepository.save(app);
        ra.addFlashAttribute("success", "Đã đặt lịch hẹn thành công!");
        return "redirect:/doctor/appointments";
    }

    @PostMapping("/appointments/update-status")
    public String updateAppointmentStatus(@RequestParam Integer appointmentId,
            @RequestParam String status,
            @RequestParam(required = false) String scheduledDate,
            @RequestParam(required = false) String timeSlot,
            @RequestParam(required = false) String roomNumber,
            RedirectAttributes ra) {
        Appointment app = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        LocalDate date = (scheduledDate != null && !scheduledDate.isBlank()) ? LocalDate.parse(scheduledDate) : app.getScheduledDate();
        DoctorProfile doctor = app.getDoctor();
        if (doctor != null && !"Cancelled".equalsIgnoreCase(status)) {
            long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, date, "Cancelled");
            boolean isSameDoctorAndDate = app.getScheduledDate().equals(date) && !"Cancelled".equalsIgnoreCase(app.getStatus());
            if (bookedCount >= 8 && !isSameDoctorAndDate) {
                ra.addFlashAttribute("error", "Bác sĩ " + doctor.getFullName() + " đã làm việc đủ 8 tiếng (8 ca khám) trong ngày này. Vui lòng chọn bác sĩ khác.");
                return "redirect:/doctor/appointments";
            }
        }

        app.setStatus(status);
        if (roomNumber != null) {
            app.setRoomNumber(roomNumber);
        }
        if (scheduledDate != null && !scheduledDate.isBlank()) {
            app.setScheduledDate(date);
        }
        if (timeSlot != null && !timeSlot.isBlank()) {
            String standardTime = timeSlot;
            if (timeSlot.length() == 5) {
                standardTime += ":00";
            }
            app.setTimeSlot(LocalTime.parse(standardTime));
        }
        appointmentRepository.save(app);
        ra.addFlashAttribute("success", "Đã cập nhật trạng thái lịch hẹn!");
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

        // 1. Lưu hồ sơ khám (ConsultationRecord)
        ConsultationRecord record = new ConsultationRecord();
        record.setPatient(patient);
        record.setDoctor(null);
        record.setVisitDate(LocalDateTime.now());
        record.setConsultationNotes(
                "Nhập chỉ số sinh tồn & xét nghiệm bởi " + (staff != null ? staff.getFullName() : "Nhân viên y tế"));
        record.setTreatmentPlan("Chờ khám chuyên khoa.");
        record.setStatus("Completed");
        consultationRepository.save(record);

        // 2. Thiết lập liên kết và lưu chỉ số (HeartClinicalMetrics)
        vitals.setRecord(record);
        if (staff != null) {
            vitals.setRecordedByStaffID(staff.getStaffId());
        }
        vitals.setRecordedAt(LocalDateTime.now());
        heartClinicalMetricsRepository.save(vitals);

        ra.addFlashAttribute("success", "Đã ghi nhận chỉ số sinh tồn và kết quả xét nghiệm!");
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
    public String thresholds(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        // Truyền danh sách bệnh nhân phụ trách của bác sĩ
        List<PatientProfile> patients = patientService.getPatientsAssignedToDoctor(doctor.getDoctorId());
        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patients);
        return "doctor/thresholds";
    }

// API lấy ngưỡng hiện tại của 1 bệnh nhân (dùng cho JS fetch)
    @GetMapping("/thresholds/patient/{patientId}")
    @ResponseBody
    public java.util.Map<String, Object> getPatientThreshold(
            @PathVariable Integer patientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        PatientProfile patient = patientService.findById(patientId).orElse(null);
        if (patient == null) {
            return java.util.Map.of();
        }

        return thresholdRepository.findByPatientAndDoctor(patient, doctor)
                .map(t -> java.util.Map.<String, Object>of(
                "riskScoreThreshold", t.getRiskScoreThreshold(),
                "maxBpm", t.getMaxBpm(),
                "maxSystolicBp", t.getMaxSystolicBp(),
                "notes", t.getNotes() != null ? t.getNotes() : ""
        ))
                .orElse(java.util.Map.of(
                        "riskScoreThreshold", 40.0,
                        "maxBpm", 100,
                        "maxSystolicBp", 140,
                        "notes", ""
                ));
    }

// Lưu ngưỡng theo từng bệnh nhân
    @PostMapping("/thresholds/save-patient")
    public String savePatientThreshold(
            @RequestParam Integer patientId,
            @RequestParam Double riskScoreThreshold,
            @RequestParam Integer maxBpm,
            @RequestParam Integer maxSystolicBp,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        PatientProfile patient = patientService.findById(patientId).orElse(null);
        if (patient == null) {
            ra.addFlashAttribute("error", "Không tìm thấy bệnh nhân!");
            return "redirect:/doctor/thresholds";
        }
        PatientAlertThreshold threshold = thresholdRepository
                .findByPatientAndDoctor(patient, doctor)
                .orElse(new PatientAlertThreshold());
        threshold.setPatient(patient);
        threshold.setDoctor(doctor);
        threshold.setRiskScoreThreshold(riskScoreThreshold);
        threshold.setMaxBpm(maxBpm);
        threshold.setMaxSystolicBp(maxSystolicBp);
        threshold.setNotes(notes);
        thresholdRepository.save(threshold);
        ra.addFlashAttribute("success", "Đã lưu ngưỡng cho bệnh nhân!");
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
                    .filter(a -> a.getPatient().getPatientId().equals(id)
                    && a.getDoctor() != null
                    && a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                    && "InProgress".equals(a.getStatus()))
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
}