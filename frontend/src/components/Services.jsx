import React from 'react';
import { Heart, Activity, FileCheck, Stethoscope, Brain, Home, ArrowRight } from 'lucide-react';

export default function Services() {
  const services = [
    {
      icon: <Stethoscope className="h-6 w-6 text-medical-trust" />,
      title: 'Khám Tim Tổng Quát',
      desc: 'Khám lâm sàng toàn diện với bác sĩ chuyên khoa đầu ngành để tầm soát và phát hiện sớm các dấu hiệu bất thường.'
    },
    {
      icon: <Activity className="h-6 w-6 text-medical-trust" />,
      title: 'Đo Điện Tâm Đồ (ECG)',
      desc: 'Ghi lại các hoạt động điện học của tim nhằm chẩn đoán rối loạn nhịp tim, nhồi máu cơ tim và thiếu máu cơ tim.'
    },
    {
      icon: <Heart className="h-6 w-6 text-medical-trust" />,
      title: 'Siêu Âm Tim Doppler',
      desc: 'Sử dụng sóng siêu âm để quan sát trực quan hình ảnh chuyển động và đánh giá huyết động học bên trong tim.'
    },
    {
      icon: <FileCheck className="h-6 w-6 text-medical-trust" />,
      title: 'Điều Trị Cao Huyết Áp',
      desc: 'Quản lý huyết áp liên tục kết hợp phác đồ điều trị, lối sống nhằm ngăn ngừa các biến chứng tai biến mạch máu não.'
    },
    {
      icon: <Brain className="h-6 w-6 text-medical-trust" />,
      title: 'Tầm Soát Nguy Cơ Bằng AI',
      desc: 'Ứng dụng AI phân tích bộ chỉ số sức khỏe lâm sàng để ước lượng xác suất nguy cơ mắc bệnh tim mạch trong tương lai.'
    },
    {
      icon: <Home className="h-6 w-6 text-medical-trust" />,
      title: 'Chăm Sóc Từ Xa 24/7',
      desc: 'Cung cấp hệ thống theo dõi huyết áp và nhịp tim liên tục tại nhà thông qua thiết bị đeo thông minh và ứng dụng di động.'
    }
  ];

  return (
    <section id="services" className="py-20 bg-medical-light">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <h2 className="text-3xl sm:text-4xl font-extrabold text-medical-dark tracking-tight mb-4">
            Dịch vụ chăm sóc tim mạch toàn diện
          </h2>
          <p className="text-slate-500 text-base sm:text-lg font-medium">
            Chúng tôi cung cấp các giải pháp khám, tầm soát phòng ngừa và điều trị tim mạch kỹ thuật cao, kết hợp giữa lâm sàng và công nghệ y khoa hiện đại.
          </p>
        </div>

        {/* Card Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {services.map((item, idx) => (
            <div 
              key={idx}
              className="bg-white rounded-3xl p-8 border border-slate-100 shadow-md shadow-slate-100/50 hover:shadow-xl hover:shadow-slate-200/50 transition-all duration-300 hover:-translate-y-1 text-left flex flex-col justify-between group"
            >
              <div>
                <div className="w-12 h-12 rounded-2xl bg-blue-50 flex items-center justify-center mb-6 group-hover:bg-medical-trust group-hover:text-white transition-colors duration-300">
                  {item.icon}
                </div>
                <h3 className="text-xl font-bold text-medical-dark mb-3 group-hover:text-medical-trust transition-colors">
                  {item.title}
                </h3>
                <p className="text-slate-500 text-sm sm:text-base leading-relaxed mb-6">
                  {item.desc}
                </p>
              </div>
              <div className="pt-4 border-t border-slate-50 flex items-center justify-between">
                <a href="#booking" className="text-xs sm:text-sm font-bold text-medical-trust hover:text-medical-accent flex items-center gap-1">
                  Đăng ký khám
                  <ArrowRight className="h-4 w-4" />
                </a>
                <span className="text-[10px] uppercase font-bold text-slate-400">Đạt chuẩn y tế</span>
              </div>
            </div>
          ))}
        </div>

      </div>
    </section>
  );
}
