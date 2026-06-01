CREATE DATABASE Cardiology_Management_DB;
GO

USE Cardiology_Management_DB;
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

-- 3. Tạo bảng Hồ sơ Bệnh nhân
CREATE TABLE Patient_Profile (
    PatientID INT IDENTITY(1,1) PRIMARY KEY,
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    DOB DATE NOT NULL,
    Gender NVARCHAR(10) NOT NULL,
    Phone VARCHAR(15),
    Address NVARCHAR(255),
    Status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

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
    FOREIGN KEY (RecordID) REFERENCES Consultation_Record(RecordID),
    FOREIGN KEY (RecordedBy_StaffID) REFERENCES Staff_Profile(StaffID)
);

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

-- 9. Tạo bảng Danh mục Mã bệnh ICD
CREATE TABLE ICD_Catalog (
    ICDCode VARCHAR(20) PRIMARY KEY,
    DiseaseName NVARCHAR(255) NOT NULL
);

-- 10. Tạo bảng trung gian Chi tiết bệnh lý tư vấn
CREATE TABLE Record_ICD (
    RecordID INT,
    ICDCode VARCHAR(20),
    Notes NVARCHAR(MAX),
    PRIMARY KEY (RecordID, ICDCode),
    FOREIGN KEY (RecordID) REFERENCES Consultation_Record(RecordID),
    FOREIGN KEY (ICDCode) REFERENCES ICD_Catalog(ICDCode)
);
