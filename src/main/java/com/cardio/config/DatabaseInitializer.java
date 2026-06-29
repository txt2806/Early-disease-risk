package com.cardio.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Override
    public void run(String... args) throws Exception {
        try {
            // Fix encoding of PreliminaryStatus column in Appointment table
            try {
                jdbcTemplate.execute("ALTER TABLE Appointment ALTER COLUMN PreliminaryStatus NVARCHAR(500)");
            } catch (Exception e) {
                log.warn("Could not alter Appointment table: {}", e.getMessage());
            }

            // Fix created_at column in Patient_Profile table if needed
            try {
                jdbcTemplate.execute("ALTER TABLE Patient_Profile ADD created_at DATETIME DEFAULT GETDATE()");
            } catch (Exception e) {
                // ignore if already exists
            }
            if (datasourceUrl != null && datasourceUrl.startsWith("jdbc:h2")) {
                createMissingTables();
            } else {
                log.info("Skipping manual table creation because the active datasource is not H2; relying on JPA schema update.");
                // Run SQL Server-specific alterations
                try {
                    jdbcTemplate.execute("ALTER TABLE Appointment ALTER COLUMN PreliminaryStatus NVARCHAR(500)");
                    log.info("Successfully altered Appointment.PreliminaryStatus to NVARCHAR(500) on SQL Server.");
                } catch (Exception e) {
                    log.warn("Could not alter Appointment.PreliminaryStatus: {}", e.getMessage());
                }
                
                // Set distinct created_at for existing patients to ensure sorting works immediately
                try {
                    for (int i = 0; i < 20; i++) {
                        String username = "patient" + (i + 1) + "@cardio.com";
                        java.time.LocalDateTime regTime = java.time.LocalDateTime.now().minusDays(20 - i).minusHours(i).minusMinutes(i * 2);
                        jdbcTemplate.update("UPDATE Patient_Profile SET created_at = ? WHERE Username = ? AND created_at IS NULL", 
                                java.sql.Timestamp.valueOf(regTime), username);
                    }
                    jdbcTemplate.update("UPDATE Patient_Profile SET created_at = ? WHERE created_at IS NULL", 
                            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                    log.info("Successfully updated existing patient created_at dates.");
                } catch (Exception e) {
                    log.warn("Could not update existing patient created_at: {}", e.getMessage());
                }
            }
            Integer adminCount = 0;
            Integer totalUsersCount = 0;
            Integer patientCount = 0;
            try {
                Integer staffCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Staff_Profile", Integer.class);
                Integer doctorCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Doctor_Profile", Integer.class);
                Integer pCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Patient_Profile", Integer.class);
                patientCount = pCount != null ? pCount : 0;
                totalUsersCount = (staffCount != null ? staffCount : 0) 
                                + (doctorCount != null ? doctorCount : 0) 
                                + patientCount;
            } catch (Exception e) {
                log.warn("Could not check if tables exist, proceeding with standard initialization. Error: {}",
                        e.getMessage());
            }

            if (totalUsersCount > 0 && patientCount >= 15) {
                log.info("Database is already seeded with at least 15 patients. Skipping purge and seeding to preserve existing data.");
                seedMedicalRecordsIfMissing();
                return;
            }

            log.info("Purging old database records to prepare clean seeding...");
            purgeOldRecords();

            log.info("Seeding clean accounts into PostgreSQL/Supabase/SQL Server...");
            seedCleanAccounts();

            log.info("Seeding medical records...");
            seedMedicalRecordsIfMissing();
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
                { "doctor01@cardio.com", "BS. Nguyễn Văn An", "Tim mạch", "CCHN-11111", "Phòng 101" },
                { "doctor02@cardio.com", "BS. Trần Văn Bình", "Tim mạch", "CCHN-22222", "Phòng 102" },
                { "doctor03@cardio.com", "BS. Lê Thị Chi", "Tim mạch", "CCHN-33333", "Phòng 103" },
                { "doctor04@cardio.com", "BS. Phạm Minh Đức", "Tim mạch", "CCHN-44444", "Phòng 104" }
        };
        for (String[] doc : doctors) {
            jdbcTemplate.update(
                    "INSERT INTO Doctor_Profile (Username, PasswordHash, FullName, Specialty, AlertThreshold_BPM, AlertThreshold_BP, LicenseNumber, RoomNumber, Status) "
                            +
                            "VALUES (?, ?, ?, ?, 100, '140/90', ?, ?, 'ACTIVE')",
                    doc[0], passHash, doc[1], doc[2], doc[3], doc[4]);
            syncUserWithFirebase(doc[0], "123", doc[1]);
        }

        // 3. Seed at least 3 Staff accounts in Staff_Profile
        String[][] staffMembers = {
                { "medicalstaff01@cardio.com", "Điều dưỡng Lê Thị Bình", "STAFF" },
                { "medicalstaff02@cardio.com", "Điều dưỡng Hoàng Văn Giang", "STAFF" },
                { "medicalstaff03@cardio.com", "Điều dưỡng Vũ Thị Hương", "STAFF" },
                { "medicalstaff04@cardio.com", "Điều dưỡng Phạm Thị Nhung", "STAFF" }
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

        // 5. Seed exactly 20 Patient accounts in Patient_Profile
        String[][] patients = {
                { "patient1@cardio.com", "Nguyễn Văn A", "1975-05-12", "Nam", "+84999999901", "Hà Nội" },
                { "patient2@cardio.com", "Trần Thị B", "1980-11-23", "Nữ", "+84999999902", "Hải Phòng" },
                { "patient3@cardio.com", "Lê Văn C", "1965-03-02", "Nam", "+84999999903", "Đà Nẵng" },
                { "patient4@cardio.com", "Phạm Thị D", "1990-07-15", "Nữ", "+84999999904", "TP HCM" },
                { "patient5@cardio.com", "Hoàng Văn E", "1985-09-30", "Nam", "+84999999905", "Cần Thơ" },
                { "patient6@cardio.com", "Ngô Thị F", "1970-12-05", "Nữ", "+84999999906", "Huế" },
                { "patient7@cardio.com", "Bùi Văn G", "1982-01-25", "Nam", "+84999999907", "Nha Trang" },
                { "patient8@cardio.com", "Hoàng Thị E", "1993-04-18", "Nữ", "+84999999908", "Cần Thơ" },
                { "patient9@cardio.com", "Vũ Văn F", "1968-09-05", "Nam", "+84999999909", "Nha Trang" },
                { "patient10@cardio.com", "Phan Thị G", "1991-12-14", "Nữ", "+84999999910", "Huế" },
                { "patient11@cardio.com", "Đỗ Văn H", "1985-02-28", "Nam", "+84999999911", "Vũng Tàu" },
                { "patient12@cardio.com", "Bùi Thị I", "1979-06-10", "Nữ", "+84999999912", "Đà Lạt" },
                { "patient13@cardio.com", "Đặng Văn K", "1998-10-22", "Nam", "+84999999913", "Quảng Ninh" },
                { "patient14@cardio.com", "Lâm Thị L", "1987-08-08", "Nữ", "+84999999914", "Nam Định" },
                { "patient15@cardio.com", "Mai Văn M", "1994-01-19", "Nam", "+84999999915", "Thanh Hóa" },
                { "patient16@cardio.com", "Ngô Thị N", "1972-05-24", "Nữ", "+84999999916", "Nghệ An" },
                { "patient17@cardio.com", "Dương Văn O", "1980-03-15", "Nam", "+84999999917", "Bình Dương" },
                { "patient18@cardio.com", "Lý Thị P", "1992-09-17", "Nữ", "+84999999918", "Đồng Nai" },
                { "patient19@cardio.com", "Đinh Văn Q", "1983-07-07", "Nam", "+84999999919", "Long An" },
                { "patient20@cardio.com", "Trịnh Thị R", "1970-11-11", "Nữ", "+84999999920", "Cà Mau" }
        };
        for (int i = 0; i < patients.length; i++) {
            String[] pat = patients[i];
            java.time.LocalDateTime regTime = java.time.LocalDateTime.now().minusDays(20 - i).minusHours(i).minusMinutes(i * 2);
            jdbcTemplate.update(
                    "INSERT INTO Patient_Profile (Username, PasswordHash, FullName, DOB, Gender, Phone, Address, Status, created_at) "
                            +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                    pat[0], passHash, pat[1], java.sql.Date.valueOf(pat[2]), pat[3], pat[4], pat[5], java.sql.Timestamp.valueOf(regTime));
            syncUserWithFirebase(pat[0], "123", pat[1]);
        }

        // 6. Seed some system logs for reports visualization
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String[][] sampleLogs = {
                { "admin@cardio.com", "LOGIN_SUCCESS", "Đăng nhập hệ thống quản trị thành công", "0" },
                { "doctor01@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Nguyễn Văn An đăng nhập thành công", "0" },
                { "doctor01@cardio.com", "CREATE_CONSULTATION", "Tạo hồ sơ khám cho bệnh nhân patient1@cardio.com",
                        "0" },
                { "medicalstaff01@cardio.com", "UPDATE_METRICS", "Cập nhật chỉ số lâm sàng cho bệnh nhân patient1@cardio.com",
                        "0" },
                { "system", "AI_PREDICTION_SUCCESS",
                        "AI hoàn thành dự đoán rủi ro tim mạch cho bệnh nhân patient1@cardio.com", "0" },

                { "admin@cardio.com", "CREATE_USER_SUCCESS", "Tạo tài khoản bác sĩ: doctor04@cardio.com", "1" },
                { "recep1@cardio.com", "CREATE_APPOINTMENT",
                        "Đặt lịch hẹn cho bệnh nhân patient2@cardio.com với BS Nguyễn Văn An", "1" },
                { "doctor02@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Trần Văn Bình đăng nhập thành công", "1" },
                { "doctor02@cardio.com", "VIEW_PATIENT_HISTORY", "Xem lịch sử bệnh án bệnh nhân patient2@cardio.com",
                        "1" },

                { "admin@cardio.com", "LOCK_USER", "Tạm khóa tài khoản bác sĩ nghỉ phép: doctor03@cardio.com", "2" },
                { "medicalstaff02@cardio.com", "LOGIN_SUCCESS", "Điều dưỡng Hoàng Văn Giang đăng nhập thành công", "2" },
                { "medicalstaff02@cardio.com", "UPDATE_METRICS", "Cập nhật chỉ số lâm sàng cho bệnh nhân patient3@cardio.com",
                        "2" },
                { "system", "AI_PREDICTION_SUCCESS", "AI hoàn thành dự đoán rủi ro cho bệnh nhân patient3@cardio.com",
                        "2" },

                { "recep2@cardio.com", "LOGIN_SUCCESS", "Lễ tân Ngô Thị Khánh đăng nhập hệ thống thành công", "3" },
                { "recep2@cardio.com", "CREATE_APPOINTMENT", "Đặt lịch hẹn tái khám cho bệnh nhân patient1@cardio.com",
                        "3" },
                { "doctor01@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Nguyễn Văn An đăng nhập thành công", "3" },
                { "doctor01@cardio.com", "UPDATE_TREATMENT_PLAN",
                        "Cập nhật phác đồ điều trị cho bệnh nhân patient1@cardio.com", "3" },

                { "admin@cardio.com", "LOGIN_SUCCESS", "Đăng nhập hệ thống quản trị", "4" },
                { "admin@cardio.com", "RESET_PASSWORD_PATIENT",
                        "Đặt lại mật khẩu cho bệnh nhân patient2@cardio.com theo yêu cầu", "4" },
                { "doctor03@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Lê Thị Chi đăng nhập thành công", "4" },

                { "recep3@cardio.com", "LOGIN_SUCCESS", "Lễ tân Bùi Văn Long đăng nhập thành công", "5" },
                { "recep3@cardio.com", "CREATE_APPOINTMENT", "Đặt lịch hẹn mới cho bệnh nhân patient3@cardio.com",
                        "5" },
                { "doctor02@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Trần Văn Bình đăng nhập thành công", "5" },

                { "admin@cardio.com", "UNLOCK_USER", "Mở khóa hoạt động tài khoản bác sĩ: doctor03@cardio.com", "6" },
                { "doctor01@cardio.com", "LOGIN_SUCCESS", "Bác sĩ Nguyễn Văn An đăng nhập thành công", "6" },
                { "doctor01@cardio.com", "CREATE_CONSULTATION", "Tạo hồ sơ khám mới cho bệnh nhân patient3@cardio.com",
                        "6" }
        };

        String timestampCol = "Timestamp";
        try {
            String dbProduct = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName();
            if (dbProduct.toLowerCase().contains("microsoft")) {
                timestampCol = "[Timestamp]";
            }
        } catch (Exception e) {
            // Ignore
        }
        for (String[] logData : sampleLogs) {
            String username = logData[0];
            String action = logData[1];
            String details = logData[2];
            int daysAgo = Integer.parseInt(logData[3]);
            java.time.LocalDateTime logTime = now.minusDays(daysAgo).minusHours(daysAgo * 2).minusMinutes(daysAgo * 5);

            jdbcTemplate.update(
                    "INSERT INTO System_Log (Username, Action, Details, " + timestampCol + ") VALUES (?, ?, ?, ?)",
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
        }
    }

    private void createMissingTables() {
        try {
            String dbProduct = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName();
            if (!dbProduct.toLowerCase().contains("h2")) {
                log.info("Database is {}, skipping H2-specific table checks.", dbProduct);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not determine database product name, proceeding with table checks: {}", e.getMessage());
        }
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
                    "TimeSlot TIME, " +
                    "EndTime TIME, " +
                    "RequestTime TIMESTAMP, " +
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
    private void seedMedicalRecordsIfMissing() {
        try {
            // 1. Seed ICD_Catalog
            Integer icdCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ICD_Catalog", Integer.class);
            if (icdCount == null || icdCount == 0) {
                log.info("Seeding ICD_Catalog...");
                String[][] icdCatalog = {
                    {"I10", "Bệnh cao huyết áp vô căn (hypertension)"},
                    {"I20", "Cơn đau thắt ngực (angina pectoris)"},
                    {"I21", "Nhồi máu cơ tim cấp (acute myocardial infarction)"},
                    {"I25", "Bệnh cơ tim thiếu máu cục bộ mạn tính (ischemic heart disease)"},
                    {"I48", "Rung nhĩ và cuồng nhĩ (atrial fibrillation)"},
                    {"I50", "Suy tim (heart failure)"}
                };
                for (String[] icd : icdCatalog) {
                    jdbcTemplate.update("INSERT INTO ICD_Catalog (ICDCode, DiseaseName) VALUES (?, ?)", icd[0], icd[1]);
                }
            }

            // 2. Check if Consultation_Record is empty or low in records
            Integer recordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Consultation_Record", Integer.class);
            if (recordCount != null && recordCount >= 15) {
                log.info("Consultation records already exist. Skipping medical record seeding.");
                return;
            }

            log.info("Seeding medical records for existing patients...");
            // Clean old medical records to prevent key conflicts if we are rebuilding
            jdbcTemplate.execute("DELETE FROM Record_ICD");
            jdbcTemplate.execute("DELETE FROM Heart_Clinical_Metrics");
            jdbcTemplate.execute("DELETE FROM AI_Risk_Prediction");
            jdbcTemplate.execute("DELETE FROM Consultation_Record");

            // Query all patient details ordered by PatientID
            java.util.List<java.util.Map<String, Object>> patientsInDb = jdbcTemplate.queryForList(
                    "SELECT PatientID, DOB, Gender FROM Patient_Profile ORDER BY PatientID");
            if (patientsInDb.isEmpty()) {
                log.warn("No patients found in DB. Skipping medical record seeding.");
                return;
            }

            // Seed specific realistic medical records for up to 20 patients
            // We define data parameters for each index of patient (0 to 19)
            Object[][] recordsData = {
                {0, 1, 5, 145, 250, 1, 1, 130, 1, 2.0, "flat", 1, "reversable defect"},
                {1, 2, 4, 115, 180, 0, 0, 168, 0, 0.0, "upsloping", 0, "normal"},
                {2, 1, 6, 155, 270, 0, 2, 125, 1, 2.5, "flat", 2, "reversable defect"},
                {3, 3, 3, 110, 190, 0, 0, 175, 0, 0.0, "upsloping", 0, "normal"},
                {4, 4, 2, 135, 225, 0, 1, 145, 0, 1.0, "flat", 0, "fixed defect"},
                {5, 2, 7, 130, 240, 1, 0, 140, 0, 1.2, "flat", 1, "fixed defect"},
                {6, 3, 3, 150, 260, 1, 1, 135, 1, 1.8, "flat", 2, "reversable defect"},
                {7, 4, 8, 112, 185, 0, 0, 170, 0, 0.0, "upsloping", 0, "normal"},
                {8, 1, 10, 140, 280, 0, 2, 120, 1, 2.2, "downsloping", 2, "reversable defect"},
                {9, 2, 9, 118, 195, 0, 0, 165, 0, 0.5, "upsloping", 0, "normal"},
                {10, 3, 1, 128, 210, 0, 0, 152, 0, 0.8, "flat", 0, "normal"},
                {11, 4, 5, 132, 230, 0, 1, 148, 1, 1.4, "flat", 1, "fixed defect"},
                {12, 1, 3, 114, 175, 0, 0, 180, 0, 0.0, "upsloping", 0, "normal"},
                {13, 2, 6, 116, 188, 0, 0, 172, 0, 0.0, "upsloping", 0, "normal"},
                {14, 3, 2, 120, 192, 0, 0, 160, 0, 0.2, "upsloping", 0, "normal"},
                {15, 4, 4, 148, 290, 1, 2, 128, 1, 2.4, "flat", 2, "reversable defect"},
                {16, 1, 10, 138, 245, 0, 1, 142, 1, 1.5, "flat", 1, "fixed defect"},
                {17, 2, 9, 110, 182, 0, 0, 176, 0, 0.0, "upsloping", 0, "normal"},
                {18, 3, 6, 134, 218, 0, 1, 150, 0, 0.6, "flat", 0, "normal"},
                {19, 4, 8, 142, 275, 0, 2, 132, 1, 1.9, "flat", 1, "reversable defect"}
            };

            for (int i = 0; i < patientsInDb.size(); i++) {
                java.util.Map<String, Object> patMap = patientsInDb.get(i);
                Integer patientId = (Integer) patMap.get("PatientID");
                Object dobObj = patMap.get("DOB");
                String gender = (String) patMap.get("Gender");

                Integer age = null;
                if (dobObj != null) {
                    java.time.LocalDate dobLocalDate = null;
                    if (dobObj instanceof java.sql.Date) {
                        dobLocalDate = ((java.sql.Date) dobObj).toLocalDate();
                    } else if (dobObj instanceof java.time.LocalDate) {
                        dobLocalDate = (java.time.LocalDate) dobObj;
                    }
                    if (dobLocalDate != null) {
                        age = java.time.Period.between(dobLocalDate, java.time.LocalDate.now()).getYears();
                    }
                }

                String sex = null;
                if (gender != null) {
                    sex = "Nam".equalsIgnoreCase(gender) || "Male".equalsIgnoreCase(gender) ? "Male" : "Female";
                }

                // Determine number of records for this patient: between 1 and 4
                int numRecords = (i % 4) + 1;

                for (int j = 0; j < numRecords; j++) {
                    // Stagger mock data selection to ensure different metrics per visit
                    Object[] data = recordsData[(i * 3 + j) % recordsData.length];
                    Integer docId = (Integer) data[1];
                    int baseDaysAgo = (Integer) data[2];
                    // Stagger visit dates (e.g. 45 days apart for previous visits)
                    int daysAgo = baseDaysAgo + j * 45;

                    String docUsername = "doctor" + docId + "@cardio.com";
                    Integer realDocId = null;
                    try {
                        realDocId = jdbcTemplate.queryForObject("SELECT DoctorID FROM Doctor_Profile WHERE Username = ?", Integer.class, docUsername);
                    } catch (Exception e) {
                        realDocId = docId;
                    }

                    java.time.LocalDateTime visitDate = java.time.LocalDateTime.now().minusDays(daysAgo).minusHours(daysAgo % 24).minusMinutes((daysAgo * 3) % 60);
                    jdbcTemplate.update("INSERT INTO Consultation_Record (PatientID, DoctorID, VisitDate, ConsultationNotes, TreatmentPlan, Status) VALUES (?, ?, ?, NULL, NULL, 'Pending')",
                            patientId, realDocId, java.sql.Timestamp.valueOf(visitDate));
                    
                    Integer recordId = jdbcTemplate.queryForObject("SELECT MAX(RecordID) FROM Consultation_Record", Integer.class);
                    if (recordId == null) continue;

                    boolean fbsVal = (Integer) data[5] == 1;
                    boolean exangVal = (Integer) data[8] == 1;
                    
                    jdbcTemplate.update("INSERT INTO Heart_Clinical_Metrics (RecordID, ChestPainType, RestingBP, Cholesterol, FastingBloodSugar, RestingECG, MaxHeartRate, ExerciseAngina, RecordedAt, Temperature, SpO2, BloodTest, UrineTest, Xray, Ultrasound, Age, Sex) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 36.5, 98, ?, ?, ?, ?, ?, ?)",
                            recordId, data[2], data[3], data[4], fbsVal, data[6], data[7], exangVal, java.sql.Timestamp.valueOf(visitDate),
                            "Glucose: 5.6 mmol/L, Cholesterol: 5.2 mmol/L", "Bình thường", "Bóng tim bình thường", "Siêu âm tim bình thường",
                            age, sex);

                    try {
                        jdbcTemplate.update("UPDATE Heart_Clinical_Metrics SET Oldpeak = ?, Slope = ?, Ca = ?, Thal = ? WHERE RecordID = ?",
                                data[9], data[10], data[11], data[12], recordId);
                    } catch (Exception ex) {
                        log.warn("Could not update additional clinical metrics: {}", ex.getMessage());
                    }
                }
            }
            log.info("Successfully seeded medical records for all patients.");
        } catch (Exception e) {
            log.error("Failed to seed medical records: ", e);
        }
    }
}
