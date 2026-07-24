package com.cardio.controller;

import com.cardio.model.*;
import com.cardio.repository.*;
import com.cardio.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@Slf4j
public class StaffController {

    private final StaffRepository staffRepository;
    private final PatientService patientService;
    private final ConsultationService consultationService;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final HeartClinicalMetricsRepository heartClinicalMetricsRepository;
    private final AuditLogService auditLogService;
    private final LabRequestRepository labRequestRepository;
    private final DoctorRepository doctorRepository;
    private final PatientAlertThresholdRepository thresholdRepository;

    private StaffProfile getCurrentStaff(UserDetails userDetails) {
        String username = userDetails.getUsername();
        return staffRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Staff profile not found for username: " + username));
    }

    // ── DASHBOARD ──────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        try {
            StaffProfile staff = getCurrentStaff(userDetails);
            model.addAttribute("staff", staff);

            Pageable pageable = PageRequest.of(page, 5); // 5 bệnh nhân trên một trang dashboard
            Page<PatientProfile> patientPage = patientService.getAllPatients(pageable);
            List<AIRiskPrediction> alerts = consultationService.getUnhandledHighAlerts();
            long highAlerts = alerts.stream().filter(a -> a != null && "HIGH".equals(a.getRiskLevel())).count();

            long totalApps = 0;
            List<Appointment> appsList = List.of();
            try {
                totalApps = appointmentRepository.count();
                appsList = appointmentRepository.findAllByOrderByScheduledDateDescTimeSlotAsc().stream().limit(5)
                        .toList();
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

            model.addAttribute("patients", patientPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", patientPage.getTotalPages());
            model.addAttribute("totalItems", patientPage.getTotalElements());
            model.addAttribute("alerts", alerts);
            model.addAttribute("highAlertCount", highAlerts);
            model.addAttribute("totalAppointments", totalApps);
            model.addAttribute("recentAppointments", appsList);
            model.addAttribute("patientRiskMap", patientRiskMap);

            return "staff/dashboard";
        } catch (Exception e) {
            log.error("Error loading staff dashboard: ", e);
            return "redirect:/login";
        }
    }

    // ── DANH SÁCH BỆNH NHÂN ─────────────────────────────
    @GetMapping("/patients")
    public String patients(@RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        Page<PatientProfile> patientPage;
        if (search != null && !search.trim().isEmpty()) {
            patientPage = patientService.searchByName(search.trim(), pageable);
        } else {
            patientPage = patientService.getAllPatients(pageable);
        }

        List<AIRiskPrediction> alerts = consultationService.getUnhandledHighAlerts();
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

        model.addAttribute("staff", staff);
        model.addAttribute("patients", patientPage.getContent());
        model.addAttribute("patientRiskMap", patientRiskMap);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", patientPage.getTotalPages());
        model.addAttribute("totalItems", patientPage.getTotalElements());
        model.addAttribute("search", search);
        return "staff/patients";
    }

    // ── THÊM BỆNH NHÂN MỚI ──────────────────────────────
    @GetMapping("/patients/new")
    public String newPatientForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        model.addAttribute("staff", staff);
        model.addAttribute("patient", new PatientProfile());
        return "staff/patient-form";
    }

    @PostMapping("/patients/save")
    public String savePatient(@ModelAttribute PatientProfile patient,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        PatientProfile saved = patientService.save(patient);
        auditLogService.logPatientCreated(
                userDetails.getUsername(), saved.getFullName(), saved.getPatientId()
        );
        ra.addFlashAttribute("success", "Đã thêm bệnh nhân " + patient.getFullName());
        return "redirect:/staff/patients";
    }

    // ── CHI TIẾT BỆNH NHÂN ─────────────────────────────
    @GetMapping("/patients/{id}")
    public String patientDetail(@PathVariable Integer id,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        PatientProfile patient = patientService.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        List<ConsultationRecord> records = consultationService.getByPatient(patient);

        java.util.Map<Integer, List<RecordIcd>> recordDiagnosesMap = new java.util.HashMap<>();
        records.forEach(r -> recordDiagnosesMap.put(
                r.getRecordId(),
                consultationService.getDiagnosesByRecord(r.getRecordId())
        ));

        List<LabRequest> labRequests = labRequestRepository.findByPatientOrderByCreatedAtDesc(patient);

        model.addAttribute("staff", staff);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        model.addAttribute("recordDiagnosesMap", recordDiagnosesMap);

        java.util.Map<Integer, com.cardio.model.HeartClinicalMetrics> recordMetricsMap =
                new java.util.HashMap<>();
        records.forEach(r -> consultationService.getMetricsByRecord(r.getRecordId())
                .ifPresent(m -> recordMetricsMap.put(r.getRecordId(), m)));
        model.addAttribute("recordMetricsMap", recordMetricsMap);

        model.addAttribute("labRequests", labRequests);
        return "staff/patient-detail";
    }

    // ── GHI NHẬN CHỈ SỐ SINH TỒN & XÉT NGHIỆM ───────────
    @GetMapping("/patients/{id}/vitals")
    public String vitalsForm(@PathVariable Integer id,
            @RequestParam(required = false) Integer requestId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        PatientProfile patient = patientService.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        model.addAttribute("staff", staff);
        model.addAttribute("patient", patient);
        model.addAttribute("vitals", new HeartClinicalMetrics());
        model.addAttribute("requestId", requestId);
        return "staff/vitals-form";
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
        StaffProfile staff = getCurrentStaff(userDetails);
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
        record.setDoctor(null); // Ghi nhận bởi Điều dưỡng, chưa gán Bác sĩ chuyên khoa khám bệnh án này
        record.setVisitDate(LocalDateTime.now());
        record.setConsultationNotes(consultationNotes != null && !consultationNotes.trim().isEmpty() ? consultationNotes : 
                ("Nhập chỉ số sinh tồn & xét nghiệm bởi " + staff.getFullName()));
        record.setTreatmentPlan(treatmentPlan != null && !treatmentPlan.trim().isEmpty() ? treatmentPlan : "Chờ khám chuyên khoa.");
        record.setStatus(status != null && !status.trim().isEmpty() ? status : "Pending");
        consultationRepository.save(record);

        // 2. Thiết lập liên kết và lưu chỉ số (HeartClinicalMetrics)
        vitals.setRecord(record);
        if (patient.getDob() != null) {
            vitals.setAge(java.time.Period.between(patient.getDob(), java.time.LocalDate.now()).getYears());
        }
        if (patient.getGender() != null) {
            vitals.setSex("Nam".equalsIgnoreCase(patient.getGender()) || "Male".equalsIgnoreCase(patient.getGender()) ? "Male" : "Female");
        }
        vitals.setRecordedByStaffID(staff.getStaffId());
        vitals.setRecordedAt(LocalDateTime.now());
        heartClinicalMetricsRepository.save(vitals);

        // 2.5. Tự động chạy dự đoán AI để phát hiện nguy cơ và sinh cảnh báo
        consultationService.runAutoAIPrediction(record, vitals);

        // 3. Hoàn thành yêu cầu xét nghiệm nếu có
        if (requestId != null) {
            consultationService.completeLabRequest(requestId, vitals, consultationNotes);
        }

        ra.addFlashAttribute("success", "Đã ghi nhận bệnh án và các chỉ số sức khỏe của bệnh nhân!");
        return "redirect:/staff/patients/" + id;
    }

    // ── XỬ LÝ CẢNH BÁO ────────────────────────────────────
    @PostMapping("/alerts/{id}/resolve")
    public String resolveAlert(@PathVariable Integer id,
            @RequestParam(defaultValue = "resolved") String action,
            @RequestParam(required = false) String reason,
            RedirectAttributes ra) {
        consultationService.updateAlertStatus(id, action, reason);
        ra.addFlashAttribute("success", "Đã cập nhật trạng thái cảnh báo!");
        return "redirect:/staff/alerts";
    }

    // ── DANH SÁCH YÊU CẦU XÉT NGHIỆM ──────────────────────
    @GetMapping("/lab-requests")
    public String listLabRequests(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        List<LabRequest> requests = labRequestRepository.findAllByOrderByCreatedAtAsc();
        model.addAttribute("requests", requests);
        model.addAttribute("staff", staff);
        return "staff/lab-requests";
    }

    // ── DANH SÁCH CẢNH BÁO ───────────────────────────────
    @GetMapping("/alerts")
    public String alerts(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        List<AIRiskPrediction> alerts = consultationService.getUnhandledHighAlerts();

        Map<Integer, Double> urgencyMap = new HashMap<>();
        alerts.forEach(a -> urgencyMap.put(a.getPredictionId(),
                consultationService.calcUrgencyScore(a)));

        Map<Integer, Boolean> thresholdExceededMap = new HashMap<>();
        alerts.forEach(a -> thresholdExceededMap.put(a.getPredictionId(),
                consultationService.exceedsPersonalThreshold(a)));

        Map<Integer, Double> thresholdValueMap = new HashMap<>();
        alerts.forEach(a -> {
            if (a.getRecord() != null && a.getRecord().getPatient() != null
                    && a.getRecord().getDoctor() != null) {
                thresholdRepository
                        .findByPatientAndDoctor(a.getRecord().getPatient(), a.getRecord().getDoctor())
                        .ifPresent(t -> thresholdValueMap.put(a.getPredictionId(), t.getRiskScoreThreshold()));
            }
        });

        List<AIRiskPrediction> unhandled = alerts.stream()
                .filter(a -> !a.getIsAlertSent()).toList();

        model.addAttribute("staff", staff);
        model.addAttribute("alerts", alerts);
        model.addAttribute("urgencyMap", urgencyMap);
        model.addAttribute("thresholdExceededMap", thresholdExceededMap);
        model.addAttribute("thresholdValueMap", thresholdValueMap);
        model.addAttribute("highCount", unhandled.stream().filter(a -> "HIGH".equals(a.getRiskLevel())).count());
        model.addAttribute("medCount", unhandled.stream().filter(a -> "MEDIUM".equals(a.getRiskLevel())).count());
        model.addAttribute("lowCount", unhandled.stream().filter(a -> "LOW".equals(a.getRiskLevel())).count());
        return "staff/alerts";
    }

    // ── LỊCH HẸN ─────────────────────────────────────────
    @GetMapping("/appointments")
    public String appointments(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam(required = false) String dateStr,
                               Model model) {
        StaffProfile staff = getCurrentStaff(userDetails);
        LocalDate date = (dateStr != null && !dateStr.isBlank()) ? LocalDate.parse(dateStr) : LocalDate.now();

        List<Appointment> appointments = appointmentRepository.findAll().stream()
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

        List<DoctorProfile> doctors = doctorRepository.findAll();
        List<Appointment> allReference = appointmentRepository.findAll();
        Appointment.populateQueueNumbers(appointments, allReference);

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

        model.addAttribute("staff", staff);
        model.addAttribute("doctors", doctors);
        model.addAttribute("appointments", appointments);
        model.addAttribute("workloads", workloads);
        model.addAttribute("doctorHasInProgress", doctorHasInProgress);
        model.addAttribute("appointmentsByDoctor", appointmentsByDoctor);
        model.addAttribute("selectedDate", date);
        model.addAttribute("patients", patientService.getAllPatients());
        return "staff/appointments";
    }

    @GetMapping("/appointments/{id}/details")
    @ResponseBody
    public Appointment getAppointmentDetails(@PathVariable Integer id) {
        return appointmentRepository.findById(id).orElse(null);
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
}
