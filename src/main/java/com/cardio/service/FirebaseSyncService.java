package com.cardio.service;

import com.cardio.model.PatientProfile;
import com.cardio.model.SystemLog;
import com.cardio.repository.PatientRepository;
import com.cardio.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FirebaseSyncService {

    private final PatientRepository patientRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SystemLogRepository systemLogRepository;

    /**
     * Tự động chạy dọn dẹp các tài khoản Firebase bị mồ côi vào 2:00 sáng mỗi ngày
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoSyncFirebaseUsers() {
        System.out.println("Bắt đầu tiến trình tự động dọn dẹp tài khoản mồ côi (Firebase Sync)...");
        int deletedCount = 0;

        try {
            boolean isFirebaseInitialized = !com.google.firebase.FirebaseApp.getApps().isEmpty();
            if (!isFirebaseInitialized) {
                System.out.println("Tiến trình bị hủy: Firebase chưa được khởi tạo.");
                return;
            }

            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            List<PatientProfile> allPatients = patientRepository.findAll();

            for (PatientProfile patient : allPatients) {
                boolean foundInFirebase = false;

                // Kiểm tra theo Email
                try {
                    auth.getUserByEmail(patient.getUsername());
                    foundInFirebase = true;
                } catch (com.google.firebase.auth.FirebaseAuthException e) {
                    if ("user-not-found".equals(e.getErrorCode())) {
                        // Tiếp tục kiểm tra theo SĐT nếu không thấy email
                        if (patient.getPhone() != null && !patient.getPhone().isEmpty()) {
                            try {
                                String phone = patient.getPhone();
                                if (phone.startsWith("0")) {
                                    phone = "+84" + phone.substring(1);
                                }
                                auth.getUserByPhoneNumber(phone);
                                foundInFirebase = true;
                            } catch (com.google.firebase.auth.FirebaseAuthException ex) {
                                // Cũng không tìm thấy
                            }
                        }
                    } else {
                        // Lỗi khác (vd: quá tải request), tạm thời coi như có để tránh xóa nhầm
                        foundInFirebase = true;
                    }
                }

                if (!foundInFirebase) {
                    // Không tồn tại trên Firebase -> Xóa ở DB
                    Integer id = patient.getPatientId();
                    jdbcTemplate.update("UPDATE invoice SET patientid = NULL, appointmentid = NULL WHERE patientid = ?", id);
                    jdbcTemplate.update("UPDATE invoice SET appointmentid = NULL WHERE appointmentid IN (SELECT appointmentid FROM appointment WHERE patientid = ?)", id);
                    jdbcTemplate.update("DELETE FROM record_icd WHERE recordid IN (SELECT recordid FROM consultation_record WHERE patientid = ?)", id);
                    jdbcTemplate.update("DELETE FROM ai_risk_prediction WHERE recordid IN (SELECT recordid FROM consultation_record WHERE patientid = ?)", id);
                    jdbcTemplate.update("DELETE FROM heart_clinical_metrics WHERE recordid IN (SELECT recordid FROM consultation_record WHERE patientid = ?)", id);
                    jdbcTemplate.update("DELETE FROM consultation_record WHERE patientid = ?", id);
                    jdbcTemplate.update("DELETE FROM lab_request WHERE patientid = ?", id);
                    jdbcTemplate.update("DELETE FROM patient_alert_threshold WHERE patientid = ?", id);
                    jdbcTemplate.update("DELETE FROM appointment WHERE patientid = ?", id);

                    patientRepository.deleteById(id);
                    deletedCount++;
                }
            }

            if (deletedCount > 0) {
                System.out.println("Tiến trình hoàn tất. Đã tự động xóa " + deletedCount + " tài khoản.");
                SystemLog log = new SystemLog();
                log.setUsername("system");
                log.setAction("AUTO_SYNC_FIREBASE");
                log.setDetails("Tự động dọn dẹp " + deletedCount + " hồ sơ bệnh nhân không còn trên Firebase.");
                log.setTimestamp(LocalDateTime.now());
                systemLogRepository.save(log);
            } else {
                System.out.println("Tiến trình hoàn tất. Không tìm thấy tài khoản mồ côi nào.");
            }

        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình Auto Sync Firebase: " + e.getMessage());
        }
    }
}
