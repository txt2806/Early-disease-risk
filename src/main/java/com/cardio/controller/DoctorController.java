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

<<<<<<< Updated upstream
=======
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
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception e) {
                date = null;
            }
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

>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
=======
        // Lấy danh sách chẩn đoán ICD cho từng record
        java.util.Map<Integer, List<RecordIcd>> recordDiagnosesMap = new java.util.HashMap<>();
        records.forEach(r -> recordDiagnosesMap.put(
                r.getRecordId(),
                consultationService.getDiagnosesByRecord(r.getRecordId())
        ));

        boolean hasActiveAppointment = false;
        Integer activeAppointmentId = null;
        if (doctor.getDoctorId() != null) {
            var appOpt = appointmentRepository.findFirstByPatientPatientIdAndDoctorDoctorIdAndStatus(id, doctor.getDoctorId(), "InProgress");
            if (appOpt.isPresent()) {
                hasActiveAppointment = true;
                activeAppointmentId = appOpt.get().getAppointmentId();
            }
        }

        // Lấy danh sách bác sĩ chuyên khoa (chuyên môn khác "Đa khoa" và khác null)
        List<DoctorProfile> specialists = doctorRepository.findAll().stream()
                .filter(d -> d.getSpecialty() != null && !d.getSpecialty().toLowerCase().contains("đa khoa"))
                .toList();

        model.addAttribute("doctor", doctor);
        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        model.addAttribute("recordDiagnosesMap", recordDiagnosesMap);
        model.addAttribute("highAlertCount", 0L);
        model.addAttribute("hasActiveAppointment", hasActiveAppointment);
        model.addAttribute("activeAppointmentId", activeAppointmentId);
        model.addAttribute("specialists", specialists);
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======

    // ── LỊCH HẸN (APPOINTMENTS) ──────────────────────────
    @GetMapping("/appointments")
    public String appointments(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam(required = false) String dateStr,
                               @RequestParam(required = false) String filterStatus,
                               Model model) {
        DoctorProfile doctor = getCurrentDoctor(userDetails);
        LocalDate parsedDate;
        try {
            parsedDate = (dateStr != null && !dateStr.isBlank()) ? LocalDate.parse(dateStr) : LocalDate.now();
        } catch (Exception e) {
            parsedDate = LocalDate.now();
        }
        final LocalDate date = parsedDate;

        final List<Appointment> dailyAppointments = appointmentRepository.findByScheduledDateFetchPatientAndDoctor(date);

        List<Appointment> appointments;
        List<DoctorProfile> doctors;

        if (doctor.getDoctorId() != null) {
            // Doctors only view their own schedule
            appointments = dailyAppointments.stream()
                    .filter(a -> a.getDoctor() != null &&
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
            appointments = dailyAppointments.stream()
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

        // Sort using the status-based priority queue sorting
        appointments = sortAppointmentsForQueue(appointments);

        // Apply Status Filter
        if (filterStatus != null && !filterStatus.isBlank() && !"all".equalsIgnoreCase(filterStatus)) {
            if ("waiting".equalsIgnoreCase(filterStatus)) {
                appointments = appointments.stream()
                        .filter(a -> "CheckedIn".equalsIgnoreCase(a.getStatus()) || "Confirmed".equalsIgnoreCase(a.getStatus()) || "InProgress".equalsIgnoreCase(a.getStatus()))
                        .toList();
            } else if ("completed".equalsIgnoreCase(filterStatus)) {
                appointments = appointments.stream()
                        .filter(a -> "Completed".equalsIgnoreCase(a.getStatus()))
                        .toList();
            }
        }

        // Calculate workload, group appointments, and check for active InProgress check-up
        java.util.Map<Integer, Long> workloads = new java.util.HashMap<>();
        java.util.Map<Integer, Boolean> doctorHasInProgress = new java.util.HashMap<>();
        java.util.Map<Integer, List<Appointment>> appointmentsByDoctor = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> firstEligibleAppIdMap = new java.util.HashMap<>();

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

            Integer firstEligibleId = null;
            if (!hasInProgress) {
                Appointment firstEligible = docApps.stream()
                        .filter(a -> "Pending".equalsIgnoreCase(a.getStatus()) ||
                                     "Confirmed".equalsIgnoreCase(a.getStatus()) ||
                                     "CheckedIn".equalsIgnoreCase(a.getStatus()))
                        .findFirst()
                        .orElse(null);
                if (firstEligible != null) {
                    firstEligibleId = firstEligible.getAppointmentId();
                }
            }
            firstEligibleAppIdMap.put(doc.getDoctorId(), firstEligibleId);
        }

        model.addAttribute("doctor", doctor);
        model.addAttribute("doctors", doctors);
        model.addAttribute("appointments", appointments);
        model.addAttribute("workloads", workloads);
        model.addAttribute("doctorHasInProgress", doctorHasInProgress);
        model.addAttribute("appointmentsByDoctor", appointmentsByDoctor);
        model.addAttribute("firstEligibleAppIdMap", firstEligibleAppIdMap);
        model.addAttribute("selectedDate", date);
        model.addAttribute("filterStatus", filterStatus);
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
            boolean alreadyBooked = appointmentRepository.existsByPatientAndDoctorAndScheduledDateAndStatusNot(patient, doctor, finalDate, "Cancelled");
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
            if (app.getDoctor() != null && app.getScheduledDate() != null) {
                Integer maxQueue = appointmentRepository.findMaxQueueNumberByDoctorAndScheduledDate(app.getDoctor(), app.getScheduledDate());
                app.setQueueNumber(maxQueue == null ? 1 : maxQueue + 1);
            }
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
            
            DoctorProfile oldDoctor = app.getDoctor();
            LocalDate oldDate = app.getScheduledDate();
            
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

            // Handle Booking TimeSlot
            if (timeSlot != null && !timeSlot.isBlank()) {
                app.setTimeSlot(LocalTime.parse(timeSlot));
            }

            // Handle Start time (Giờ vào khám)
            if ("InProgress".equalsIgnoreCase(status) && app.getStartTime() == null) {
                app.setStartTime(LocalTime.now());
            } else if (!"InProgress".equalsIgnoreCase(status) && !"Completed".equalsIgnoreCase(status)) {
                app.setStartTime(null);
            }

            // Handle Completed end time (Giờ ra khám)
            if (endTime != null && !endTime.isBlank()) {
                app.setEndTime(LocalTime.parse(endTime));
            } else if ("Completed".equalsIgnoreCase(status) && app.getEndTime() == null) {
                app.setEndTime(LocalTime.now());
            } else if (!"Completed".equalsIgnoreCase(status)) {
                app.setEndTime(null);
            }

            boolean doctorChanged = (oldDoctor == null && app.getDoctor() != null) ||
                                    (oldDoctor != null && app.getDoctor() == null) ||
                                    (oldDoctor != null && app.getDoctor() != null && !oldDoctor.getDoctorId().equals(app.getDoctor().getDoctorId()));
            boolean dateChanged = (oldDate == null && app.getScheduledDate() != null) ||
                                  (oldDate != null && app.getScheduledDate() == null) ||
                                  (oldDate != null && !oldDate.equals(app.getScheduledDate()));
            if (doctorChanged || dateChanged || app.getQueueNumber() == null) {
                if (app.getDoctor() != null && app.getScheduledDate() != null) {
                    Integer maxQueue = appointmentRepository.findMaxQueueNumberByDoctorAndScheduledDate(app.getDoctor(), app.getScheduledDate());
                    app.setQueueNumber(maxQueue == null ? 1 : maxQueue + 1);
                } else {
                    app.setQueueNumber(null);
                }
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
        app.setStartTime(LocalTime.now());
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
            var appOpt = appointmentRepository.findFirstByPatientPatientIdAndDoctorDoctorIdAndStatus(id, doctor.getDoctorId(), "InProgress");
            if (appOpt.isPresent()) {
                Appointment app = appOpt.get();
                app.setStatus("Completed");
                app.setEndTime(LocalTime.now());
                appointmentRepository.save(app);
                ra.addFlashAttribute("success", "Đã hoàn thành khám cho bệnh nhân " + app.getPatient().getFullName());
            } else {
                ra.addFlashAttribute("error", "Không tìm thấy ca khám đang thực hiện cho bệnh nhân này.");
            }
        }
        return "redirect:/doctor/appointments";
    }

    @PostMapping("/appointments/refer")
    public String referToSpecialist(@RequestParam("patientId") Integer patientId,
                                    @RequestParam("appointmentId") Integer appointmentId,
                                    @RequestParam("specialistDoctorId") Integer specialistDoctorId,
                                    @RequestParam("referralNotes") String referralNotes,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    RedirectAttributes ra) {
        try {
            DoctorProfile doctor = getCurrentDoctor(userDetails);
            
            // 1. Hoàn thành lịch hẹn đa khoa hiện tại
            Appointment currentApp = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Lịch hẹn không tồn tại"));
            currentApp.setStatus("Completed");
            currentApp.setEndTime(LocalTime.now());
            appointmentRepository.save(currentApp);
            
            // 2. Tạo lịch hẹn chuyên khoa mới
            PatientProfile patient = patientService.findById(patientId)
                    .orElseThrow(() -> new RuntimeException("Bệnh nhân không tồn tại"));
            DoctorProfile specialist = doctorRepository.findById(specialistDoctorId)
                    .orElseThrow(() -> new RuntimeException("Bác sĩ chuyên khoa không tồn tại"));
            
            Appointment newApp = new Appointment();
            newApp.setPatient(patient);
            newApp.setDoctor(specialist);
            newApp.setScheduledDate(LocalDate.now());
            newApp.setTimeSlot(LocalTime.now());
            newApp.setRoomNumber(specialist.getRoomNumber());
            newApp.setPreliminaryStatus("Chuyển tuyến từ BS Đa khoa " + doctor.getFullName() + ": " + referralNotes);
            newApp.setStatus("CheckedIn"); // Đã đến, xuất hiện ngay trong danh sách chờ của chuyên khoa
            
            // Tính số thứ tự cho bác sĩ chuyên khoa
            Integer maxQueue = appointmentRepository.findMaxQueueNumberByDoctorAndScheduledDate(specialist, LocalDate.now());
            newApp.setQueueNumber(maxQueue == null ? 1 : maxQueue + 1);
            appointmentRepository.save(newApp);
            
            // Ghi nhận log hệ thống
            auditLogService.log(userDetails.getUsername(), "REFER_PATIENT", 
                "Chuyển tuyến bệnh nhân " + patient.getFullName() + " từ BS Đa khoa " + doctor.getFullName() + " sang BS Chuyên khoa " + specialist.getFullName());
            
            ra.addFlashAttribute("success", "Đã hoàn thành khám đa khoa và chuyển bệnh nhân " + patient.getFullName() + " sang Bác sĩ Chuyên khoa: " + specialist.getFullName() + " (STT: " + newApp.getQueueNumber() + " - " + newApp.getRoomNumber() + ")");
        } catch (Exception e) {
            log.error("Error referring patient: ", e);
            ra.addFlashAttribute("error", "Lỗi chuyển tuyến chuyên khoa: " + e.getMessage());
        }
        return "redirect:/doctor/appointments";
    }

    private int getStatusPriority(String status) {
        if ("InProgress".equalsIgnoreCase(status)) return 0;
        if ("CheckedIn".equalsIgnoreCase(status)) return 1;
        if ("Confirmed".equalsIgnoreCase(status)) return 2;
        if ("Pending".equalsIgnoreCase(status)) return 3;
        if ("Completed".equalsIgnoreCase(status)) return 4;
        return 5; // Cancelled or others
    }

    private List<Appointment> sortAppointmentsForQueue(List<Appointment> list) {
        if (list == null) return java.util.Collections.emptyList();
        return list.stream().sorted((a1, a2) -> {
            int p1 = getStatusPriority(a1.getStatus());
            int p2 = getStatusPriority(a2.getStatus());
            if (p1 != p2) return Integer.compare(p1, p2);

            // Within the same status priority, sort by queueNumber ascending
            Integer q1 = a1.getQueueNumber();
            Integer q2 = a2.getQueueNumber();
            if (q1 != null && q2 != null) {
                return q1.compareTo(q2);
            }
            if (q1 != null) return -1;
            if (q2 != null) return 1;

            // Fallback: TimeSlot
            LocalTime t1 = a1.getTimeSlot();
            LocalTime t2 = a2.getTimeSlot();
            if (t1 != null && t2 != null) return t1.compareTo(t2);
            if (t1 != null) return -1;
            if (t2 != null) return 1;

            // Fallback: ID
            if (a1.getAppointmentId() != null && a2.getAppointmentId() != null) {
                return a1.getAppointmentId().compareTo(a2.getAppointmentId());
            }
            return 0;
        }).collect(java.util.stream.Collectors.toList());
    }
>>>>>>> Stashed changes
}
