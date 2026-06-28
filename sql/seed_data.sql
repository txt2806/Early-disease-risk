-- Dữ liệu mẫu (Seed Data) cho hệ thống Cardiology Management (dành cho SQL Server)
-- Dựa trên DatabaseInitializer.java

-- 1. Xóa dữ liệu cũ trong các bảng liên quan (để tránh xung đột hoặc trùng khóa chính/Username)
-- Thứ tự xóa tuân theo các ràng buộc khóa ngoại (Foreign Key)
DELETE FROM Record_ICD;
DELETE FROM ICD_Catalog;
DELETE FROM Heart_Clinical_Metrics;
DELETE FROM AI_Risk_Prediction;
DELETE FROM Patient_Self_Monitoring;
DELETE FROM Appointment;
DELETE FROM Consultation_Record;
DELETE FROM System_Log;
DELETE FROM Patient_Profile;
DELETE FROM Doctor_Profile;
DELETE FROM Staff_Profile;

-- Reset identity seed cho các trường tăng tự động trong SQL Server
DBCC CHECKIDENT ('Staff_Profile', RESEED, 0);
DBCC CHECKIDENT ('Doctor_Profile', RESEED, 0);
DBCC CHECKIDENT ('Patient_Profile', RESEED, 0);
DBCC CHECKIDENT ('Appointment', RESEED, 0);
DBCC CHECKIDENT ('Consultation_Record', RESEED, 0);
DBCC CHECKIDENT ('Heart_Clinical_Metrics', RESEED, 0);
DBCC CHECKIDENT ('AI_Risk_Prediction', RESEED, 0);
DBCC CHECKIDENT ('Patient_Self_Monitoring', RESEED, 0);
DBCC CHECKIDENT ('System_Log', RESEED, 0);

-- 2. Nạp dữ liệu vào bảng Staff_Profile (Admin, Staff, Receptionist)
-- Mật khẩu mặc định của tất cả tài khoản mẫu là '123' (mã hóa bằng BCrypt)
INSERT INTO Staff_Profile (Username, PasswordHash, FullName, Role, Status) VALUES
('admin@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'System Administrator', 'ADMIN', 'ACTIVE'),
('medicalstaff01@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Điều dưỡng Lê Thị Bình', 'STAFF', 'ACTIVE'),
('medicalstaff02@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Điều dưỡng Hoàng Văn Giang', 'STAFF', 'ACTIVE'),
('medicalstaff03@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Điều dưỡng Vũ Thị Hương', 'STAFF', 'ACTIVE'),
('medicalstaff04@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Điều dưỡng Phạm Thị Nhung', 'STAFF', 'ACTIVE'),
('recep1@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Lễ tân Trần Văn Cường', 'RECEPTIONIST', 'ACTIVE'),
('recep2@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Lễ tân Ngô Thị Khánh', 'RECEPTIONIST', 'ACTIVE'),
('recep3@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Lễ tân Bùi Văn Long', 'RECEPTIONIST', 'ACTIVE');

-- 3. Nạp dữ liệu vào bảng Doctor_Profile (Danh sách bác sĩ)
INSERT INTO Doctor_Profile (Username, PasswordHash, FullName, Specialty, AlertThreshold_BPM, AlertThreshold_BP, LicenseNumber, RoomNumber, Status) VALUES
('doctor01@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'BS. Nguyễn Văn An', N'Tim mạch', 100, '140/90', 'CCHN-11111', 'Phòng 101', 'ACTIVE'),
('doctor02@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'BS. Trần Văn Bình', N'Tim mạch', 100, '140/90', 'CCHN-22222', 'Phòng 102', 'ACTIVE'),
('doctor03@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'BS. Lê Thị Chi', N'Tim mạch', 100, '140/90', 'CCHN-33333', 'Phòng 103', 'ACTIVE'),
('doctor04@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'BS. Phạm Minh Đức', N'Tim mạch', 100, '140/90', 'CCHN-44444', 'Phòng 104', 'ACTIVE');

-- 4. Nạp dữ liệu vào bảng Patient_Profile (Hồ sơ bệnh nhân - có thêm cột is_alert mặc định = 0)
INSERT INTO Patient_Profile (Username, PasswordHash, FullName, DOB, Gender, Phone, Address, Status, is_alert) VALUES
('patient1@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Bệnh nhân Nguyễn Văn Test', '2000-01-01', N'Nam', '+84999999991', N'Hà Nội', 'ACTIVE', 0),
('patient2@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Bệnh nhân Trần Thị Mai', '1995-05-15', N'Nữ', '+84999999992', N'Đà Nẵng', 'ACTIVE', 0),
('patient3@cardio.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', N'Bệnh nhân Lê Hoàng Nam', '1988-08-20', N'Nam', '+84999999993', N'TP. Hồ Chí Minh', 'ACTIVE', 0);

-- 5. Nạp dữ liệu vào bảng System_Log (Nhật ký hệ thống)
-- Sử dụng DATEADD và GETDATE() tương đối so với thời gian hiện tại để hiển thị đẹp trên biểu đồ dashboard
INSERT INTO System_Log (Username, Action, Details, Timestamp) VALUES
-- Hôm nay (Days ago = 0)
('admin@cardio.com', 'LOGIN_SUCCESS', N'Đăng nhập hệ thống quản trị thành công', GETDATE()),
('doctor01@cardio.com', 'LOGIN_SUCCESS', N'Bác sĩ Nguyễn Văn An đăng nhập thành công', GETDATE()),
('doctor01@cardio.com', 'CREATE_CONSULTATION', N'Tạo hồ sơ khám cho bệnh nhân patient1@cardio.com', GETDATE()),
('medicalstaff01@cardio.com', 'UPDATE_METRICS', N'Cập nhật chỉ số lâm sàng cho bệnh nhân patient1@cardio.com', GETDATE()),
('system', 'AI_PREDICTION_SUCCESS', N'AI hoàn thành dự đoán rủi ro tim mạch cho bệnh nhân patient1@cardio.com', GETDATE()),

-- 1 ngày trước
('admin@cardio.com', 'CREATE_USER_SUCCESS', N'Tạo tài khoản bác sĩ: doctor04@cardio.com', DATEADD(minute, -5, DATEADD(hour, -2, DATEADD(day, -1, GETDATE())))),
('recep1@cardio.com', 'CREATE_APPOINTMENT', N'Đặt lịch hẹn cho bệnh nhân patient2@cardio.com với BS Nguyễn Văn An', DATEADD(minute, -5, DATEADD(hour, -2, DATEADD(day, -1, GETDATE())))),
('doctor02@cardio.com', 'LOGIN_SUCCESS', N'Bác sĩ Trần Văn Bình đăng nhập thành công', DATEADD(minute, -5, DATEADD(hour, -2, DATEADD(day, -1, GETDATE())))),
('doctor02@cardio.com', 'VIEW_PATIENT_HISTORY', N'Xem lịch sử bệnh án bệnh nhân patient2@cardio.com', DATEADD(minute, -5, DATEADD(hour, -2, DATEADD(day, -1, GETDATE())))),

-- 2 ngày trước
('admin@cardio.com', 'LOCK_USER', N'Tạm khóa tài khoản bác sĩ nghỉ phép: doctor03@cardio.com', DATEADD(minute, -10, DATEADD(hour, -4, DATEADD(day, -2, GETDATE())))),
('medicalstaff02@cardio.com', 'LOGIN_SUCCESS', N'Điều dưỡng Hoàng Văn Giang đăng nhập thành công', DATEADD(minute, -10, DATEADD(hour, -4, DATEADD(day, -2, GETDATE())))),
('medicalstaff02@cardio.com', 'UPDATE_METRICS', N'Cập nhật chỉ số lâm sàng cho bệnh nhân patient3@cardio.com', DATEADD(minute, -10, DATEADD(hour, -4, DATEADD(day, -2, GETDATE())))),
('system', 'AI_PREDICTION_SUCCESS', N'AI hoàn thành dự đoán rủi ro cho bệnh nhân patient3@cardio.com', DATEADD(minute, -10, DATEADD(hour, -4, DATEADD(day, -2, GETDATE())))),

-- 3 ngày trước
('recep2@cardio.com', 'LOGIN_SUCCESS', N'Lễ tân Ngô Thị Khánh đăng nhập hệ thống thành công', DATEADD(minute, -15, DATEADD(hour, -6, DATEADD(day, -3, GETDATE())))),
('recep2@cardio.com', 'CREATE_APPOINTMENT', N'Đặt lịch hẹn tái khám cho bệnh nhân patient1@cardio.com', DATEADD(minute, -15, DATEADD(hour, -6, DATEADD(day, -3, GETDATE())))),
('doctor01@cardio.com', 'LOGIN_SUCCESS', N'Bác sĩ Nguyễn Văn An đăng nhập thành công', DATEADD(minute, -15, DATEADD(hour, -6, DATEADD(day, -3, GETDATE())))),
('doctor01@cardio.com', 'UPDATE_TREATMENT_PLAN', N'Cập nhật phác đồ điều trị cho bệnh nhân patient1@cardio.com', DATEADD(minute, -15, DATEADD(hour, -6, DATEADD(day, -3, GETDATE())))),

-- 4 ngày trước
('admin@cardio.com', 'LOGIN_SUCCESS', N'Đăng nhập hệ thống quản trị', DATEADD(minute, -20, DATEADD(hour, -8, DATEADD(day, -4, GETDATE())))),
('admin@cardio.com', 'RESET_PASSWORD_PATIENT', N'Đặt lại mật khẩu cho bệnh nhân patient2@cardio.com theo yêu cầu', DATEADD(minute, -20, DATEADD(hour, -8, DATEADD(day, -4, GETDATE())))),
('doctor03@cardio.com', 'LOGIN_SUCCESS', N'Bác sĩ Lê Thị Chi đăng nhập thành công', DATEADD(minute, -20, DATEADD(hour, -8, DATEADD(day, -4, GETDATE())))),

-- 5 ngày trước
('recep3@cardio.com', 'LOGIN_SUCCESS', N'Lễ tân Bùi Văn Long đăng nhập thành công', DATEADD(minute, -25, DATEADD(hour, -10, DATEADD(day, -5, GETDATE())))),
('recep3@cardio.com', 'CREATE_APPOINTMENT', N'Đặt lịch hẹn mới cho bệnh nhân patient3@cardio.com', DATEADD(minute, -25, DATEADD(hour, -10, DATEADD(day, -5, GETDATE())))),
('doctor02@cardio.com', 'LOGIN_SUCCESS', N'Bác sĩ Trần Văn Bình đăng nhập thành công', DATEADD(minute, -25, DATEADD(hour, -10, DATEADD(day, -5, GETDATE())))),

-- 6 ngày trước
('admin@cardio.com', 'UNLOCK_USER', N'Mở khóa hoạt động tài khoản bác sĩ: doctor03@cardio.com', DATEADD(minute, -30, DATEADD(hour, -12, DATEADD(day, -6, GETDATE())))),
('doctor01@cardio.com', 'LOGIN_SUCCESS', N'Bác sĩ Nguyễn Văn An đăng nhập thành công', DATEADD(minute, -30, DATEADD(hour, -12, DATEADD(day, -6, GETDATE())))),
('doctor01@cardio.com', 'CREATE_CONSULTATION', N'Tạo hồ sơ khám mới cho bệnh nhân patient3@cardio.com', DATEADD(minute, -30, DATEADD(hour, -12, DATEADD(day, -6, GETDATE()))));

-- 6. Nạp dữ liệu vào bảng Appointment (Lịch hẹn)
-- Trạng thái: Pending, N'Đã khám', N'Hủy', N'Dời lịch'
INSERT INTO Appointment (PatientID, DoctorID, ScheduledDate, TimeSlot, Status) VALUES
(1, 1, DATEADD(day, 1, CAST(GETDATE() AS DATE)), '09:00:00', N'Pending'),
(2, 2, DATEADD(day, -1, CAST(GETDATE() AS DATE)), '10:30:00', N'Đã khám'),
(3, 1, DATEADD(day, 2, CAST(GETDATE() AS DATE)), '14:00:00', N'Dời lịch'),
(1, 3, DATEADD(day, 3, CAST(GETDATE() AS DATE)), '15:30:00', N'Hủy');

-- 7. Nạp dữ liệu vào bảng Consultation_Record & Heart_Clinical_Metrics (Hồ sơ khám & Chỉ số y tế của Medical Staff)
INSERT INTO Consultation_Record (PatientID, DoctorID, VisitDate, ConsultationNotes, TreatmentPlan, Status) VALUES
(1, 1, DATEADD(day, -2, GETDATE()), N'Bệnh nhân đau ngực nhẹ, nhịp tim bình thường.', N'Nghỉ ngơi, hạn chế chất béo.', N'Completed');

INSERT INTO Heart_Clinical_Metrics (RecordID, RecordedBy_StaffID, ChestPainType, RestingBP, Cholesterol, FastingBloodSugar, RestingECG, MaxHeartRate, ExerciseAngina, RecordedAt, Temperature, SpO2, BloodTest, UrineTest, Xray, MRI, CT, Ultrasound) VALUES
(1, 2, 2, 130, 250, 0, 0, 150, 0, DATEADD(day, -2, GETDATE()), 36.5, 98, N'Đường huyết: 5.6 mmol/L, Cholesterol TP: 5.1 mmol/L', N'Bình thường', N'Bóng tim không to, phổi sáng.', NULL, NULL, N'Siêu âm tim bình thường.');

