-- 1. Xóa dữ liệu cũ liên quan đến bệnh án và danh mục ICD để tránh trùng lặp
DELETE FROM Record_ICD;
DELETE FROM Heart_Clinical_Metrics;
DELETE FROM AI_Risk_Prediction;
DELETE FROM Consultation_Record;
DELETE FROM ICD_Catalog;

-- Reset identity seed cho các bảng bệnh án trong SQL Server
DBCC CHECKIDENT ('Consultation_Record', RESEED, 0);
DBCC CHECKIDENT ('Heart_Clinical_Metrics', RESEED, 0);
DBCC CHECKIDENT ('AI_Risk_Prediction', RESEED, 0);

-- 2. Nạp dữ liệu danh mục mã bệnh ICD-10 liên quan đến tim mạch (để sẵn trong hệ thống)
INSERT INTO ICD_Catalog (ICDCode, DiseaseName) VALUES
('I10', N'Bệnh cao huyết áp vô căn (hypertension)'),
('I20', N'Cơn đau thắt ngực (angina pectoris)'),
('I21', N'Nhồi máu cơ tim cấp (acute myocardial infarction)'),
('I25', N'Bệnh cơ tim thiếu máu cục bộ mạn tính (ischemic heart disease)'),
('I48', N'Rung nhĩ và cuồng nhĩ (atrial fibrillation)'),
('I50', N'Suy tim (heart failure)');

-- 3. Khai báo các biến tạm để tìm ID động từ Username và sử dụng vòng lặp (Cursor) để nạp động 1 đến 4 ca khám cho từng bệnh nhân
DECLARE @pId INT, @dId INT, @recId INT, @staffId INT;
DECLARE @dob DATE, @gender NVARCHAR(50), @age INT, @sex VARCHAR(10);
SELECT TOP 1 @staffId = StaffID FROM Staff_Profile;

-- Khai báo bảng tạm chứa thông số lâm sàng mẫu
DECLARE @MetricsTable TABLE (
    Idx INT,
    DocId INT,
    BaseDaysAgo INT,
    ChestPainType INT,
    RestingBP INT,
    Cholesterol INT,
    FastingBloodSugar INT,
    RestingECG INT,
    MaxHeartRate INT,
    ExerciseAngina INT,
    Oldpeak DECIMAL(3,1),
    Slope VARCHAR(20),
    Ca INT,
    Thal VARCHAR(50),
    BloodTest NVARCHAR(255),
    UrineTest NVARCHAR(255),
    Xray NVARCHAR(255),
    Ultrasound NVARCHAR(255)
);

INSERT INTO @MetricsTable VALUES
(0, 1, 5, 2, 145, 250, 1, 1, 130, 1, 2.0, 'flat', 1, 'reversable defect', N'Glucose: 7.2 mmol/L, Cholesterol TP: 6.5 mmol/L', N'Bình thường', N'Phổi sáng, bóng tim hơi to', N'Siêu âm tim: Hở van 2 lá nhẹ'),
(1, 2, 4, 0, 115, 180, 0, 0, 168, 0, 0.0, 'upsloping', 0, 'normal', N'Glucose: 5.1 mmol/L, Cholesterol TP: 4.5 mmol/L', N'Bình thường', N'Bình thường', N'Siêu âm tim bình thường'),
(2, 1, 6, 1, 155, 270, 0, 2, 125, 1, 2.5, 'flat', 2, 'reversable defect', N'Troponin T: 14 ng/L, Cholesterol TP: 6.8 mmol/L', N'Bình thường', N'Bóng tim to, ứ huyết nhẹ ở phổi', N'Giảm vận động vùng vách liên thất'),
(3, 3, 3, 0, 110, 190, 0, 0, 175, 0, 0.0, 'upsloping', 0, 'normal', N'Chỉ số sinh hóa bình thường', N'Bình thường', N'Bình thường', N'Bình thường'),
(4, 4, 2, 3, 135, 225, 0, 1, 145, 0, 1.0, 'flat', 0, 'fixed defect', N'Cholesterol TP: 5.8 mmol/L, LDL-C: 3.4 mmol/L', N'Bình thường', N'Bình thường', N'Bình thường'),
(5, 2, 7, 2, 130, 240, 1, 0, 140, 0, 1.2, 'flat', 1, 'fixed defect', N'Đường huyết đói: 6.8 mmol/L, mỡ máu cao', N'Bình thường', N'Bình thường', N'Hở van 3 lá nhẹ'),
(6, 3, 3, 1, 150, 260, 1, 1, 135, 1, 1.8, 'flat', 2, 'reversable defect', N'Cholesterol TP: 7.1 mmol/L, Triglyceride: 3.2 mmol/L', N'Bình thường', N'Phổi bình thường', N'Phì đại cơ tim nhẹ'),
(7, 4, 8, 0, 112, 185, 0, 0, 170, 0, 0.0, 'upsloping', 0, 'normal', N'Máu và nước tiểu hoàn toàn bình thường', N'Bình thường', N'Bình thường', N'Bình thường'),
(8, 1, 10, 2, 140, 280, 0, 2, 120, 1, 2.2, 'downsloping', 2, 'reversable defect', N'NT-proBNP: 1250 pg/mL, Điện giải đồ ổn định', N'Bình thường', N'Ứ dịch phổi hai bên, tim to toàn bộ', N'EF giảm còn 40%'),
(9, 2, 9, 3, 118, 195, 0, 0, 165, 0, 0.5, 'upsloping', 0, 'normal', N'T3, T4, TSH trong giới hạn bình thường', N'Bình thường', N'Bình thường', N'Bình thường'),
(10, 3, 1, 2, 128, 210, 0, 0, 152, 0, 0.8, 'flat', 0, 'normal', N'Cholesterol TP: 5.4 mmol/L, LDL-C: 3.1 mmol/L', N'Bình thường', N'Bình thường', N'Mảng xơ vữa nhẹ ở gốc động mạch chủ'),
(11, 4, 5, 3, 132, 230, 0, 1, 148, 1, 1.4, 'flat', 1, 'fixed defect', N'Xét nghiệm máu cơ bản ổn định', N'Bình thường', N'Bình thường', N'Bình thường'),
(12, 1, 3, 0, 114, 175, 0, 0, 180, 0, 0.0, 'upsloping', 0, 'normal', N'Bình thường', N'Bình thường', N'Bình thường', N'Bình thường'),
(13, 2, 6, 0, 116, 188, 0, 0, 172, 0, 0.0, 'upsloping', 0, 'normal', N'Bình thường', N'Bình thường', N'Bình thường', N'Bình thường'),
(14, 3, 2, 3, 120, 192, 0, 0, 160, 0, 0.2, 'upsloping', 0, 'normal', N'Men tim bình thường', N'Bình thường', N'Bình thường', N'Bình thường'),
(15, 4, 4, 1, 148, 290, 1, 2, 128, 1, 2.4, 'flat', 2, 'reversable defect', N'HbA1c: 8.5%, Cholesterol: 7.5 mmol/L', N'Bình thường', N'Phổi ứ huyết nhẹ', N'Giảm động vách liên thất diện rộng'),
(16, 1, 10, 2, 138, 245, 0, 1, 142, 1, 1.5, 'flat', 1, 'fixed defect', N'Mỡ máu tăng cao, men gan bình thường', N'Bình thường', N'X-quang phổi bình thường', N'Bình thường'),
(17, 2, 9, 0, 110, 182, 0, 0, 176, 0, 0.0, 'upsloping', 0, 'normal', N'Các chỉ số bình thường', N'Bình thường', N'Bình thường', N'Bình thường'),
(18, 3, 6, 3, 134, 218, 0, 1, 150, 0, 0.6, 'flat', 0, 'normal', N'Xét nghiệm máu cơ bản ổn định', N'Bình thường', N'Bình thường', N'Bình thường'),
(19, 4, 8, 1, 142, 275, 0, 2, 132, 1, 1.9, 'flat', 1, 'reversable defect', N'INR: 2.1, Cholesterol TP: 6.9 mmol/L', N'Bình thường', N'Bóng tim to nhẹ', N'Giãn nhĩ trái nhẹ');

-- Cursor duyệt qua tất cả bệnh nhân
DECLARE patient_cursor CURSOR FOR
SELECT PatientID, DOB, Gender FROM Patient_Profile ORDER BY PatientID;

OPEN patient_cursor;
FETCH NEXT FROM patient_cursor INTO @pId, @dob, @gender;

DECLARE @patientIdx INT = 0;

WHILE @@FETCH_STATUS = 0
BEGIN
    -- Số lượng ca khám: 1, 2, 3, 4 dựa trên chỉ số bệnh nhân
    DECLARE @numRecords INT = (@patientIdx % 4) + 1;
    
    SET @age = DATEDIFF(YEAR, @dob, GETDATE());
    SET @sex = CASE WHEN @gender = N'Nam' OR @gender = 'Male' THEN 'Male' ELSE 'Female' END;

    DECLARE @j INT = 0;
    WHILE @j < @numRecords
    BEGIN
        -- Lấy chỉ số thông số lâm sàng tương ứng
        DECLARE @metricIdx INT = (@patientIdx * 3 + @j) % 20;

        DECLARE @docIdMock INT, @baseDaysAgo INT, @cp INT, @bp INT, @chol INT, @fbs INT, @ecg INT, @hr INT, @exang INT;
        DECLARE @oldpeak DECIMAL(3,1), @slope VARCHAR(20), @ca INT, @thal VARCHAR(50);
        DECLARE @blood NVARCHAR(255), @urine NVARCHAR(255), @xray NVARCHAR(255), @ultrasound NVARCHAR(255);

        SELECT 
            @docIdMock = DocId, @baseDaysAgo = BaseDaysAgo, @cp = ChestPainType, @bp = RestingBP, @chol = Cholesterol,
            @fbs = FastingBloodSugar, @ecg = RestingECG, @hr = MaxHeartRate, @exang = ExerciseAngina,
            @oldpeak = Oldpeak, @slope = Slope, @ca = Ca, @thal = Thal,
            @blood = BloodTest, @urine = UrineTest, @xray = Xray, @ultrasound = Ultrasound
        FROM @MetricsTable WHERE Idx = @metricIdx;

        DECLARE @daysAgo INT = @baseDaysAgo + @j * 45;
        
        -- Xác định DoctorID thực tế
        DECLARE @docUsername VARCHAR(100) = 'doctor' + CAST(@docIdMock AS VARCHAR(10)) + '@cardio.com';
        SET @dId = (SELECT DoctorID FROM Doctor_Profile WHERE Username = @docUsername);

        IF @dId IS NOT NULL
        BEGIN
            INSERT INTO Consultation_Record (PatientID, DoctorID, VisitDate, ConsultationNotes, TreatmentPlan, Status) VALUES
            (@pId, @dId, DATEADD(day, -@daysAgo, GETDATE()), NULL, NULL, N'Pending');
            SET @recId = SCOPE_IDENTITY();

            INSERT INTO Heart_Clinical_Metrics (RecordID, RecordedBy_StaffID, ChestPainType, RestingBP, Cholesterol, FastingBloodSugar, RestingECG, MaxHeartRate, ExerciseAngina, RecordedAt, Temperature, SpO2, BloodTest, UrineTest, Xray, Ultrasound, Age, Sex, Oldpeak, Slope, Ca, Thal) VALUES
            (@recId, @staffId, @cp, @bp, @chol, @fbs, @ecg, @hr, @exang, DATEADD(day, -@daysAgo, GETDATE()), 36.5, 98, @blood, @urine, @xray, @ultrasound, @age, @sex, @oldpeak, @slope, @ca, @thal);
        END

        SET @j = @j + 1;
    END

    SET @patientIdx = @patientIdx + 1;
    FETCH NEXT FROM patient_cursor INTO @pId, @dob, @gender;
END

CLOSE patient_cursor;
DEALLOCATE patient_cursor;
