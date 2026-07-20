import React from 'react';
import { ArrowRight, Brain, Heart, ShieldAlert } from 'lucide-react';

export default function Hero() {
  return (
    <section id="home" className="relative bg-medical-dark pt-32 pb-24 md:pt-40 md:pb-36 overflow-hidden flex items-center min-h-[90vh]">
      
      {/* Background gradients for premium glassmorphism vibe */}
      <div className="absolute top-0 right-0 w-[45%] h-[45%] bg-blue-600/10 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-0 left-[10%] w-[35%] h-[35%] bg-rose-500/5 rounded-full blur-[100px] pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-8 items-center">
          
          {/* Text Content */}
          <div className="lg:col-span-7 flex flex-col items-start text-left">
            
            {/* AI badge */}
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-blue-500/10 border border-blue-500/20 text-blue-400 font-bold text-xs uppercase tracking-wider mb-6 animate-pulse">
              <Brain className="h-4 w-4" />
              Công nghệ Dự đoán Nguy cơ bằng AI Đạt chuẩn Y khoa
            </div>

            <h1 className="text-white text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight leading-[1.1] mb-6">
              Bảo vệ <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-blue-600">Trái tim</span> của bạn từ sớm và chuẩn xác
            </h1>

            <p className="text-slate-300 text-lg sm:text-xl leading-relaxed mb-8 max-w-xl">
              Kết hợp đột phá giữa kinh nghiệm lâm sàng của đội ngũ bác sĩ hàng đầu và hệ thống AI phân tích rủi ro tim mạch từ các chỉ số cơ bản.
            </p>

            {/* Action Buttons */}
            <div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto">
              <a
                href="#booking"
                className="inline-flex items-center justify-center bg-medical-coral hover:bg-medical-coralhover text-white px-8 py-4 rounded-full text-base font-bold shadow-lg shadow-medical-coral/20 transition-all duration-200 hover:-translate-y-0.5"
              >
                Đặt lịch khám ngay
                <ArrowRight className="ml-2 h-5 w-5" />
              </a>
              <a
                href="#services"
                className="inline-flex items-center justify-center bg-white/10 hover:bg-white/15 border border-white/20 text-white px-8 py-4 rounded-full text-base font-bold transition-all duration-200"
              >
                Tìm hiểu dịch vụ
              </a>
            </div>

            {/* Micro USP list */}
            <div className="flex flex-wrap gap-6 mt-10 border-t border-white/10 pt-8 w-full">
              <div className="flex items-center gap-2.5">
                <Heart className="h-5 w-5 text-medical-coral" />
                <span className="text-sm font-semibold text-slate-300">Tim mạch chuyên sâu</span>
              </div>
              <div className="flex items-center gap-2.5">
                <Brain className="h-5 w-5 text-blue-400" />
                <span className="text-sm font-semibold text-slate-300">AI dự báo nguy cơ</span>
              </div>
              <div className="flex items-center gap-2.5">
                <ShieldAlert className="h-5 w-5 text-green-400" />
                <span className="text-sm font-semibold text-slate-300">Bảo mật bệnh án 100%</span>
              </div>
            </div>

          </div>

          {/* Glowing Cardiovascular Graphic (Right side) */}
          <div className="lg:col-span-5 flex justify-center items-center relative h-[380px] sm:h-[450px]">
            
            {/* Pulsating circles background */}
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="w-[300px] h-[300px] rounded-full border border-blue-500/20 animate-[ping_3s_infinite]" />
              <div className="absolute w-[220px] h-[220px] rounded-full border border-rose-500/20 animate-[ping_4s_infinite]" />
            </div>

            {/* Main Interactive Graphic Dashboard representation */}
            <div className="relative bg-slate-900/80 border border-white/15 rounded-3xl p-6 w-[320px] sm:w-[350px] shadow-2xl backdrop-blur-md">
              <div className="flex justify-between items-center mb-6">
                <div className="flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-green-500 animate-pulse" />
                  <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Hệ thống giám sát AI</span>
                </div>
                <span className="text-xs font-semibold text-blue-400">92% Accuracy</span>
              </div>

              {/* Heart graphic placeholder with custom SVG pulse */}
              <div className="h-44 bg-slate-950/70 rounded-2xl border border-white/5 flex flex-col justify-center items-center relative overflow-hidden mb-6">
                <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,rgba(244,63,94,0.1),transparent)]" />
                
                {/* Neon heartbeat pulse path */}
                <svg className="absolute w-full h-24 stroke-medical-coral fill-none" viewBox="0 0 100 30" preserveAspectRatio="none">
                  <path d="M0,15 L35,15 L38,10 L41,20 L44,2 L47,28 L50,15 L53,12 L56,17 L60,15 L100,15" 
                        strokeWidth="1.5" 
                        strokeLinecap="round" 
                        strokeLinejoin="round" 
                        className="animate-[dash_2s_linear_infinite]"
                        style={{
                          strokeDasharray: 100,
                          strokeDashoffset: 100
                        }}
                  />
                </svg>
                
                <Heart className="h-14 w-14 text-medical-coral drop-shadow-[0_0_12px_rgba(244,63,94,0.6)] animate-pulse" />
                <span className="text-xs font-bold text-slate-400 mt-4">ECG Live Monitor: 72 BPM</span>
              </div>

              {/* Live Metric Stats grid */}
              <div className="grid grid-cols-2 gap-3 text-left">
                <div className="bg-white/5 border border-white/5 rounded-xl p-3">
                  <div className="text-[10px] text-slate-400">Huyết áp</div>
                  <div className="text-sm font-bold text-white mt-0.5">120/80 mmHg</div>
                </div>
                <div className="bg-white/5 border border-white/5 rounded-xl p-3">
                  <div className="text-[10px] text-slate-400">Độ bão hòa Oxy</div>
                  <div className="text-sm font-bold text-white mt-0.5">99% SpO2</div>
                </div>
              </div>
            </div>

          </div>

        </div>
      </div>

      {/* Tailwind specific custom pulse dash keyframes injected as styles */}
      <style>{`
        @keyframes dash {
          to {
            stroke-dashoffset: 0;
          }
        }
      `}</style>
    </section>
  );
}
