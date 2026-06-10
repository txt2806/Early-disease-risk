package com.cardio.controller;

import com.cardio.dto.AIRequest;
import com.cardio.dto.AIResponse;
import com.cardio.model.*;
import com.cardio.repository.DoctorRepository;
import com.cardio.repository.SystemLogRepository;
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

// ── CONTROLLER (C trong MVC) ─────────────────────────
@Controller
@RequestMapping("/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorRepository doctorRepository;
    private final PatientService patientService;
    private final ConsultationService consultationService;
    private final AIService aiService;
    private final SystemLogRepository systemLogRepository;

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

        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patients);
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

        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patientPage.getContent());
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
        patientService.save(patient);
        if (userDetails != null) {
            try {
                SystemLog log = new SystemLog();
                log.setUsername(userDetails.getUsername());
                log.setAction("DOCTOR_ADD_PATIENT");
                log.setDetails("Bác sĩ thêm bệnh nhân mới: " + patient.getFullName() + " (Email/Username: " + patient.getUsername() + ")");
                log.setTimestamp(java.time.LocalDateTime.now());
                systemLogRepository.save(log);
            } catch (Exception ex) {
                System.err.println("Error saving doctor add patient system audit log: " + ex.getMessage());
            }
        }
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

        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        return "doctor/patient-detail";
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

    // ── DỰ ĐOÁN AI ─────────────────────────────────────
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

        // Call AI Service (do not save to database yet)
        com.cardio.dto.AIResponse aiResponse = aiService.predict(aiRequest);

        // Build transient prediction to display on screen
        AIRiskPrediction prediction = new AIRiskPrediction();
        prediction.setRiskScore(java.math.BigDecimal.valueOf(aiResponse.getProbability() * 100));
        prediction.setRiskLevel(aiResponse.getRisk_level());
        prediction.setRiskExplanation(aiResponse.getMessage());

        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patientService.getAllPatients());
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("prediction", prediction);
        model.addAttribute("selectedPatient", patient);
        model.addAttribute("isSaved", false); // Not saved to database yet
        return "doctor/ai-predict";
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

        if (userDetails != null) {
            try {
                SystemLog log = new SystemLog();
                log.setUsername(userDetails.getUsername());
                log.setAction("DOCTOR_SAVE_PREDICTION");
                log.setDetails("Bác sĩ lưu kết quả chẩn đoán AI và hồ sơ khám cho bệnh nhân: " + patient.getFullName() + 
                               " (Mức độ nguy cơ: " + riskLevel + ", Điểm số: " + riskScore + "%)");
                log.setTimestamp(java.time.LocalDateTime.now());
                systemLogRepository.save(log);
            } catch (Exception ex) {
                System.err.println("Error saving doctor save prediction system audit log: " + ex.getMessage());
            }
        }

        model.addAttribute("doctor", doctor);
        model.addAttribute("patients", patientService.getAllPatients());
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("prediction", prediction);
        model.addAttribute("selectedPatient", patient);
        model.addAttribute("isSaved", true); // Record is now saved
        return "doctor/ai-predict";
    }
}
