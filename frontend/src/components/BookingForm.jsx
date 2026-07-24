import React, { useState } from 'react';
import { Calendar, Phone, User, Clock, Heart, ClipboardCheck } from 'lucide-react';

export default function BookingForm() {
  const [formData, setFormData] = useState({
    fullName: '',
    phone: '',
    specialty: '',
    doctor: '',
    date: '',
    timeSlot: ''
  });

  const [status, setStatus] = useState({
    type: '', // 'success' or 'error'
    message: ''
  });

  const [loading, setLoading] = useState(false);

  const specialties = [
    'Khám tim mạch tổng quát',
    'Đo điện tâm đồ (ECG)',
    'Siêu âm tim Doppler',
    'Điều trị cao huyết áp'
  ];

  const doctors = [
    'PGS.TS.BS. Nguyễn Văn An',
    'ThS.BS. Trần Thị Bình',
    'TS.BS. Lê Hoàng Đức'
  ];

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setStatus({ type: '', message: '' });

    // Client-side validation
    if (!formData.fullName.trim()) {
      setStatus({ type: 'error', message: 'Vui lòng nhập họ và tên.' });
      setLoading(false);
      return;
    }
    if (!formData.phone.match(/^[0-9]{10,11}$/)) {
      setStatus({ type: 'error', message: 'Số điện thoại không hợp lệ (yêu cầu 10-11 số).' });
      setLoading(false);
      return;
    }

    try {
      /* 
         ======================================================
         KẾT NỐI API BACKEND (API INTEGRATION PLACEHOLDER)
         ======================================================
         Dưới đây là ví dụ gọi API đến backend Spring Boot.
         Bạn hãy thay URL khớp với endpoint thực tế của hệ thống.
         
         const response = await fetch('/api/appointments/save', {
           method: 'POST',
           headers: {
             'Content-Type': 'application/json',
             // Nếu có CSRF token trong dự án Spring Security:
             // 'X-CSRF-TOKEN': csrfToken 
           },
           body: JSON.stringify({
             patientName: formData.fullName,
             patientPhone: formData.phone,
             specialty: formData.specialty,
             preferredDoctor: formData.doctor,
             scheduledDate: formData.date,
             timeSlot: formData.timeSlot
           })
         });
         
         const data = await response.json();
         if (!response.ok) throw new Error(data.message || 'Lỗi đặt lịch.');
      */

      // Giả lập kết nối mạng trong 1.5s
      await new Promise(resolve => setTimeout(resolve, 1500));

      setStatus({
        type: 'success',
        message: '🎉 Đăng ký đặt lịch khám thành công! Nhân viên y tế sẽ gọi điện xác nhận trong vòng 15 phút.'
      });

      // Clear form
      setFormData({
        fullName: '',
        phone: '',
        specialty: '',
        doctor: '',
        date: '',
        timeSlot: ''
      });

    } catch (err) {
      console.error(err);
      setStatus({
        type: 'error',
        message: err.message || 'Có lỗi xảy ra khi kết nối. Vui lòng thử lại hoặc gọi hotline.'
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <section id="booking" className="py-20 bg-white relative overflow-hidden">
      
      {/* Background decoration */}
      <div className="absolute top-1/2 left-0 -translate-y-1/2 w-[300px] h-[300px] bg-rose-50 rounded-full blur-[100px] pointer-events-none" />

      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Card Container */}
        <div className="bg-slate-900 text-white rounded-3xl overflow-hidden border border-white/10 shadow-2xl p-8 sm:p-12">
          
          <div className="text-center max-w-2xl mx-auto mb-10">
            <div className="inline-flex items-center justify-center p-3 bg-white/10 rounded-2xl mb-4">
              <ClipboardCheck className="h-6 w-6 text-medical-coral" />
            </div>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight mb-2">
              Đặt lịch hẹn khám nhanh
            </h2>
            <p className="text-slate-400 text-xs sm:text-sm font-semibold">
              Nhập thông tin đăng ký khám bệnh. Chúng tôi bảo mật thông tin y khoa của bạn tuyệt đối.
            </p>
          </div>

          {/* Form Alert Status */}
          {status.message && (
            <div className={`mb-6 p-4 rounded-xl text-sm font-bold border ${
              status.type === 'success' 
                ? 'bg-green-500/10 border-green-500/20 text-green-400' 
                : 'bg-rose-500/10 border-rose-500/20 text-rose-400'
            }`}>
              {status.message}
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-6 text-left">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              
              {/* Họ tên */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Họ và tên bệnh nhân *</label>
                <div className="relative">
                  <User className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-500" />
                  <input 
                    type="text" 
                    name="fullName"
                    required
                    value={formData.fullName}
                    onChange={handleInputChange}
                    placeholder="Nguyễn Văn A" 
                    className="w-full bg-white/5 border border-white/10 rounded-2xl py-3.5 pl-12 pr-4 text-white placeholder-slate-500 focus:outline-none focus:border-medical-coral focus:ring-1 focus:ring-medical-coral transition-all text-sm font-semibold"
                  />
                </div>
              </div>

              {/* SĐT */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Số điện thoại liên hệ *</label>
                <div className="relative">
                  <Phone className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-500" />
                  <input 
                    type="tel" 
                    name="phone"
                    required
                    value={formData.phone}
                    onChange={handleInputChange}
                    placeholder="Ví dụ: 0912345678" 
                    className="w-full bg-white/5 border border-white/10 rounded-2xl py-3.5 pl-12 pr-4 text-white placeholder-slate-500 focus:outline-none focus:border-medical-coral focus:ring-1 focus:ring-medical-coral transition-all text-sm font-semibold"
                  />
                </div>
              </div>

              {/* Chuyên khoa */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Chuyên khoa cần khám *</label>
                <div className="relative">
                  <Heart className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-500" />
                  <select 
                    name="specialty"
                    required
                    value={formData.specialty}
                    onChange={handleInputChange}
                    className="w-full bg-slate-900 border border-white/10 rounded-2xl py-3.5 pl-12 pr-4 text-white focus:outline-none focus:border-medical-coral focus:ring-1 focus:ring-medical-coral transition-all text-sm font-semibold appearance-none cursor-pointer"
                  >
                    <option value="">-- Chọn chuyên khoa --</option>
                    {specialties.map((item, idx) => (
                      <option key={idx} value={item} className="bg-slate-900 text-white">{item}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Bác sĩ chuyên khoa */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Bác sĩ mong muốn (nếu có)</label>
                <div className="relative">
                  <Heart className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-500" />
                  <select 
                    name="doctor"
                    value={formData.doctor}
                    onChange={handleInputChange}
                    className="w-full bg-slate-900 border border-white/10 rounded-2xl py-3.5 pl-12 pr-4 text-white focus:outline-none focus:border-medical-coral focus:ring-1 focus:ring-medical-coral transition-all text-sm font-semibold appearance-none cursor-pointer"
                  >
                    <option value="">-- Chọn bác sĩ --</option>
                    {doctors.map((item, idx) => (
                      <option key={idx} value={item} className="bg-slate-900 text-white">{item}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Ngày khám */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Ngày hẹn khám *</label>
                <div className="relative">
                  <Calendar className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-500" />
                  <input 
                    type="date" 
                    name="date"
                    required
                    value={formData.date}
                    onChange={handleInputChange}
                    className="w-full bg-white/5 border border-white/10 rounded-2xl py-3.5 pl-12 pr-4 text-white focus:outline-none focus:border-medical-coral focus:ring-1 focus:ring-medical-coral transition-all text-sm font-semibold cursor-pointer"
                  />
                </div>
              </div>

              {/* Giờ khám */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">Giờ hẹn khám *</label>
                <div className="relative">
                  <Clock className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-500" />
                  <select 
                    name="timeSlot"
                    required
                    value={formData.timeSlot}
                    onChange={handleInputChange}
                    className="w-full bg-slate-900 border border-white/10 rounded-2xl py-3.5 pl-12 pr-4 text-white focus:outline-none focus:border-medical-coral focus:ring-1 focus:ring-medical-coral transition-all text-sm font-semibold appearance-none cursor-pointer"
                  >
                    <option value="">-- Chọn khung giờ --</option>
                    <option value="08:00 - 09:00">08:00 - 09:00</option>
                    <option value="09:00 - 10:00">09:00 - 10:00</option>
                    <option value="10:00 - 11:00">10:00 - 11:00</option>
                    <option value="14:00 - 15:00">14:00 - 15:00</option>
                    <option value="15:00 - 16:00">15:00 - 16:00</option>
                  </select>
                </div>
              </div>

            </div>

            <div className="pt-4 flex justify-center">
              <button 
                type="submit" 
                disabled={loading}
                className="w-full sm:w-auto bg-medical-coral hover:bg-medical-coralhover text-white px-10 py-4 rounded-full text-base font-bold shadow-lg shadow-medical-coral/20 hover:-translate-y-0.5 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? 'Đang gửi thông tin...' : 'Xác nhận Đặt lịch hẹn'}
              </button>
            </div>
          </form>

        </div>
      </div>
    </section>
  );
}
