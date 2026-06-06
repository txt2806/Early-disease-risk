-- 0. Dọn dẹp các bảng cũ nếu tồn tại (để tránh xung đột cấu trúc)
DROP TABLE IF EXISTS "Record_ICD" CASCADE;
DROP TABLE IF EXISTS "ICD_Catalog" CASCADE;
DROP TABLE IF EXISTS "Patient_Self_Monitoring" CASCADE;
DROP TABLE IF EXISTS "AI_Risk_Prediction" CASCADE;
DROP TABLE IF EXISTS "Heart_Clinical_Metrics" CASCADE;
DROP TABLE IF EXISTS "Consultation_Record" CASCADE;
DROP TABLE IF EXISTS "Appointment" CASCADE;
DROP TABLE IF EXISTS "Patient_Profile" CASCADE;
DROP TABLE IF EXISTS "Doctor_Profile" CASCADE;
DROP TABLE IF EXISTS "Staff_Profile" CASCADE;
DROP TABLE IF EXISTS "System_Log" CASCADE;
DROP TABLE IF EXISTS "app_users" CASCADE;

-- 0. Tạo bảng Tài khoản người dùng (tổng hợp đăng nhập)
CREATE TABLE IF NOT EXISTS "app_users" (
    "UserID"       SERIAL PRIMARY KEY,
    "Username"     VARCHAR(50)  NOT NULL UNIQUE,
    "PasswordHash" VARCHAR(255) NOT NULL,
    "FullName"     VARCHAR(100) NOT NULL,
    "Role"         VARCHAR(50)  NOT NULL,
    "Status"       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    "CreatedAt"    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Seed tài khoản Admin (mật khẩu: 123, mã hoá BCrypt)
INSERT INTO "app_users" ("Username", "PasswordHash", "FullName", "Role")
VALUES (
    'admin@cardio.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'System Administrator',
    'ADMIN'
) ON CONFLICT ("Username") DO NOTHING;

-- 1. Tạo bảng Hồ sơ Nhân sự (Admin, Lễ tân, Điều dưỡng)
CREATE TABLE IF NOT EXISTS "Staff_Profile" (
    "StaffID"      SERIAL PRIMARY KEY,
    "Username"     VARCHAR(50)  NOT NULL UNIQUE,
    "PasswordHash" VARCHAR(255) NOT NULL,
    "FullName"     VARCHAR(100) NOT NULL,
    "Role"         VARCHAR(50)  NOT NULL,
    "Status"       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
);

-- 2. Tạo bảng Hồ sơ Bác sĩ
CREATE TABLE IF NOT EXISTS "Doctor_Profile" (
    "DoctorID"           SERIAL PRIMARY KEY,
    "Username"           VARCHAR(50)  NOT NULL UNIQUE,
    "PasswordHash"       VARCHAR(255) NOT NULL,
    "FullName"           VARCHAR(100) NOT NULL,
    "Specialty"          VARCHAR(100) DEFAULT 'Tim mạch',
    "AlertThreshold_BPM" INT          DEFAULT 100,
    "AlertThreshold_BP"  VARCHAR(20)  DEFAULT '140/90',
    "LicenseNumber"      VARCHAR(50)  NULL,
    "Status"             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
);

-- 3. Tạo bảng Hồ sơ Bệnh nhân
CREATE TABLE IF NOT EXISTS "Patient_Profile" (
    "PatientID"    SERIAL PRIMARY KEY,
    "Username"     VARCHAR(50)  NOT NULL UNIQUE,
    "PasswordHash" VARCHAR(255) NOT NULL,
    "FullName"     VARCHAR(100) NOT NULL,
    "DOB"          DATE         NOT NULL,
    "Gender"       VARCHAR(10)  NOT NULL,
    "Phone"        VARCHAR(15),
    "Address"      VARCHAR(255),
    "Status"       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
);

-- 4. Tạo bảng Lịch hẹn (chặn trùng lịch bác sĩ)
CREATE TABLE IF NOT EXISTS "Appointment" (
    "AppointmentID" SERIAL PRIMARY KEY,
    "PatientID"     INT,
    "DoctorID"      INT,
    "ScheduledDate" DATE        NOT NULL,
    "TimeSlot"      TIME        NOT NULL,
    "Status"        VARCHAR(20) NOT NULL DEFAULT 'Pending',
    FOREIGN KEY ("PatientID") REFERENCES "Patient_Profile"("PatientID"),
    FOREIGN KEY ("DoctorID")  REFERENCES "Doctor_Profile"("DoctorID"),
    CONSTRAINT "uq_appointment_doctor_slot" UNIQUE ("DoctorID", "ScheduledDate", "TimeSlot")
);

-- 5. Tạo bảng Hồ sơ Tư vấn & Khám bệnh
CREATE TABLE IF NOT EXISTS "Consultation_Record" (
    "RecordID"          SERIAL PRIMARY KEY,
    "PatientID"         INT,
    "DoctorID"          INT,
    "VisitDate"         TIMESTAMP   NOT NULL,
    "ConsultationNotes" TEXT,
    "TreatmentPlan"     TEXT,
    "Status"            VARCHAR(20) DEFAULT 'Completed',
    FOREIGN KEY ("PatientID") REFERENCES "Patient_Profile"("PatientID"),
    FOREIGN KEY ("DoctorID")  REFERENCES "Doctor_Profile"("DoctorID")
);

-- 6. Tạo bảng Chỉ số Lâm sàng Tim mạch (1-1 với Hồ sơ khám)
CREATE TABLE IF NOT EXISTS "Heart_Clinical_Metrics" (
    "MetricID"           SERIAL PRIMARY KEY,
    "RecordID"           INT UNIQUE,
    "RecordedBy_StaffID" INT,
    "ChestPainType"      INT,
    "RestingBP"          INT,
    "Cholesterol"        INT,
    "FastingBloodSugar"  BOOLEAN,
    "RestingECG"         INT,
    "MaxHeartRate"        INT,
    "ExerciseAngina"     BOOLEAN,
    "RecordedAt"         TIMESTAMP NOT NULL,
    FOREIGN KEY ("RecordID")           REFERENCES "Consultation_Record"("RecordID"),
    FOREIGN KEY ("RecordedBy_StaffID") REFERENCES "Staff_Profile"("StaffID")
);

-- 7. Tạo bảng Kết quả Dự đoán Rủi ro AI (1-1 với Hồ sơ khám)
CREATE TABLE IF NOT EXISTS "AI_Risk_Prediction" (
    "PredictionID"    SERIAL PRIMARY KEY,
    "RecordID"        INT UNIQUE,
    "RiskScore"       DECIMAL(5,2),
    "RiskLevel"       VARCHAR(20),
    "RiskExplanation" TEXT,
    "HealthAdvice"    TEXT,
    "DietaryAdvice"   TEXT,
    "IsAlertSent"     BOOLEAN DEFAULT FALSE,
    FOREIGN KEY ("RecordID") REFERENCES "Consultation_Record"("RecordID")
);

-- 8. Tạo bảng Bệnh nhân Tự theo dõi
CREATE TABLE IF NOT EXISTS "Patient_Self_Monitoring" (
    "LogID"            SERIAL PRIMARY KEY,
    "PatientID"        INT,
    "LogDate"          TIMESTAMP NOT NULL,
    "CurrentHeartRate" INT,
    "Symptoms"         TEXT,
    "TriggeredAlert"   BOOLEAN DEFAULT FALSE,
    FOREIGN KEY ("PatientID") REFERENCES "Patient_Profile"("PatientID")
);

-- 9. Tạo bảng Danh mục Mã bệnh ICD
CREATE TABLE IF NOT EXISTS "ICD_Catalog" (
    "ICDCode"     VARCHAR(20) PRIMARY KEY,
    "DiseaseName" VARCHAR(255) NOT NULL
);

-- 10. Tạo bảng trung gian Chi tiết bệnh lý tư vấn
CREATE TABLE IF NOT EXISTS "Record_ICD" (
    "RecordID" INT,
    "ICDCode"  VARCHAR(20),
    "Notes"    TEXT,
    PRIMARY KEY ("RecordID", "ICDCode"),
    FOREIGN KEY ("RecordID") REFERENCES "Consultation_Record"("RecordID"),
    FOREIGN KEY ("ICDCode")  REFERENCES "ICD_Catalog"("ICDCode")
);

-- 11. Tạo bảng System Log
CREATE TABLE IF NOT EXISTS "System_Log" (
    "LogID"     SERIAL PRIMARY KEY,
    "Username"  VARCHAR(255) NOT NULL,
    "Action"    VARCHAR(255) NOT NULL,
    "Details"   TEXT,
    "Timestamp" TIMESTAMP    NOT NULL DEFAULT NOW()
);
