-- 0. Dọn dẹp các bảng cũ nếu tồn tại (để tránh xung đột cấu trúc)
-- Đối với SQL Server, ta cần xóa theo đúng thứ tự ràng buộc khóa ngoại
IF OBJECT_ID('dbo.Record_ICD', 'U') IS NOT NULL DROP TABLE dbo.Record_ICD;
IF OBJECT_ID('dbo.ICD_Catalog', 'U') IS NOT NULL DROP TABLE dbo.ICD_Catalog;
IF OBJECT_ID('dbo.Patient_Self_Monitoring', 'U') IS NOT NULL DROP TABLE dbo.Patient_Self_Monitoring;
IF OBJECT_ID('dbo.AI_Risk_Prediction', 'U') IS NOT NULL DROP TABLE dbo.AI_Risk_Prediction;
IF OBJECT_ID('dbo.Heart_Clinical_Metrics', 'U') IS NOT NULL DROP TABLE dbo.Heart_Clinical_Metrics;
IF OBJECT_ID('dbo.Consultation_Record', 'U') IS NOT NULL DROP TABLE dbo.Consultation_Record;
IF OBJECT_ID('dbo.Appointment', 'U') IS NOT NULL DROP TABLE dbo.Appointment;
IF OBJECT_ID('dbo.Patient_Profile', 'U') IS NOT NULL DROP TABLE dbo.Patient_Profile;
IF OBJECT_ID('dbo.Doctor_Profile', 'U') IS NOT NULL DROP TABLE dbo.Doctor_Profile;
IF OBJECT_ID('dbo.Staff_Profile', 'U') IS NOT NULL DROP TABLE dbo.Staff_Profile;
IF OBJECT_ID('dbo.System_Log', 'U') IS NOT NULL DROP TABLE dbo.System_Log;
GO

-- 1. Tạo bảng Hồ sơ Nhân sự (Admin, Lễ tân, Điều dưỡng)
CREATE TABLE Staff_Profile (
    StaffID INT IDENTITY(1,1) PRIMARY KEY,
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    Role VARCHAR(50) NOT NULL,
    Status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
GO

-- 2. Tạo bảng Hồ sơ Bác sĩ
CREATE TABLE Doctor_Profile (
    DoctorID INT IDENTITY(1,1) PRIMARY KEY,
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    Specialty NVARCHAR(100) DEFAULT N'Tim mạch',
    AlertThreshold_BPM INT DEFAULT 100,
    AlertThreshold_BP VARCHAR(20) DEFAULT '140/90',
    LicenseNumber VARCHAR(50) NULL,
    Status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
GO

-- 3. Tạo bảng Hồ sơ Bệnh nhân (Đã tích hợp cột is_alert)
CREATE TABLE Patient_Profile (
    PatientID INT IDENTITY(1,1) PRIMARY KEY,
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    DOB DATE NOT NULL,
    Gender NVARCHAR(10) NOT NULL,
    Phone VARCHAR(15),
    Address NVARCHAR(255),
    Status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_alert INT DEFAULT 0
);
GO

-- 4. Tạo bảng Lịch hẹn (Đã có Status và chặn trùng lịch bác sĩ)
CREATE TABLE Appointment (
    AppointmentID INT IDENTITY(1,1) PRIMARY KEY,
    PatientID INT,
    DoctorID INT,
    ScheduledDate DATE NOT NULL,
    TimeSlot TIME NOT NULL,
    Status NVARCHAR(20) NOT NULL DEFAULT N'Pending',
    FOREIGN KEY (PatientID) REFERENCES Patient_Profile(PatientID),
    FOREIGN KEY (DoctorID) REFERENCES Doctor_Profile(DoctorID),
    CONSTRAINT UNIQUE_Doctor_Slot UNIQUE (DoctorID, ScheduledDate, TimeSlot)
);
GO

-- 5. Tạo bảng Hồ sơ Tư vấn & Khám bệnh
CREATE TABLE Consultation_Record (
    RecordID INT IDENTITY(1,1) PRIMARY KEY,
    PatientID INT,
    DoctorID INT,
    VisitDate DATETIME NOT NULL,
    ConsultationNotes NVARCHAR(MAX),
    TreatmentPlan NVARCHAR(MAX),
    Status NVARCHAR(20) DEFAULT N'Completed',
    FOREIGN KEY (PatientID) REFERENCES Patient_Profile(PatientID),
    FOREIGN KEY (DoctorID) REFERENCES Doctor_Profile(DoctorID)
);
GO

-- 6. Tạo bảng Chỉ số Lâm sàng Tim mạch (Quan hệ 1-1 với Hồ sơ khám)
CREATE TABLE Heart_Clinical_Metrics (
    MetricID INT IDENTITY(1,1) PRIMARY KEY,
    RecordID INT UNIQUE, 
    RecordedBy_StaffID INT,
    ChestPainType INT,
    RestingBP INT,
    Cholesterol INT,
    FastingBloodSugar BIT, -- Trong MS SQL dùng BIT thay cho BOOLEAN
    RestingECG INT,
    MaxHeartRate INT,
    ExerciseAngina BIT,
    RecordedAt DATETIME NOT NULL,
    Temperature DECIMAL(4,1) NULL,
    SpO2 INT NULL,
    BloodTest NVARCHAR(MAX) NULL,
    UrineTest NVARCHAR(MAX) NULL,
    Xray NVARCHAR(MAX) NULL,
    MRI NVARCHAR(MAX) NULL,
    CT NVARCHAR(MAX) NULL,
    Ultrasound NVARCHAR(MAX) NULL,
    FOREIGN KEY (RecordID) REFERENCES Consultation_Record(RecordID),
    FOREIGN KEY (RecordedBy_StaffID) REFERENCES Staff_Profile(StaffID)
);
GO

-- 7. Tạo bảng Kết quả Dự đoán Rủi ro AI (Quan hệ 1-1 với Hồ sơ khám)
CREATE TABLE AI_Risk_Prediction (
    PredictionID INT IDENTITY(1,1) PRIMARY KEY,
    RecordID INT UNIQUE,
    RiskScore DECIMAL(5,2),
    RiskLevel NVARCHAR(20),
    RiskExplanation NVARCHAR(MAX),
    HealthAdvice NVARCHAR(MAX),
    DietaryAdvice NVARCHAR(MAX),
    IsAlertSent BIT DEFAULT 0,
    FOREIGN KEY (RecordID) REFERENCES Consultation_Record(RecordID)
);
GO

-- 8. Tạo bảng Bệnh nhân Tự theo dõi
CREATE TABLE Patient_Self_Monitoring (
    LogID INT IDENTITY(1,1) PRIMARY KEY,
    PatientID INT,
    LogDate DATETIME NOT NULL,
    CurrentHeartRate INT,
    Symptoms NVARCHAR(MAX),
    TriggeredAlert BIT DEFAULT 0,
    FOREIGN KEY (PatientID) REFERENCES Patient_Profile(PatientID)
);
GO

-- 9. Tạo bảng Danh mục Mã bệnh ICD
CREATE TABLE ICD_Catalog (
    ICDCode VARCHAR(20) PRIMARY KEY,
    DiseaseName NVARCHAR(255) NOT NULL
);
GO

-- 10. Tạo bảng trung gian Chi tiết bệnh lý tư vấn
CREATE TABLE Record_ICD (
    RecordID INT,
    ICDCode VARCHAR(20),
    Notes NVARCHAR(MAX),
    PRIMARY KEY (RecordID, ICDCode),
    FOREIGN KEY (RecordID) REFERENCES Consultation_Record(RecordID),
    FOREIGN KEY (ICDCode) REFERENCES ICD_Catalog(ICDCode)
);
GO

-- 11. Tạo bảng System Log (Nhật ký hệ thống)
CREATE TABLE System_Log (
    LogID INT IDENTITY(1,1) PRIMARY KEY,
    Username VARCHAR(255) NOT NULL,
    Action VARCHAR(255) NOT NULL,
    Details NVARCHAR(MAX),
    Timestamp DATETIME NOT NULL DEFAULT GETDATE()
);
GO

-- 12. Tạo View app_users hỗ trợ đăng nhập tích hợp cho Spring Security
IF OBJECT_ID('dbo.app_users', 'V') IS NOT NULL DROP VIEW dbo.app_users;
GO
CREATE VIEW app_users AS
SELECT Username, PasswordHash, FullName, Role, Status FROM Staff_Profile
UNION ALL
SELECT Username, PasswordHash, FullName, 'DOCTOR' AS Role, Status FROM Doctor_Profile
UNION ALL
SELECT Username, PasswordHash, FullName, 'PATIENT' AS Role, Status FROM Patient_Profile;
GO
