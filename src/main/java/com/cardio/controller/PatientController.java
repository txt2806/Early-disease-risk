package com.cardio.controller;

import com.cardio.model.*;
import com.cardio.repository.*;
import com.cardio.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.Optional;

@Controller
@RequestMapping("/patient")
@RequiredArgsConstructor
@Slf4j
public class PatientController {

    private static final String SQL_SELECT_LATEST_SELF_MONITORING = "SELECT CurrentHeartRate, TriggeredAlert, LogDate, Symptoms FROM Patient_Self_Monitoring WHERE PatientID = ? ORDER BY LogDate DESC LIMIT 5";

    private static final String SQL_SELECT_ALL_SELF_MONITORING = "SELECT LogID AS \"LogID\", LogDate AS \"LogDate\", CurrentHeartRate AS \"CurrentHeartRate\", Symptoms AS \"Symptoms\", TriggeredAlert AS \"TriggeredAlert\" FROM Patient_Self_Monitoring WHERE PatientID = ? ORDER BY LogDate DESC";

    private static final String SQL_INSERT_SELF_MONITORING = "INSERT INTO Patient_Self_Monitoring (PatientID, LogDate, CurrentHeartRate, Symptoms, SeverityScore, TriggeredAlert) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?)";

    private static final String SQL_SELECT_CONSULTATION_RECORDS = "SELECT r.RecordID AS \"RecordID\", r.VisitDate AS \"VisitDate\", r.ConsultationNotes AS \"ConsultationNotes\", r.TreatmentPlan AS \"TreatmentPlan\", r.Status AS \"Status\", "
            +
            "COALESCE(d.FullName, (SELECT doc.FullName FROM Appointment app JOIN Doctor_Profile doc ON app.DoctorID = doc.DoctorID WHERE app.PatientID = r.PatientID AND DATE(app.ScheduledDate) = DATE(r.VisitDate) LIMIT 1), 'BS. CardioCare') AS \"DoctorName\", " +
            "COALESCE(d.Specialty, (SELECT doc.Specialty FROM Appointment app JOIN Doctor_Profile doc ON app.DoctorID = doc.DoctorID WHERE app.PatientID = r.PatientID AND DATE(app.ScheduledDate) = DATE(r.VisitDate) LIMIT 1), 'Chuyên khoa Tim mạch') AS \"DoctorSpecialty\", " +
            "p.RiskScore AS \"RiskScore\", p.RiskLevel AS \"RiskLevel\", p.RiskExplanation AS \"RiskExplanation\", " +
            "COALESCE(p.HealthAdvice, CASE WHEN p.RiskLevel = 'HIGH' THEN 'Bệnh nhân có nguy cơ tim mạch cao. Cần tái khám ngay, tuân thủ đơn thuốc và hạn chế vận động quá sức.' WHEN p.RiskLevel = 'MEDIUM' THEN 'Nguy cơ trung bình — nên theo dõi huyết áp/nhịp tim hàng ngày, duy trì ăn nhạt và lối sống lành mạnh.' WHEN p.RiskLevel = 'LOW' THEN 'Chỉ số nguy cơ thấp. Tiếp tục duy trì chế độ sinh hoạt và kiểm tra sức khỏe định kỳ.' ELSE 'Theo dõi sức khỏe và tái khám theo chỉ định của bác sĩ.' END) AS \"HealthAdvice\", " +
            "m.ChestPainType AS \"ChestPainType\", m.RestingBP AS \"RestingBP\", m.Cholesterol AS \"Cholesterol\", m.FastingBloodSugar AS \"FastingBloodSugar\", " +
            "m.RestingECG AS \"RestingECG\", m.MaxHeartRate AS \"MaxHeartRate\", m.ExerciseAngina AS \"ExerciseAngina\", m.Oldpeak AS \"Oldpeak\", m.Slope AS \"Slope\", m.Ca AS \"Ca\", m.Thal AS \"Thal\", " +
            "m.Temperature AS \"Temperature\", m.SpO2 AS \"SpO2\", m.BloodTest AS \"BloodTest\", m.UrineTest AS \"UrineTest\", m.Xray AS \"Xray\", m.Ultrasound AS \"Ultrasound\", m.Mri AS \"Mri\", m.Ct AS \"Ct\" " +
            "FROM Consultation_Record r " +
            "LEFT JOIN Doctor_Profile d ON r.DoctorID = d.DoctorID " +
            "LEFT JOIN AI_Risk_Prediction p ON r.RecordID = p.RecordID " +
            "LEFT JOIN Heart_Clinical_Metrics m ON r.RecordID = m.RecordID " +
            "WHERE r.PatientID = ? ORDER BY r.VisitDate DESC";

    private static final String ACTION_SELF_MONITORING = "PATIENT_SELF_MONITORING";
    private static final String ACTION_BOOK_APPOINTMENT = "PATIENT_BOOK_APPOINTMENT";
    private static final String APPOINTMENT_STATUS_PENDING = "Pending";
    private static final String APPOINTMENT_STATUS_CONFIRMED = "Confirmed";
    private static final String APPOINTMENT_STATUS_CANCELLED = "Cancelled";

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final AIRiskRepository aiRiskRepository;
    private final SystemLogRepository systemLogRepository;
    private final InvoiceRepository invoiceRepository;
    private final SystemSettingService systemSettingService;
    private final JdbcTemplate jdbcTemplate;
    private final com.cardio.service.ChatService chatService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Value("${sepay.bank.id:}")
    private String bankId;

    @Value("${sepay.bank.account:}")
    private String bankAccount;

    @Value("${sepay.bank.owner:}")
    private String bankOwner;

    private PatientProfile getCurrentPatient(UserDetails userDetails) {
        return patientRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .orElseThrow(
                        () -> new RuntimeException("Patient profile not found for user: " + userDetails.getUsername()));
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        if (patient.isFirstLogin()) {
            return "redirect:/patient/change-password-first-time";
        }
        List<Appointment> appointments = appointmentRepository
                .findByPatientOrderByScheduledDateDescTimeSlotDesc(patient);
        List<Appointment> allReference = appointmentRepository.findAll();
        Appointment.populateQueueNumbers(appointments, allReference);

        List<AIRiskPrediction> predictions = aiRiskRepository.findByPatient(patient);

        // Fetch latest heart rate and alerts from self monitoring
        List<Map<String, Object>> selfLogs = jdbcTemplate.queryForList(
                SQL_SELECT_LATEST_SELF_MONITORING,
                patient.getPatientId());

        Integer latestHeartRate = null;
        boolean hasActiveAlert = false;
        if (!selfLogs.isEmpty()) {
            latestHeartRate = (Integer) selfLogs.get(0).get("CurrentHeartRate");
            hasActiveAlert = Boolean.TRUE.equals(selfLogs.get(0).get("TriggeredAlert"));
        }

        String latestRiskLevel = "N/A";
        java.math.BigDecimal latestRiskScore = java.math.BigDecimal.ZERO;
        if (!predictions.isEmpty()) {
            latestRiskLevel = predictions.get(0).getRiskLevel();
            latestRiskScore = predictions.get(0).getRiskScore();
        }

        model.addAttribute("patient", patient);
        model.addAttribute("appointments", appointments.stream().limit(5).toList());
        model.addAttribute("selfLogs", selfLogs);
        model.addAttribute("latestHeartRate", latestHeartRate != null ? latestHeartRate : 0);
        model.addAttribute("latestRiskLevel", latestRiskLevel);
        model.addAttribute("latestRiskScore", latestRiskScore);
        model.addAttribute("hasActiveAlert", hasActiveAlert);
        model.addAttribute("totalAppointments", appointments.size());

        return "patient/dashboard";
    }

    @GetMapping("/self-declare")
    public String selfDeclareForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);

        List<Map<String, Object>> monitoringLogs = jdbcTemplate.queryForList(
                SQL_SELECT_ALL_SELF_MONITORING,
                patient.getPatientId());

        model.addAttribute("patient", patient);
        model.addAttribute("monitoringLogs", monitoringLogs);
        return "patient/self-declare";
    }

    @PostMapping("/self-declare/save")
    public String saveSelfDeclare(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("heartRate") Integer heartRate,
            @RequestParam("symptoms") String symptoms,
            @RequestParam(value = "severityScore", defaultValue = "5") Integer severityScore,
            RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);

        boolean triggeredAlert = (heartRate > 100 || heartRate < 50 || severityScore >= 8);
        String symLower = symptoms != null ? symptoms.toLowerCase() : "";
        if (symLower.contains("đau ngực") || symLower.contains("khó thở") || symLower.contains("ngất")
                || symLower.contains("chóng mặt") || symLower.contains("đau thắt ngực") || symLower.contains("dữ dội")) {
            triggeredAlert = true;
        }

        try {
            jdbcTemplate.update(
                    SQL_INSERT_SELF_MONITORING,
                    patient.getPatientId(), heartRate, symptoms, severityScore, triggeredAlert);

            // Log action
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(patient.getUsername());
            sysLog.setAction(ACTION_SELF_MONITORING);
            sysLog.setDetails("Bệnh nhân tự khai báo: Nhịp tim " + heartRate + " BPM, Triệu chứng: " + symptoms
                    + " (Cảnh báo: " + (triggeredAlert ? "Có" : "Không") + ")");
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            ra.addFlashAttribute("success", "Ghi nhận chỉ số sức khỏe thành công!");
        } catch (Exception e) {
            log.error("Error saving self declare: ", e);
            ra.addFlashAttribute("error", "Lỗi lưu khai báo sức khỏe.");
        }
        return "redirect:/patient/self-declare";
    }

    @GetMapping("/records")
    public String medicalRecords(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);

        // Fetch all ConsultationRecords for the patient
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                SQL_SELECT_CONSULTATION_RECORDS,
                patient.getPatientId());

        model.addAttribute("patient", patient);
        model.addAttribute("records", records);
        return "patient/records";
    }

    @GetMapping("/book-appointment")
    public String bookAppointmentForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        List<DoctorProfile> doctors = doctorRepository.findAll();

        model.addAttribute("patient", patient);
        model.addAttribute("doctors", doctors);
        return "patient/book-appointment";
    }

    @PostMapping("/book-appointment/save")
    public String saveAppointment(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("doctorId") Integer doctorId,
            @RequestParam("scheduledDate") String scheduledDateStr,
            @RequestParam("preliminaryStatus") String preliminaryStatus,
            @RequestParam(value = "bookingType", required = false, defaultValue = "General") String bookingType,
            @RequestParam(value = "timeSlot", required = false) String timeSlotStr,
            RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);

        try {
            DoctorProfile doctor = doctorRepository.findById(doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));

            LocalDate scheduledDate = LocalDate.parse(scheduledDateStr);

            // Auto-overflow logic: find next consecutive day where doctor has < 8
            // appointments
            LocalDate targetDate = scheduledDate;
            boolean wasShifted = false;
            while (appointmentRepository.countByDoctorAndScheduledDateAndStatusNot(doctor, targetDate,
                    APPOINTMENT_STATUS_CANCELLED) >= 8) {
                targetDate = targetDate.plusDays(1);
                wasShifted = true;
            }

            // Check if patient already has an active appointment with the same doctor on
            // this final date
            final LocalDate finalDate = targetDate;
            boolean alreadyBooked = appointmentRepository.findAll().stream()
                    .anyMatch(a -> a.getPatient().getPatientId().equals(patient.getPatientId())
                            && a.getDoctor() != null && a.getDoctor().getDoctorId().equals(doctor.getDoctorId())
                            && a.getScheduledDate().equals(finalDate)
                            && !APPOINTMENT_STATUS_CANCELLED.equalsIgnoreCase(a.getStatus()));
            if (alreadyBooked) {
                ra.addFlashAttribute("error",
                        "Bạn đã đăng ký lịch hẹn khám với bác sĩ " + doctor.getFullName() + " vào ngày này rồi.");
                return "redirect:/patient/book-appointment";
            }

            Appointment appointment = new Appointment();
            appointment.setPatient(patient);
            appointment.setDoctor(doctor);
            appointment.setScheduledDate(targetDate);
            if (timeSlotStr != null && !timeSlotStr.isBlank()) {
                appointment.setTimeSlot(java.time.LocalTime.parse(timeSlotStr));
            }
            appointment.setBookingType(bookingType);
            appointment.setPreliminaryStatus(preliminaryStatus);
            appointment.setStatus(APPOINTMENT_STATUS_CONFIRMED);

            appointmentRepository.save(appointment);

            // Tự động tạo hóa đơn tương ứng với lịch khám
            Long fee = "Specialist".equalsIgnoreCase(bookingType) ? systemSettingService.getFeeSpecialist() : systemSettingService.getFeeGeneral();
            Invoice invoice = new Invoice();
            invoice.setAppointment(appointment);
            invoice.setPatient(patient);
            invoice.setAmount(fee);
            invoice.setPaidAmount(0L);
            invoice.setStatus("Unpaid");
            invoice.setCreatedDate(LocalDateTime.now());
            invoice = invoiceRepository.save(invoice);

            // Cập nhật mã nội dung chuyển khoản bảo mật dạng TT{id}
            invoice.setReferenceCode("TT" + invoice.getInvoiceId());
            invoiceRepository.save(invoice);

            // Save system log
            SystemLog sysLog = new SystemLog();
            sysLog.setUsername(patient.getUsername());
            sysLog.setAction(ACTION_BOOK_APPOINTMENT);
            sysLog.setDetails("Bệnh nhân đặt lịch hẹn khám với bác sĩ: " + doctor.getFullName() + " vào ngày "
                    + targetDate.toString());
            sysLog.setTimestamp(LocalDateTime.now());
            systemLogRepository.save(sysLog);

            if (wasShifted) {
                ra.addFlashAttribute("success",
                        "Do bác sĩ đã đầy lịch khám vào ngày đã chọn (tối đa 8 ca/ngày), lịch hẹn của bạn đã được tự động chuyển sang ngày "
                                + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                + ". Vui lòng chờ tiếp nhận.");
            } else {
                ra.addFlashAttribute("success", "Đặt lịch hẹn khám thành công! Vui lòng chờ phòng khám tiếp nhận.");
            }
        } catch (Exception e) {
            log.error("Error booking appointment: ", e);
            String message = e.getMessage();
            if (message != null && (message.contains("unique_doctor_slot") || message.contains("unique_patient_slot")
                    || message.contains("ConstraintViolation") || message.contains("duplicate key"))) {
                ra.addFlashAttribute("error", "Lỗi đặt lịch hẹn: đã có lịch hẹn được đặt trước đó");
            } else {
                ra.addFlashAttribute("error", "Lỗi đặt lịch hẹn: " + e.getMessage());
            }
        }
        return "redirect:/patient/dashboard";
    }

    @GetMapping("/invoices")
    public String viewInvoices(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);

        // Tự động khởi tạo hóa đơn chưa tồn tại cho các lịch khám của patient này (để
        // tránh dữ liệu cũ không có hóa đơn)
        List<Appointment> appointments = appointmentRepository
                .findByPatientOrderByScheduledDateDescTimeSlotDesc(patient);
        for (Appointment app : appointments) {
            Optional<Invoice> invOpt = invoiceRepository.findByAppointment(app);
            if (!invOpt.isPresent() && !"Cancelled".equalsIgnoreCase(app.getStatus())) {
                Long fee = "Specialist".equalsIgnoreCase(app.getBookingType()) ? systemSettingService.getFeeSpecialist() : systemSettingService.getFeeGeneral();
                Invoice invoice = new Invoice();
                invoice.setAppointment(app);
                invoice.setPatient(patient);
                invoice.setAmount(fee);
                invoice.setPaidAmount(0L);
                invoice.setStatus("Unpaid");
                invoice.setCreatedDate(LocalDateTime.now());
                invoice = invoiceRepository.save(invoice);
                invoice.setReferenceCode("TT" + invoice.getInvoiceId());
                invoiceRepository.save(invoice);
            }
        }

        List<Invoice> invoices = invoiceRepository.findByPatientOrderByCreatedDateDesc(patient);
        model.addAttribute("patient", patient);
        model.addAttribute("invoices", invoices);
        return "patient/invoices";
    }

    @GetMapping("/invoices/pay/{id}")
    public String payInvoice(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);

        Optional<Invoice> invOpt = invoiceRepository.findById(id);
        if (!invOpt.isPresent()) {
            ra.addFlashAttribute("error", "Không tìm thấy hóa đơn cần thanh toán.");
            return "redirect:/patient/invoices";
        }

        Invoice invoice = invOpt.get();
        // Bảo vệ: Chỉ cho phép bệnh nhân thanh toán hóa đơn của chính mình
        if (!invoice.getPatient().getPatientId().equals(patient.getPatientId())) {
            ra.addFlashAttribute("error", "Bạn không có quyền truy cập hóa đơn này.");
            return "redirect:/patient/invoices";
        }

        model.addAttribute("patient", patient);
        model.addAttribute("invoice", invoice);
        model.addAttribute("bankId", bankId);
        model.addAttribute("bankAccount", bankAccount);
        model.addAttribute("bankOwner", bankOwner);

        return "patient/pay-invoice";
    }

    @GetMapping("/invoices/{id}/check-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkInvoiceStatusPatient(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        try {
            PatientProfile patient = getCurrentPatient(userDetails);
            Optional<Invoice> invOpt = invoiceRepository.findById(id);
            if (invOpt.isPresent()) {
                Invoice invoice = invOpt.get();
                if (!invoice.getPatient().getPatientId().equals(patient.getPatientId())) {
                    result.put("success", false);
                    result.put("error", "Unauthorized access");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
                }
                result.put("success", true);
                result.put("status", invoice.getStatus());
                result.put("amount", invoice.getAmount());
                result.put("paidAmount", invoice.getPaidAmount());
                result.put("missingAmount", Math.max(0, invoice.getAmount() - invoice.getPaidAmount()));
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("error", "Hóa đơn không tồn tại");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    // ── CHATBOT AI CHO BỆNH NHÂN TRÊN WEB ──────────────────────
    @GetMapping("/chatbot")
    public String chatbotPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        model.addAttribute("patient", patient);
        return "patient/chatbot";
    }

    @PostMapping("/chatbot/send")
    @ResponseBody
    public ResponseEntity<com.cardio.dto.ChatResponse> sendChatMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody com.cardio.dto.ChatRequest request) {
        try {
            PatientProfile patient = getCurrentPatient(userDetails);
            request.setPatientId(patient.getPatientId());

            Map<String, Object> context = request.getPredict_context();
            if (context == null) context = new HashMap<>();
            context.put("Họ và tên bệnh nhân", patient.getFullName());
            context.put("Giới tính", patient.getGender() != null ? patient.getGender() : "Nam");
            if (patient.getDob() != null) {
                int age = java.time.Period.between(patient.getDob(), java.time.LocalDate.now()).getYears();
                context.put("Tuổi", age);
            }
            context.put("Số điện thoại", patient.getPhone());
            
            List<Map<String, Object>> records = jdbcTemplate.queryForList(SQL_SELECT_CONSULTATION_RECORDS, patient.getPatientId());
            if (!records.isEmpty()) {
                Map<String, Object> latest = records.get(0);
                context.put("Ngày khám gần nhất", latest.get("VisitDate"));
                context.put("Bác sĩ phụ trách", latest.get("DoctorName"));
                context.put("Mức độ nguy cơ tim mạch", latest.get("RiskLevel"));
                context.put("Lời khuyên y tế", latest.get("HealthAdvice"));
            }
            request.setPredict_context(context);

            String prompt = "[HỆ THỐNG CHUYÊN MÔN: Bạn là Trợ lý AI Y tế tư vấn tim mạch CardioCare cho BỆNH NHÂN " + patient.getFullName() + ". Hãy trả lời ân cần, ngắn gọn, dễ hiểu và tư vấn y tế phù hợp.] " + request.getMessage();
            request.setMessage(prompt);

            com.cardio.dto.ChatResponse response = chatService.sendMessage(request, "patient");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending patient chatbot message: ", e);
            com.cardio.dto.ChatResponse err = new com.cardio.dto.ChatResponse();
            err.setReply("Hệ thống trợ lý AI y tế hiện đang bận. Vui lòng thử lại sau ít phút.");
            return ResponseEntity.ok(err);
        }
    }

    // ── TÍNH NĂNG GIẢ LẬP THANH TOÁN DÀNH CHO DEMO/TEST ──────
    @PostMapping("/invoices/{id}/test-pay")
    public String testPayInvoice(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);
        Optional<Invoice> invOpt = invoiceRepository.findById(id);
        if (invOpt.isPresent()) {
            Invoice invoice = invOpt.get();
            if (invoice.getPatient().getPatientId().equals(patient.getPatientId())) {
                invoice.setStatus("Paid");
                invoice.setPaidAmount(invoice.getAmount());
                invoice.setPaymentDate(LocalDateTime.now());
                invoice.setPaymentMethod("Chuyển khoản (Demo Test)");
                invoiceRepository.save(invoice);

                Appointment app = invoice.getAppointment();
                if (app != null && !"Cancelled".equalsIgnoreCase(app.getStatus())) {
                    app.setStatus("Confirmed");
                    appointmentRepository.save(app);
                }
                ra.addFlashAttribute("success", "Thanh toán hóa đơn thành công (Chế độ Giả lập Test)!");
            }
        }
        return "redirect:/patient/invoices";
    }

    // ── ĐỔI MẬT KHẨU LẦN ĐẦU CHO BỆNH NHÂN TRÊN WEB ────────────
    @GetMapping("/change-password-first-time")
    public String changePasswordFirstTimePage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        PatientProfile patient = getCurrentPatient(userDetails);
        model.addAttribute("patient", patient);
        return "patient/change-password-first-time";
    }

    @PostMapping("/change-password-first-time/save")
    public String saveFirstTimePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes ra) {
        PatientProfile patient = getCurrentPatient(userDetails);

        if (newPassword == null || newPassword.trim().length() < 3) {
            ra.addFlashAttribute("error", "Mật khẩu mới phải có ít nhất 3 ký tự!");
            return "redirect:/patient/change-password-first-time";
        }

        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", "Xác nhận mật khẩu mới không trùng khớp!");
            return "redirect:/patient/change-password-first-time";
        }

        if (passwordEncoder.matches(newPassword.trim(), patient.getPasswordHash())) {
            ra.addFlashAttribute("error", "Mật khẩu mới phải khác với mật khẩu ban đầu!");
            return "redirect:/patient/change-password-first-time";
        }

        patient.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        patient.setFirstLogin(false);
        patientRepository.save(patient);

        ra.addFlashAttribute("success", "Đổi mật khẩu thành công! Chào mừng bạn đến với hệ thống CardioCare.");
        return "redirect:/patient/dashboard";
    }
}
