package com.cardio.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            createMissingTables();
<<<<<<< Updated upstream
            log.info("Database initialization check completed successfully.");
        } catch (Exception e) {
            log.error("Error during database initialization check: ", e);
=======
            // Check if database is already seeded (e.g., admin exists in Staff_Profile)
            Integer adminCount = 0;
            try {
                adminCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM Staff_Profile WHERE Username = 'admin@cardio.com'",
                        Integer.class);
            } catch (Exception e) {
                log.warn("Could not check if Staff_Profile exists, proceeding with standard initialization. Error: {}",
                        e.getMessage());
            }

            if (adminCount != null && adminCount > 0) {
                log.info(
                        "Database is already seeded with admin account. Skipping purge and seeding to preserve existing data.");
                return;
            }

            log.info("Purging old database records to prepare clean seeding...");
            purgeOldRecords();

            log.info("Seeding clean accounts into SQL Server...");
            seedCleanAccounts();

            log.info("Database seeding and Firebase sync completed successfully.");
        } catch (Exception e) {
            log.error("Error during database initialization: ", e);
        }
    }

    private void purgeOldRecords() {
        String[] deleteSqls = {
                "DELETE FROM Record_ICD",
                "DELETE FROM Heart_Clinical_Metrics",
                "DELETE FROM AI_Risk_Prediction",
                "DELETE FROM Patient_Self_Monitoring",
                "DELETE FROM Appointment",
                "DELETE FROM Consultation_Record",
                "DELETE FROM System_Log",
                "DELETE FROM Patient_Profile",
                "DELETE FROM Doctor_Profile",
                "DELETE FROM Staff_Profile"
        };
        for (String sql : deleteSqls) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Skipped statement during purge: {} ({})", sql, e.getMessage());
            }
        }
    }

    private void seedCleanAccounts() {
        String passHash = passwordEncoder.encode("123");

        // 1. Seed exactly 1 Admin account in Staff_Profile
        jdbcTemplate.update("INSERT INTO Staff_Profile (Username, PasswordHash, FullName, Role, Status) " +
                "VALUES ('admin@cardio.com', ?, 'System Administrator', 'ADMIN', 'ACTIVE')", passHash);
        syncUserWithFirebase("admin@cardio.com", "123", "System Administrator");

        // 2. Seed at least 4 Doctor accounts in Doctor_Profile
        String[][] doctors = {
                { "doctor1@cardio.com", "BS. Nguyễn Văn An", "Tim mạch", "CCHN-11111" },
                { "doctor2@cardio.com", "BS. Trần Văn Bình", "Tim mạch", "CCHN-22222" },
                { "doctor3@cardio.com", "BS. Lê Thị Chi", "Tim mạch", "CCHN-33333" },
                { "doctor4@cardio.com", "BS. Phạm Minh Đức", "Tim mạch", "CCHN-44444" }
        };
        for (String[] doc : doctors) {
            jdbcTemplate.update(
                    "INSERT INTO Doctor_Profile (Username, PasswordHash, FullName, Specialty, AlertThreshold_BPM, AlertThreshold_BP, LicenseNumber, Status) "
                            +
                            "VALUES (?, ?, ?, ?, 100, '140/90', ?, 'ACTIVE')",
                    doc[0], passHash, doc[1], doc[2], doc[3]);
            syncUserWithFirebase(doc[0], "123", doc[1]);
        }

        // 3. Seed at least 3 Staff accounts in Staff_Profile
        String[][] staffMembers = {
                { "staff1@cardio.com", "Điều dưỡng Lê Thị Bình", "STAFF" },
                { "staff2@cardio.com", "Điều dưỡng Hoàng Văn Giang", "STAFF" },
                { "staff3@cardio.com", "Điều dưỡng Vũ Thị Hương", "STAFF" }
        };
        for (String[] st : staffMembers) {
            jdbcTemplate.update("INSERT INTO Staff_Profile (Username, PasswordHash, FullName, Role, Status) " +
                    "VALUES (?, ?, ?, ?, 'ACTIVE')", st[0], passHash, st[1], st[2]);
            syncUserWithFirebase(st[0], "123", st[1]);
        }

        // 4. Seed at least 3 Receptionist accounts in Staff_Profile
        String[][] receptionists = {
                { "recep1@cardio.com", "Lễ tân Trần Văn Cường", "RECEPTIONIST" },
                { "recep2@cardio.com", "Lễ tân Ngô Thị Khánh", "RECEPTIONIST" },
                { "recep3@cardio.com", "Lễ tân Bùi Văn Long", "RECEPTIONIST" }
        };
        for (String[] recep : receptionists) {
            jdbcTemplate.update("INSERT INTO Staff_Profile (Username, PasswordHash, FullName, Role, Status) " +
                    "VALUES (?, ?, ?, ?, 'ACTIVE')", recep[0], passHash, recep[1], recep[2]);
            syncUserWithFirebase(recep[0], "123", recep[1]);
        }

        // 5. Seed at least 3 Patient accounts in Patient_Profile
        String[][] patients = {
                { "patient1@cardio.com", "Bệnh nhân Nguyễn Văn Test", "2000-01-01", "Nam", "+84999999991", "Hà Nội" },
                { "patient2@cardio.com", "Bệnh nhân Trần Thị Mai", "1995-05-15", "Nữ", "+84999999992", "Đà Nẵng" },
                { "patient3@cardio.com", "Bệnh nhân Lê Hoàng Nam", "1988-08-20", "Nam", "+84999999993",
                        "TP. Hồ Chí Minh" }
        };
        for (String[] pat : patients) {
            jdbcTemplate.update(
                    "INSERT INTO Patient_Profile (Username, PasswordHash, FullName, DOB, Gender, Phone, Address, Status) "
                            +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                    pat[0], passHash, pat[1], java.sql.Date.valueOf(pat[2]), pat[3], pat[4], pat[5]);
            syncUserWithFirebase(pat[0], "123", pat[1]);
        }

        // 6. Seed some system logs for reports visualization
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String[][] sampleLogs = {
                { "admin@cardio.com", "LOGIN_SUCCESS", "Đăng nhập hệ thống quản trị thành công", "0" },
                { "doctor1@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Nguyễn Văn An đăng nhập thành công", "0" },
                { "doctor1@cardio.com", "CREATE_CONSULTATION", "Tạo hồ sơ khám cho bệnh nhân patient1@cardio.com",
                        "0" },
                { "staff1@cardio.com", "UPDATE_METRICS", "Cập nhật chỉ số lâm sàng cho bệnh nhân patient1@cardio.com",
                        "0" },
                { "system", "AI_PREDICTION_SUCCESS",
                        "AI hoàn thành dự đoán rủi ro tim mạch cho bệnh nhân patient1@cardio.com", "0" },

                { "admin@cardio.com", "CREATE_USER_SUCCESS", "Tạo tài khoản bác sĩ: doctor4@cardio.com", "1" },
                { "recep1@cardio.com", "CREATE_APPOINTMENT",
                        "Đặt lịch hẹn cho bệnh nhân patient2@cardio.com với BS Nguyễn Văn An", "1" },
                { "doctor2@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Trần Văn Bình đăng nhập thành công", "1" },
                { "doctor2@cardio.com", "VIEW_PATIENT_HISTORY", "Xem lịch sử bệnh án bệnh nhân patient2@cardio.com",
                        "1" },

                { "admin@cardio.com", "LOCK_USER", "Tạm khóa tài khoản bác sĩ nghỉ phép: doctor3@cardio.com", "2" },
                { "staff2@cardio.com", "LOGIN_SUCCESS", "Điều dưỡng Hoàng Văn Giang đăng nhập thành công", "2" },
                { "staff2@cardio.com", "UPDATE_METRICS", "Cập nhật chỉ số lâm sàng cho bệnh nhân patient3@cardio.com",
                        "2" },
                { "system", "AI_PREDICTION_SUCCESS", "AI hoàn thành dự đoán rủi ro cho bệnh nhân patient3@cardio.com",
                        "2" },

                { "recep2@cardio.com", "LOGIN_SUCCESS", "Lễ tân Ngô Thị Khánh đăng nhập hệ thống thành công", "3" },
                { "recep2@cardio.com", "CREATE_APPOINTMENT", "Đặt lịch hẹn tái khám cho bệnh nhân patient1@cardio.com",
                        "3" },
                { "doctor1@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Nguyễn Văn An đăng nhập thành công", "3" },
                { "doctor1@cardio.com", "UPDATE_TREATMENT_PLAN",
                        "Cập nhật phác đồ điều trị cho bệnh nhân patient1@cardio.com", "3" },

                { "admin@cardio.com", "LOGIN_SUCCESS", "Đăng nhập hệ thống quản trị", "4" },
                { "admin@cardio.com", "RESET_PASSWORD_PATIENT",
                        "Đặt lại mật khẩu cho bệnh nhân patient2@cardio.com theo yêu cầu", "4" },
                { "doctor3@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Lê Thị Chi đăng nhập thành công", "4" },

                { "recep3@cardio.com", "LOGIN_SUCCESS", "Lễ tân Bùi Văn Long đăng nhập thành công", "5" },
                { "recep3@cardio.com", "CREATE_APPOINTMENT", "Đặt lịch hẹn mới cho bệnh nhân patient3@cardio.com",
                        "5" },
                { "doctor2@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Trần Văn Bình đăng nhập thành công", "5" },

                { "admin@cardio.com", "UNLOCK_USER", "Mở khóa hoạt động tài khoản bác sĩ: doctor3@cardio.com", "6" },
                { "doctor1@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Nguyễn Văn An đăng nhập thành công", "6" },
                { "doctor1@cardio.com", "CREATE_CONSULTATION", "Tạo hồ sơ khám mới cho bệnh nhân patient3@cardio.com",
                        "6" }
        };

        for (String[] logData : sampleLogs) {
            String username = logData[0];
            String action = logData[1];
            String details = logData[2];
            int daysAgo = Integer.parseInt(logData[3]);
            java.time.LocalDateTime logTime = now.minusDays(daysAgo).minusHours(daysAgo * 2).minusMinutes(daysAgo * 5);

            jdbcTemplate.update(
                    "INSERT INTO System_Log (Username, Action, Details, Timestamp) VALUES (?, ?, ?, ?)",
                    username, action, details, logTime);
        }
    }

    private void syncUserWithFirebase(String email, String password, String fullName) {
        try {
            // Check if Firebase application is initialized
            if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
                log.warn("Firebase App is not initialized. Skipping Firebase sync for: " + email);
                return;
            }

            // 1. Delete existing Firebase Auth user if present
            try {
                UserRecord existingUser = FirebaseAuth.getInstance().getUserByEmail(email);
                if (existingUser != null) {
                    FirebaseAuth.getInstance().deleteUser(existingUser.getUid());
                    log.info("Deleted existing Firebase user for re-sync: " + email);
                }
            } catch (Exception e) {
                // Not found, skip delete
            }

            // 2. Create and mark as email verified (sync & verify!)
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(fullName)
                    .setEmailVerified(true);

            UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
            log.info("Successfully synced & verified Firebase account for: " + email + " (UID: " + userRecord.getUid()
                    + ")");
        } catch (Exception e) {
            log.warn("Firebase Auth sync warning for " + email + ": " + e.getMessage());
>>>>>>> Stashed changes
        }
    }

    private void createMissingTables() {
        log.info("Creating missing tables for H2 compatibility if not exist...");

        // 1. ICD_Catalog
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ICD_Catalog (" +
                    "ICDCode VARCHAR(20) PRIMARY KEY, " +
                    "DiseaseName VARCHAR(255) NOT NULL)");
            log.info("Table ICD_Catalog is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create ICD_Catalog: {}", e.getMessage());
        }

        // 2. Appointment
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Appointment (" +
                    "AppointmentID SERIAL PRIMARY KEY, " +
                    "PatientID INT, " +
                    "DoctorID INT, " +
                    "ScheduledDate DATE NOT NULL, " +
                    "TimeSlot TIME NOT NULL, " +
                    "Status VARCHAR(20) DEFAULT 'Pending', " +
                    "RoomNumber VARCHAR(50), " +
                    "PreliminaryStatus TEXT)");

            // Safe ALTER TABLE commands in case the table exists without the new columns
            try {
                jdbcTemplate.execute("ALTER TABLE Appointment ADD COLUMN IF NOT EXISTS RoomNumber VARCHAR(50)");
                jdbcTemplate.execute("ALTER TABLE Appointment ADD COLUMN IF NOT EXISTS PreliminaryStatus TEXT");
            } catch (Exception alterEx) {
                log.warn("Failed to alter Appointment table: {}", alterEx.getMessage());
            }

            log.info("Table Appointment is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Appointment: {}", e.getMessage());
        }

        // 3. Heart_Clinical_Metrics
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Heart_Clinical_Metrics (" +
                    "MetricID SERIAL PRIMARY KEY, " +
                    "RecordID INT UNIQUE, " +
                    "RecordedBy_StaffID INT, " +
                    "ChestPainType INT, " +
                    "RestingBP INT, " +
                    "Cholesterol INT, " +
                    "FastingBloodSugar BOOLEAN, " +
                    "RestingECG INT, " +
                    "MaxHeartRate INT, " +
                    "ExerciseAngina BOOLEAN, " +
                    "RecordedAt TIMESTAMP NOT NULL)");
            log.info("Table Heart_Clinical_Metrics is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Heart_Clinical_Metrics: {}", e.getMessage());
        }

        // 4. Patient_Self_Monitoring
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Patient_Self_Monitoring (" +
                    "LogID SERIAL PRIMARY KEY, " +
                    "PatientID INT, " +
                    "LogDate TIMESTAMP NOT NULL, " +
                    "CurrentHeartRate INT, " +
                    "Symptoms VARCHAR(255), " +
                    "TriggeredAlert BOOLEAN DEFAULT FALSE)");
            log.info("Table Patient_Self_Monitoring is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Patient_Self_Monitoring: {}", e.getMessage());
        }

        // 5. Record_ICD
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS Record_ICD (" +
                    "RecordID INT, " +
                    "ICDCode VARCHAR(20), " +
                    "Notes VARCHAR(255), " +
                    "PRIMARY KEY (RecordID, ICDCode))");
            log.info("Table Record_ICD is checked/created.");
        } catch (Exception e) {
            log.warn("Failed to create Record_ICD: {}", e.getMessage());
        }
    }
}
