# Cardio Care — Giao diện Trang chủ (Frontend)

Mã nguồn frontend trang chủ phòng khám tim mạch được viết bằng **React.js (Vite)** và **Tailwind CSS**.

## Cấu trúc thư mục chính
- `src/components/Navbar.jsx`: Thanh điều hướng mờ kính (glassmorphism), responsive menu.
- `src/components/Hero.jsx`: Biển hiệu đầu trang kèm sơ đồ biểu thị nhịp tim động (SVG animation).
- `src/components/Stats.jsx`: Bảng số liệu uy tín (kinh nghiệm, ca bệnh thành công).
- `src/components/Services.jsx`: Danh sách dịch vụ cận lâm sàng dạng card tương phản cao.
- `src/components/Doctors.jsx`: Danh sách thông tin học vị & chuyên môn bác sĩ.
- `src/components/BookingForm.jsx`: Biểu mẫu đăng ký khám nhanh (đầy đủ validation, comment nối API).
- `src/components/Footer.jsx`: Chân trang chứa hotline khẩn cấp 24/7 và địa chỉ.

## Hướng dẫn cài đặt và chạy thử

### 1. Di chuyển vào thư mục frontend
Mở terminal tại thư mục gốc của dự án và chạy:
```bash
cd frontend
```

### 2. Cài đặt các thư viện phụ thuộc
Chạy lệnh sau để cài đặt React, Vite, Tailwind CSS và Lucide React:
```bash
npm install
```

### 3. Chạy giao diện ở chế độ nhà phát triển
Khởi động máy chủ phát triển cục bộ:
```bash
npm run dev
```
Mặc định máy chủ sẽ chạy tại địa chỉ: `http://localhost:3000` (được cấu hình trong `vite.config.js`).

### 4. Biên dịch đóng gói sản xuất (Build)
Biên dịch đóng gói tối ưu hóa dung lượng:
```bash
npm run build
```
Thư mục kết quả biên dịch `/dist` có thể tích hợp trực tiếp làm tài nguyên tĩnh (`static/`) cho server Spring Boot hoặc deploy độc lập.
