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
import com.cardio.model.DoctorProfile;
import java.util.Map;
import java.util.HashMap;
import com.cardio.model.PatientAlertThreshold;
import com.cardio.repository.PatientAlertThresholdRepository;
import com.cardio.repository.LabRequestRepository;

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
    private final LabRequestRepository labRequestRepository;

    // Helper lấy doctor đang đăng nhập
    private DoctorProfile getCurrentDoctor(UserDetails userDetails) {
        String username = userDetails.getUsername();
        return doctorRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Doctor profile not found for username: " + username));
    }

    // ── DASHBOARD ──────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {
        try {
            DoctorProfile doctor = getCurrentDoctor(userDetails);

            List<PatientProfile> patients = List.of();
            try {
                patients = patientService.getPatientsAssignedToDoctor(doctor.getDoctorId());
            } catch (Exception ex) {
                System.err.println("Error querying patients: " + ex.getMessage());
            }

            List<AIRiskPrediction> alerts = List.of();
            try {
                if (doctor.getDoctorId() != null) {
                    alerts = consultationService.getAlertsByDoctor(doctor.getDoctorId());
                } else {
                    alerts = consultationService.getUnhandledHighAlerts();
                }
            } catch (Exception ex) {
                System.err.println("Error querying alerts: " + ex.getMessage());
            }
            long highAlerts = alerts.stream().filter(a -> a != null && "HIGH".equals(a.getRiskLevel())).count();

            // Truy vấn Lịch hẹn an toàn cho bác sĩ
            long totalApps = 0;
            List<Appointment> appsList = List.of();
            try {
                LocalDate date = LocalDate.now();
                appsList = appointmentRepository.findByDoctorAndScheduledDate(doctor, date);
                totalApps = appsList.size();
            } catch (Exception ex) {
                System.err.println("Error querying appointments: " + ex.getMessage());
            }

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

            List<PatientProfile> sortedPatients = patients.stream()
                    .sorted((p1, p2) -> {
                        int s1 = "HIGH".equals(patientRiskMap.get(p1.getPatientId())) ? 2
                                : "MEDIUM".equals(patientRiskMap.get(p1.getPatientId())) ? 1 : 0;
                        int s2 = "HIGH".equals(patientRiskMap.get(p2.getPatientId())) ? 2
                                : "MEDIUM".equals(patientRiskMap.get(p2.getPatientId())) ? 1 : 0;
                        return Integer.compare(s2, s1);
                    })
                    .collect(java.util.stream.Collectors.toList());

            // ═══════════════════════════════════════════════════════════
            // [MỚI] Bộ lọc ngày + Timeline hoạt động — mặc định 7 ngày gần
            // nhất nếu bác sĩ chưa chọn khoảng ngày cụ thể.
            // ═══════════════════════════════════════════════════════════
            LocalDate toDate = (dateTo != null && !dateTo.isBlank()) ? LocalDate.parse(dateTo) : LocalDate.now();
            LocalDate fromDate = (dateFrom != null && !dateFrom.isBlank()) ? LocalDate.parse(dateFrom) : toDate.minusDays(7);

            List<java.util.Map<String, Object>> activityTimeline = new java.util.ArrayList<>();

            // Nguồn 1: Cảnh báo AI mới (dựa theo ngày khám gắn với prediction)
            alerts.stream()
                    .filter(a -> a != null && a.getRecord() != null && a.getRecord().getVisitDate() != null)
                    .filter(a -> {
                        LocalDate d = a.getRecord().getVisitDate().toLocalDate();
                        return !d.isBefore(fromDate) && !d.isAfter(toDate);
                    })
                    .forEach(a -> {
                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("type", "alert");
                        item.put("time", a.getRecord().getVisitDate());
                        item.put("title", "Cảnh báo AI: "
                                + (a.getRecord().getPatient() != null ? a.getRecord().getPatient().getFullName() : "Không rõ"));
                        item.put("detail", a.getRiskExplanation());
                        item.put("riskLevel", a.getRiskLevel());
                        item.put("link", a.getRecord().getPatient() != null
                                ? "/doctor/patients/" + a.getRecord().getPatient().getPatientId() : "/doctor/alerts");
                        activityTimeline.add(item);
                    });

            // Nguồn 2: Lịch hẹn mới đặt (dựa theo thời điểm ĐẶT lịch, không phải ngày khám)
            try {
                LocalDateTime startDateTime = fromDate.atStartOfDay();
                LocalDateTime endDateTime = toDate.atTime(LocalTime.MAX);
                List<Appointment> timelineApps;
                if (doctor.getDoctorId() == null) {
                    timelineApps = appointmentRepository.findAll().stream()
                            .filter(a -> a.getRequestTime() != null)
                            .filter(a -> !a.getRequestTime().isBefore(startDateTime) && !a.getRequestTime().isAfter(endDateTime))
                            .toList();
                } else {
                    timelineApps = appointmentRepository.findByDoctorAndRequestTimeBetween(doctor, startDateTime, endDateTime);
                }
                timelineApps.forEach(a -> {
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("type", "appointment");
                    item.put("time", a.getRequestTime());
                    item.put("title", "Lịch hẹn mới: "
                            + (a.getPatient() != null ? a.getPatient().getFullName() : "Không rõ"));
                    item.put("detail", "Ngày khám: " + a.getScheduledDate());
                    item.put("link", "/doctor/appointments");
                    activityTimeline.add(item);
                });
            } catch (Exception ex) {
                System.err.println("Error querying timeline appointments: " + ex.getMessage());
            }

            activityTimeline.sort((x, y)
                    -> ((LocalDateTime) y.get("time")).compareTo((LocalDateTime) x.get("time")));

            model.addAttribute("dateFrom", fromDate.toString());
            model.addAttribute("dateTo", toDate.toString());
            model.addAttribute("activityTimeline", activityTimeline);

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

        List<DoctorProfile> specialists = doctorRepository.findAll().stream()
                .filter(d -> "Chuyên khoa".equalsIgnoreCase(d.getSpecialty()))
                .toList();

        List<LabRequest> labRequests = labRequestRepository.findByPatientOrderByCreatedAtDesc(patient);
        // [MỚI] Chuẩn bị dữ liệu xu hướng nguy cơ qua TẤT CẢ lần khám đã có
// kết quả AI (khác biểu đồ ở ai-predict.html chỉ hiện được lần vừa chạy
// — đây tổng hợp toàn bộ lịch sử để bác sĩ thấy xu hướng dài hạn).
        List<java.util.Map<String, Object>> trendPoints = records.stream()
                .filter(r -> r.getAiRiskPrediction() != null && r.getVisitDate() != null)
                .sorted(java.util.Comparator.comparing(ConsultationRecord::getVisitDate))
                .map(r -> {
                    java.util.Map<String, Object> point = new java.util.HashMap<>();
                    point.put("date", r.getVisitDate().toLocalDate().toString());
                    point.put("riskScore", r.getAiRiskPrediction().getRiskScore());
                    point.put("riskLevel", r.getAiRiskPrediction().getRiskLevel());
                    point.put("recordId", r.getRecordId());
                    return point;
                })
                .collect(java.util.stream.Collectors.toList());

        String trendPointsJson = "[]";
        try {
            trendPointsJson = objectMapper.writeValueAsString(trendPoints);
        } catch (Exception e) {
            log.warn("Không serialize được trendPoints cho patient-detail: {}", e.getMessage());
        }
        model.addAttribute("trendPointsJson", trendPointsJson);
        model.addAttribute("hasTrendData", trendPoints.size() >= 2);
        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        model.addAttribute("recordDiagnosesMap", recordDiagnosesMap);

        // [FIX] Truyền HeartClinicalMetrics của từng record để form sửa có thể
        // hiển thị giá trị hiện tại (huyết áp, cholesterol...) thay vì ô trống
        java.util.Map<Integer, com.cardio.model.HeartClinicalMetrics> recordMetricsMap
                = new java.util.HashMap<>();
        records.forEach(r -> consultationService.getMetricsByRecord(r.getRecordId())
                .ifPresent(m -> recordMetricsMap.put(r.getRecordId(), m)));
        model.addAttribute("recordMetricsMap", recordMetricsMap);

        model.addAttribute("highAlertCount", 0L);
        model.addAttribute("hasActiveAppointment", hasActiveAppointment);
        model.addAttribute("specialists", specialists);
        model.addAttribute("labRequests", labRequests);
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
        Boolean fbs = fastingBloodSugar != null
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
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        List<AIRiskPrediction> alerts;

        if (doctor.getDoctorId() != null) {
            alerts = consultationService.getAlertsByDoctorSorted(doctor.getDoctorId());
        } else {
            alerts = consultationService.getUnhandledHighAlerts();
        }

        // Truyền urgencyScore vào model để hiển thị badge "🔥 Khẩn cấp"
        Map<Integer, Double> urgencyMap = new HashMap<>();
        alerts.forEach(a -> urgencyMap.put(a.getPredictionId(),
                consultationService.calcUrgencyScore(a)));

        // [FIX] Đổi từ boolean đơn thuần sang danh sách LÝ DO CỤ THỂ (nhịp
        // tim/huyết áp/cholesterol/nguy cơ AI vượt ngưỡng nào) — trước đây
        // chỉ có badge "⚡ Vượt ngưỡng riêng" chung chung, không rõ lý do gì.
        Map<Integer, List<String>> thresholdViolationsMap = new HashMap<>();
        alerts.forEach(a -> thresholdViolationsMap.put(a.getPredictionId(),
                consultationService.checkPersonalThreshold(a).getViolations()));

        // Filter counts (chỉ đếm chưa xử lý)
        List<AIRiskPrediction> unhandled = alerts.stream()
                .filter(a -> !a.getIsAlertSent()).toList();

        model.addAttribute("doctor", doctor);
        model.addAttribute("alerts", alerts);
        model.addAttribute("urgencyMap", urgencyMap);
        model.addAttribute("thresholdViolationsMap", thresholdViolationsMap);
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
            model.addAttribute("modelInfo", aiService.getModelInfo());
            model.addAttribute("error", "Bạn không có quyền chạy dự đoán AI cho bệnh nhân này!");
            model.addAttribute("doctor", doctor);
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
            model.addAttribute("aiRequest", aiRequest);
            return "doctor/ai-predict";
        }
        // [MỚI] Luôn chỉ chạy V1 ở bước đầu — trả lời nhanh "có/không bệnh".
        // Model V2 (mức độ nặng) CHỈ chạy khi bác sĩ chủ động bấm "Chẩn đoán
        // mức độ" ở bước sau (xem runAIAnalyzeV2 bên dưới).
        return doAnalyze(aiRequest, patientId, doctor, true, false, userDetails, model);
    }

// [MỚI] Bước 2 — bác sĩ đã xem kết quả V1, bấm "Chẩn đoán mức độ" để chạy
// thêm Model V2. Chạy LẠI cả V1 (để vẫn hiển thị đúng phần giải thích/badge
// V1 trên cùng trang) VÀ V2, thay vì chỉ chạy riêng V2 — tránh phải truyền
// tay toàn bộ object AIResponse phức tạp qua hidden input.
    @PostMapping("/ai-predict/analyze-v2")
    public String runAIAnalyzeV2(@ModelAttribute AIRequest aiRequest,
            @RequestParam Integer patientId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        if (doctor.getDoctorId() != null && !patientService.isPatientAssignedToDoctor(patientId, doctor.getDoctorId())) {
            model.addAttribute("modelInfo", aiService.getModelInfo());
            model.addAttribute("error", "Bạn không có quyền chạy dự đoán AI cho bệnh nhân này!");
            model.addAttribute("doctor", doctor);
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
            model.addAttribute("aiRequest", aiRequest);
            return "doctor/ai-predict";
        }
        return doAnalyze(aiRequest, patientId, doctor, true, true, userDetails, model);
    }

// [MỚI] Logic dùng chung cho cả 2 endpoint trên — tránh lặp code, tránh
// 2 nơi có thể lệch nhau theo thời gian khi sửa sau này.
    private String doAnalyze(AIRequest aiRequest, Integer patientId, DoctorProfile doctor,
            boolean runV1, boolean runV2, UserDetails userDetails, Model model) {
        model.addAttribute("modelInfo", aiService.getModelInfo());

        PatientProfile patient = patientService.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        List<ConsultationRecord> pastRecords = consultationService.getByPatientChronological(patient).stream()
                .filter(r -> r.getAiRiskPrediction() != null)
                .collect(java.util.stream.Collectors.toList());

        com.cardio.dto.AIResponse aiResponse = null;
        com.cardio.dto.AIResponseV2 aiResponseV2 = null;

        if (pastRecords.size() >= 1) {
            List<AIRequest> visitHistory = pastRecords.stream()
                    .filter(r -> r.getClinicalMetrics() != null)
                    .filter(r -> {
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

            visitHistory.add(aiRequest);

            if (runV1) {
                aiResponse = visitHistory.size() >= 2
                        ? aiService.predictWithTrend(visitHistory)
                        : aiService.predict(aiRequest);
            }
            if (runV2) {
                aiResponseV2 = visitHistory.size() >= 2
                        ? aiService.predictV2WithTrend(visitHistory)
                        : aiService.predictV2(aiRequest);
            }
        } else {
            if (runV1) {
                aiResponse = aiService.predict(aiRequest);
            }
            if (runV2) {
                aiResponseV2 = aiService.predictV2(aiRequest);
            }
        }

        AIRiskPrediction prediction = null;
        String topFactorsJsonForForm = null;
        String trendInfoJsonForForm = null;

        if (aiResponse != null) {
            prediction = new AIRiskPrediction();
            prediction.setRiskScore(java.math.BigDecimal.valueOf(aiResponse.getProbability() * 100));
            prediction.setRiskLevel(aiResponse.getRisk_level());
            prediction.setRiskExplanation(aiResponse.getMessage());

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

            auditLogService.logAIPrediction(
                    userDetails.getUsername(), patientId,
                    aiResponse.getRisk_level(),
                    String.valueOf(Math.round(aiResponse.getProbability() * 100))
            );
        }

        model.addAttribute("doctor", doctor);
        if (doctor.getDoctorId() != null) {
            model.addAttribute("patients", patientService.getPatientsAssignedToDoctor(doctor.getDoctorId()));
        } else {
            model.addAttribute("patients", patientService.getAllPatients());
        }
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("prediction", prediction);
        model.addAttribute("aiResponse", aiResponse);
        model.addAttribute("aiResponseV2", aiResponseV2);
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
                    .sorted((a1, a2) -> {
                        LocalTime t1 = a1.getTimeSlot();
                        LocalTime t2 = a2.getTimeSlot();
                        if (t1 != null && t2 != null) {
                            return t1.compareTo(t2);
                        }
                        if (t1 != null) {
                            return -1;
                        }
                        if (t2 != null) {
                            return 1;
                        }

                        LocalDateTime r1 = a1.getRequestTime();
                        LocalDateTime r2 = a2.getRequestTime();
                        if (r1 != null && r2 != null) {
                            return r1.compareTo(r2);
                        }
                        if (r1 != null) {
                            return -1;
                        }
                        if (r2 != null) {
                            return 1;
                        }

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
                        if (t1 != null) {
                            return -1;
                        }
                        if (t2 != null) {
                            return 1;
                        }

                        LocalDateTime r1 = a1.getRequestTime();
                        LocalDateTime r2 = a2.getRequestTime();
                        if (r1 != null && r2 != null) {
                            return r1.compareTo(r2);
                        }
                        if (r1 != null) {
                            return -1;
                        }
                        if (r2 != null) {
                            return 1;
                        }

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
        if (app == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

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
                boolean isSameDoctorAndDate = app.getDoctor() != null
                        && app.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                        && app.getScheduledDate().equals(targetDate)
                        && !"Cancelled".equalsIgnoreCase(app.getStatus());

                long bookedCount = appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate, "Cancelled");
                if (bookedCount >= 8 && !isSameDoctorAndDate) {
                    while (true) {
                        targetDate = targetDate.plusDays(1);
                        boolean isSameOnNext = app.getDoctor() != null
                                && app.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                                && app.getScheduledDate().equals(targetDate)
                                && !"Cancelled".equalsIgnoreCase(app.getStatus());
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
            LocalTime startVal = app.getTimeSlot();
            if (timeSlot != null && !timeSlot.isBlank()) {
                startVal = LocalTime.parse(timeSlot);
            } else if ("CheckedIn".equalsIgnoreCase(status) && startVal == null) {
                startVal = LocalTime.now();
            } else if (!"CheckedIn".equalsIgnoreCase(status) && !"InProgress".equalsIgnoreCase(status) && !"Completed".equalsIgnoreCase(status)) {
                startVal = null; // Reset arrival time if moved back
            }

            // Handle Completed end time
            LocalTime endVal = app.getEndTime();
            if (endTime != null && !endTime.isBlank()) {
                endVal = LocalTime.parse(endTime);
            } else if ("Completed".equalsIgnoreCase(status) && endVal == null) {
                endVal = LocalTime.now();
            } else if (!"Completed".equalsIgnoreCase(status)) {
                endVal = null;
            }

            if (startVal != null && endVal != null && endVal.isBefore(startVal)) {
                throw new RuntimeException("Giờ ra (giờ về) phải sau giờ vào!");
            }

            app.setTimeSlot(startVal);
            app.setEndTime(endVal);

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
            @RequestParam(required = false) Integer requestId,
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
        model.addAttribute("requestId", requestId);
        return "doctor/vitals-form";
    }

    @PostMapping("/patients/{id}/vitals/save")
    public String saveVitals(@PathVariable Integer id,
            @ModelAttribute HeartClinicalMetrics vitals,
            @RequestParam(required = false) String consultationNotes,
            @RequestParam(required = false) String treatmentPlan,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer requestId,
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
        record.setConsultationNotes(consultationNotes != null && !consultationNotes.trim().isEmpty() ? consultationNotes
                : ("Nhập chỉ số sinh tồn & xét nghiệm bởi " + (staff != null ? staff.getFullName() : (doctor != null ? doctor.getFullName() : "Nhân viên y tế"))));
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

        // 3. Hoàn thành yêu cầu xét nghiệm nếu có
        if (requestId != null) {
            consultationService.completeLabRequest(requestId, vitals, consultationNotes);
        }

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
    public String thresholds(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        List<PatientProfile> patients = patientService.getPatientsAssignedToDoctor(doctor.getDoctorId());
        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patients);

        // [FIX] Đánh dấu bệnh nhân nào ĐÃ cấu hình ngưỡng riêng, bệnh nhân
        // nào CHƯA — để giao diện 2 cột hiển thị rõ (yêu cầu review: "nhìn
        // 1 phát là thấy" — bác sĩ không phải bấm từng người mới biết).
        Map<Integer, Boolean> configuredMap = new HashMap<>();
        patients.forEach(p -> configuredMap.put(p.getPatientId(),
                thresholdRepository.findByPatientAndDoctor(p, doctor).isPresent()));
        model.addAttribute("configuredMap", configuredMap);

        return "doctor/thresholds";
    }

    /**
     * [FIX v2] Gợi ý ngưỡng theo TUỔI/GIỚI TÍNH — viết lại sau khi tra cứu lại
     * y văn hiện hành, vì bản đầu tự đặt số theo cảm tính không có căn cứ.
     *
     * ── HUYẾT ÁP & CHOLESTEROL: KHÔNG đổi số theo tuổi/giới tính ──────────
     * AHA/ACC (kể cả bản cập nhật 08/2025) dùng CÙNG 1 bộ mốc phân loại cho mọi
     * lứa tuổi (Normal <120, Stage 1: 130-139, Stage 2: ≥140). Điều khác biệt
     * theo tuổi là MỨC ĐỘ RỦI RO TƯƠNG ĐỐI của cùng 1 chỉ số (nghiên cứu
     * PMC6912685: hazard ratio của Stage 1 hypertension là 1.54 ở tuổi 35-49
     * nhưng chỉ 1.11 ở tuổi 80-94 — cùng chỉ số, rủi ro khác nhau). Vì vậy
     * KHÔNG bịa ra số khác nhau — giữ nguyên mốc chuẩn, chỉ đổi mức độ khẩn cấp
     * trong thông báo (xem checkPersonalThreshold ở ConsultationService). Tương
     * tự với cholesterol (200/240 mg/dL theo NCEP/AHA) — không có mốc số khác
     * biệt chính thức theo giới tính, chỉ khác về DIỄN GIẢI rủi ro (phụ nữ
     * trước mãn kinh có yếu tố bảo vệ hormone, nhưng vẫn dùng cùng mốc chẩn
     * đoán).
     *
     * ── NHỊP TIM: CÓ công thức khoa học khác theo tuổi VÀ giới tính ───────
     * Lưu ý quan trọng: trường "Nhịp tim tối đa" (MaxHeartRate/thalch) trong hệ
     * thống này là NHỊP TIM ĐẠT ĐƯỢC KHI GẮNG SỨC (bài test gắng sức), KHÔNG
     * PHẢI nhịp tim lúc nghỉ. Với chỉ số này, chiều nguy hiểm NGƯỢC LẠI trực
     * giác thông thường: nhịp tim THẤP so với dự kiến theo tuổi mới là dấu hiệu
     * đáng lo (chronotropic incompetence — tim đáp ứng kém khi gắng sức), còn
     * đạt gần mức tối đa theo tuổi là dấu hiệu TỐT.
     *
     * Công thức dùng (đã kiểm chứng qua nghiên cứu, chính xác hơn 220-tuổi cũ,
     * đặc biệt với người lớn tuổi và nữ giới): - Nam / không rõ giới tính:
     * Tanaka (Tanaka, Monahan, Seals 2001, meta-analysis 18.712 người): HRmax =
     * 208 − 0,7 × tuổi - Nữ: Gulati (Gulati et al. 2010, St. James Women Take
     * Heart Project, 5.437 phụ nữ): HRmax = 206 − 0,88 × tuổi — công thức chung
     * (220-tuổi hay Tanaka) đánh giá QUÁ CAO nhịp tim tối đa thật của nữ giới.
     *
     * minBpm gợi ý = 70% HRmax dự kiến (dưới mức này nghi ngờ chronotropic
     * incompetence), maxBpm gợi ý = 105% HRmax dự kiến (vượt quá nhiều so với
     * dự kiến theo tuổi thường là sai số đo hoặc rối loạn nhịp).
     */
    private Map<String, Object> computeAgeGenderDefaults(PatientProfile patient) {
        Map<String, Object> defaults = new HashMap<>();
        Integer age = null;
        if (patient.getDob() != null) {
            age = java.time.Period.between(patient.getDob(), java.time.LocalDate.now()).getYears();
        }
        String gender = patient.getGender();
        boolean isFemale = "Female".equalsIgnoreCase(gender) || "Nữ".equalsIgnoreCase(gender);

        // ── Huyết áp & Cholesterol: mốc CHUẨN, không đổi theo tuổi/giới tính ──
        double riskScoreThreshold = 40.0;
        int maxSystolicBp = 140, minSystolicBp = 90;
        int maxCholesterol = 240;

        // ── Nhịp tim: dùng công thức Tanaka/Gulati theo tuổi + giới tính ──
        int maxBpm = 100, minBpm = 50; // fallback nếu thiếu tuổi
        Double predictedHRmax = null;
        if (age != null) {
            predictedHRmax = isFemale
                    ? (206 - 0.88 * age) // Gulati — riêng cho nữ
                    : (208 - 0.7 * age);   // Tanaka — nam/không rõ giới tính
            minBpm = (int) Math.round(predictedHRmax * 0.70);
            maxBpm = (int) Math.round(predictedHRmax * 1.05);
        }

        // Nguy cơ AI: tuổi càng cao, nguy cơ nền càng cao — bác sĩ có thể
        // muốn ngưỡng nhạy hơn (thấp hơn) để bắt sớm dấu hiệu bất thường ở
        // nhóm tuổi vốn đã nguy cơ cao. Đây là gợi ý VẬN HÀNH (workflow),
        // không phải trích dẫn từ 1 guideline cụ thể — bác sĩ toàn quyền
        // điều chỉnh theo đánh giá riêng.
        if (age != null && age >= 60) {
            riskScoreThreshold = 35.0;
        }

        defaults.put("riskScoreThreshold", riskScoreThreshold);
        defaults.put("maxBpm", maxBpm);
        defaults.put("minBpm", minBpm);
        defaults.put("maxSystolicBp", maxSystolicBp);
        defaults.put("minSystolicBp", minSystolicBp);
        defaults.put("maxCholesterol", maxCholesterol);
        defaults.put("patientAge", age);
        defaults.put("patientGender", gender);
        defaults.put("predictedHRmax", predictedHRmax != null ? Math.round(predictedHRmax) : null);
        defaults.put("hrFormula", isFemale ? "Gulati (206 − 0,88×tuổi)" : "Tanaka (208 − 0,7×tuổi)");
        return defaults;
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
                .map(t -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("riskScoreThreshold", t.getRiskScoreThreshold());
                    result.put("maxBpm", t.getMaxBpm());
                    result.put("minBpm", t.getMinBpm());
                    result.put("maxSystolicBp", t.getMaxSystolicBp());
                    result.put("minSystolicBp", t.getMinSystolicBp());
                    result.put("maxCholesterol", t.getMaxCholesterol());
                    result.put("notes", t.getNotes() != null ? t.getNotes() : "");
                    result.put("isConfigured", true);
                    return result;
                })
                // [FIX] Chưa cấu hình → trả về gợi ý theo TUỔI/GIỚI TÍNH thay vì
                // 1 bộ số cố định giống nhau cho mọi bệnh nhân.
                .orElseGet(() -> {
                    Map<String, Object> defaults = computeAgeGenderDefaults(patient);
                    defaults.put("notes", "");
                    defaults.put("isConfigured", false);
                    return defaults;
                });
    }

// [FIX v3] Method này thay thế savePatientThreshold() cũ trong DoctorController.java
// Copy đoạn dưới đây, dán đè lên method savePatientThreshold() hiện tại
// (giữ nguyên vị trí, chỉ thay nội dung method).
// Lưu ngưỡng theo từng bệnh nhân
    @PostMapping("/thresholds/save-patient")
    public String savePatientThreshold(
            @RequestParam Integer patientId,
            @RequestParam Double riskScoreThreshold,
            @RequestParam Integer maxBpm,
            @RequestParam Integer minBpm,
            @RequestParam Integer maxSystolicBp,
            @RequestParam Integer minSystolicBp,
            @RequestParam Integer maxCholesterol,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        PatientProfile patient = patientService.findById(patientId).orElse(null);
        if (patient == null) {
            ra.addFlashAttribute("error", "Không tìm thấy bệnh nhân!");
            return "redirect:/doctor/thresholds";
        }

        // [FIX v3] Validate min < max — tránh logic OR ở checkPersonalThreshold()
        // luôn báo "vượt ngưỡng" (vì mọi giá trị đều đồng thời > max và < min).
        // [FIX v3] Redirect kèm ?patientId= để trang thresholds.html tự động
        // chọn lại đúng bệnh nhân đang sửa dở, thay vì mất hết context và bác
        // sĩ phải chọn lại + nhập lại từ đầu.
        String errorRedirect = "redirect:/doctor/thresholds?patientId=" + patientId;

        if (minBpm != null && maxBpm != null && minBpm >= maxBpm) {
            ra.addFlashAttribute("error", "Nhịp tim tối thiểu phải nhỏ hơn nhịp tim tối đa!");
            return errorRedirect;
        }
        if (minSystolicBp != null && maxSystolicBp != null && minSystolicBp >= maxSystolicBp) {
            ra.addFlashAttribute("error", "Huyết áp tối thiểu phải nhỏ hơn huyết áp tối đa!");
            return errorRedirect;
        }

        // [FIX v3] Validate phạm vi hợp lý phía SERVER — HTML min/max chỉ chặn
        // ở trình duyệt, ai gửi request trực tiếp (Postman/DevTools) có thể
        // bỏ qua hoàn toàn. Đây là dữ liệu y tế — không thể chỉ tin tưởng
        // validation phía client.
        if (riskScoreThreshold == null || riskScoreThreshold < 0 || riskScoreThreshold > 100) {
            ra.addFlashAttribute("error", "Ngưỡng nguy cơ AI phải trong khoảng 0-100%!");
            return errorRedirect;
        }
        if (minBpm != null && (minBpm < 20 || minBpm > 200)) {
            ra.addFlashAttribute("error", "Nhịp tim tối thiểu phải trong khoảng 20-200 bpm!");
            return errorRedirect;
        }
        if (maxBpm != null && (maxBpm < 20 || maxBpm > 250)) {
            ra.addFlashAttribute("error", "Nhịp tim tối đa phải trong khoảng 20-250 bpm!");
            return errorRedirect;
        }
        if (minSystolicBp != null && (minSystolicBp < 40 || minSystolicBp > 200)) {
            ra.addFlashAttribute("error", "Huyết áp tối thiểu phải trong khoảng 40-200 mmHg!");
            return errorRedirect;
        }
        if (maxSystolicBp != null && (maxSystolicBp < 60 || maxSystolicBp > 260)) {
            ra.addFlashAttribute("error", "Huyết áp tối đa phải trong khoảng 60-260 mmHg!");
            return errorRedirect;
        }
        if (maxCholesterol != null && (maxCholesterol < 100 || maxCholesterol > 500)) {
            ra.addFlashAttribute("error", "Ngưỡng cholesterol tối đa phải trong khoảng 100-500 mg/dL!");
            return errorRedirect;
        }

        PatientAlertThreshold threshold = thresholdRepository
                .findByPatientAndDoctor(patient, doctor)
                .orElse(new PatientAlertThreshold());
        threshold.setPatient(patient);
        threshold.setDoctor(doctor);
        threshold.setRiskScoreThreshold(riskScoreThreshold);
        threshold.setMaxBpm(maxBpm);
        threshold.setMinBpm(minBpm);
        threshold.setMaxSystolicBp(maxSystolicBp);
        threshold.setMinSystolicBp(minSystolicBp);
        threshold.setMaxCholesterol(maxCholesterol);
        threshold.setNotes(notes);
        thresholdRepository.save(threshold);
        ra.addFlashAttribute("success", "Đã lưu ngưỡng cho bệnh nhân " + patient.getFullName() + "!");
        // [FIX v3] Giữ nguyên bệnh nhân đang xem sau khi lưu thành công,
        // thay vì quay về trang trống — bác sĩ thường muốn xem lại/chỉnh
        // tiếp ngay sau khi lưu.
        return "redirect:/doctor/thresholds?patientId=" + patientId;
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
                ra.addFlashAttribute("error", "Bạn đang có một ca khám chưa hoàn thành. Vui lòng hoàn thành ca khám hiện tại trước khi bắt đầu tiếp nhận bệnh nhân tiếp theo!");
                return "redirect:/doctor/appointments";
            }
        } else if (app.getDoctor() != null) {
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

    private List<Appointment> sortAppointmentsForQueue(List<Appointment> list) {
        if (list == null) {
            return java.util.Collections.emptyList();
        }
        return list.stream().sorted((a1, a2) -> {
            // Priority 1: Status InProgress
            int p1 = "InProgress".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int p2 = "InProgress".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }

            // Priority 2: Status CheckedIn (FIFO by arrival time / timeSlot)
            int c1 = "CheckedIn".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int c2 = "CheckedIn".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (c1 != c2) {
                return Integer.compare(c1, c2);
            }
            if (c1 == 0) {
                if (a1.getTimeSlot() != null && a2.getTimeSlot() != null) {
                    return a1.getTimeSlot().compareTo(a2.getTimeSlot());
                }
                if (a1.getTimeSlot() != null) {
                    return -1;
                }
                if (a2.getTimeSlot() != null) {
                    return 1;
                }
            }

            // Priority 3: Status Confirmed
            int f1 = "Confirmed".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int f2 = "Confirmed".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (f1 != f2) {
                return Integer.compare(f1, f2);
            }

            // Priority 4: Status Pending
            int d1 = "Pending".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int d2 = "Pending".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (d1 != d2) {
                return Integer.compare(d1, d2);
            }

            // Priority 5: Completed
            int m1 = "Completed".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int m2 = "Completed".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (m1 != m2) {
                return Integer.compare(m1, m2);
            }

            // Priority 6: Cancelled
            int n1 = "Cancelled".equalsIgnoreCase(a1.getStatus()) ? 0 : 1;
            int n2 = "Cancelled".equalsIgnoreCase(a2.getStatus()) ? 0 : 1;
            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }

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
    }

    @PostMapping("/patients/{id}/transfer")
    public String transferPatient(@PathVariable Integer id,
            @RequestParam("targetDoctorId") Integer targetDoctorId,
            @RequestParam("notes") String notes,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        try {
            PatientProfile patient = patientService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Patient not found"));
            DoctorProfile targetDoctor = doctorRepository.findById(targetDoctorId)
                    .orElseThrow(() -> new RuntimeException("Target doctor not found"));

            consultationService.createReferral(patient, targetDoctor, notes);

            // Log action
            auditLogService.log(userDetails.getUsername(), "REFER_PATIENT",
                    "Chuyển bệnh nhân PatientID=" + id + " sang BS Chuyên khoa " + targetDoctor.getFullName() + ". Lý do: " + notes);

            ra.addFlashAttribute("success", "Đã chuyển bệnh nhân sang chuyên khoa và tạo lịch hẹn thành công!");
        } catch (Exception e) {
            log.error("Error transferring patient: ", e);
            ra.addFlashAttribute("error", "Lỗi chuyển chuyên khoa: " + e.getMessage());
        }
        return "redirect:/doctor/patients/" + id;
    }

    @PostMapping("/patients/{id}/lab-request")
    public String requestLabTest(@PathVariable Integer id,
            @RequestParam("requestNotes") String requestNotes,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        try {
            PatientProfile patient = patientService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Patient not found"));
            DoctorProfile doctor = getCurrentDoctor(userDetails);

            consultationService.createLabRequest(patient, doctor, requestNotes);

            // Log action
            auditLogService.log(userDetails.getUsername(), "CREATE_LAB_REQUEST",
                    "Yêu cầu xét nghiệm cho PatientID=" + id + ". Ghi chú: " + requestNotes);

            ra.addFlashAttribute("success", "Đã gửi yêu cầu xét nghiệm cho điều dưỡng!");
        } catch (Exception e) {
            log.error("Error creating lab request: ", e);
            ra.addFlashAttribute("error", "Lỗi tạo yêu cầu xét nghiệm: " + e.getMessage());
        }
        return "redirect:/doctor/patients/" + id;
    }

    @GetMapping("/lab-requests")
    public String listLabRequests(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        String username = userDetails.getUsername();
        boolean isStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_RECEPTIONIST"));

        List<LabRequest> requests;
        if (isStaff) {
            // Staff sees all requests
            requests = labRequestRepository.findAllByOrderByCreatedAtDesc();
        } else {
            // Doctors see requests they created
            DoctorProfile doctor = getCurrentDoctor(userDetails);
            requests = labRequestRepository.findByDoctorOrderByCreatedAtDesc(doctor);
        }

        model.addAttribute("requests", requests);
        model.addAttribute("isStaff", isStaff);
        return "doctor/lab-requests";
    }
}
