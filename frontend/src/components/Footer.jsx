import React from 'react';
import { HeartPulse, Mail, MapPin, PhoneCall, ShieldAlert, Heart } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="bg-medical-dark text-slate-400 pt-16 pb-8 border-t border-white/5 relative overflow-hidden">
      
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-12 gap-10 lg:gap-8 mb-12">
          
          {/* Brand Col */}
          <div className="lg:col-span-4 flex flex-col items-start text-left">
            <div className="flex items-center gap-2 mb-4 cursor-pointer" onClick={() => window.scrollTo({top: 0, behavior: 'smooth'})}>
              <HeartPulse className="h-8 w-8 text-medical-coral" />
              <span className="text-white font-extrabold text-xl tracking-tight">
                CARDIO<span className="text-medical-coral">CARE</span>
              </span>
            </div>
            <p className="text-slate-400 text-sm leading-relaxed mb-6">
              Hệ thống phòng khám chuyên khoa tim mạch hàng đầu, tích hợp trí tuệ nhân tạo (AI) giúp dự đoán và ngăn ngừa nguy cơ đột quỵ và các bệnh lý tim mạch sớm.
            </p>
            <div className="flex items-center gap-3 p-3 bg-red-500/10 border border-red-500/20 rounded-2xl">
              <ShieldAlert className="h-5 w-5 text-red-500 animate-pulse" />
              <span className="text-xs font-bold text-red-400 uppercase tracking-wider">Cấp cứu 24/7: 1900 1234</span>
            </div>
          </div>

          {/* Direct Services list links */}
          <div className="lg:col-span-3 text-left">
            <h4 className="text-white font-bold text-sm uppercase tracking-wider mb-6">Dịch vụ chính</h4>
            <ul className="space-y-3.5 text-sm font-semibold">
              <li><a href="#services" className="hover:text-white transition-colors">Khám tim tổng quát</a></li>
              <li><a href="#services" className="hover:text-white transition-colors">Đo điện tâm đồ (ECG)</a></li>
              <li><a href="#services" className="hover:text-white transition-colors">Siêu âm tim Doppler</a></li>
              <li><a href="#services" className="hover:text-white transition-colors">Dự báo nguy cơ bằng AI</a></li>
            </ul>
          </div>

          {/* Contact Col */}
          <div className="lg:col-span-5 text-left">
            <h4 className="text-white font-bold text-sm uppercase tracking-wider mb-6">Thông tin liên hệ</h4>
            <ul className="space-y-4 text-sm font-semibold">
              <li className="flex items-start gap-3">
                <MapPin className="h-5 w-5 text-medical-coral flex-shrink-0 mt-0.5" />
                <span className="text-slate-300">Tòa nhà Cardio Care, Khu đô thị công nghệ FPT Đà Nẵng, Ngũ Hành Sơn, Đà Nẵng</span>
              </li>
              <li className="flex items-center gap-3">
                <PhoneCall className="h-5 w-5 text-medical-coral flex-shrink-0" />
                <span className="text-slate-300">Hotline: (0236) 3 999 888</span>
              </li>
              <li className="flex items-center gap-3">
                <Mail className="h-5 w-5 text-medical-coral flex-shrink-0" />
                <span className="text-slate-300">lienhe@cardiocare.vn</span>
              </li>
            </ul>
          </div>

        </div>

        {/* Bottom copyright */}
        <div className="pt-8 border-t border-white/5 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs font-semibold text-slate-500">
          <div>
            &copy; {new Date().getFullYear()} Cardio Care. Bảo lưu mọi quyền.
          </div>
          <div className="flex items-center gap-1">
            Made with <Heart className="h-3.5 w-3.5 text-medical-coral fill-medical-coral" /> for a healthier heart.
          </div>
        </div>
      </div>

    </footer>
  );
}
