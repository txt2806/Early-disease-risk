import React from 'react';
import { Award, Users, Stethoscope, Smile } from 'lucide-react';

export default function Stats() {
  const statItems = [
    {
      icon: <Award className="h-6 w-6 text-medical-coral" />,
      value: '15+',
      label: 'Năm kinh nghiệm chuyên sâu'
    },
    {
      icon: <Smile className="h-6 w-6 text-medical-coral" />,
      value: '10K+',
      label: 'Ca chẩn đoán & khám tim thành công'
    },
    {
      icon: <Stethoscope className="h-6 w-6 text-medical-coral" />,
      value: '25+',
      label: 'Bác sĩ chuyên khoa hàng đầu'
    },
    {
      icon: <Users className="h-6 w-6 text-medical-coral" />,
      value: '99.8%',
      label: 'Bệnh nhân hài lòng và tin cậy'
    }
  ];

  return (
    <section className="relative z-20 -mt-10 sm:-mt-16 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div className="bg-white rounded-3xl border border-slate-100 shadow-xl shadow-slate-200/80 p-8 sm:p-10">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8 md:gap-12">
          {statItems.map((item, idx) => (
            <div 
              key={idx} 
              className={`flex items-start gap-4 text-left ${
                idx !== 0 ? 'lg:border-l lg:border-slate-100 lg:pl-8' : ''
              }`}
            >
              <div className="p-3 bg-rose-50 rounded-2xl flex-shrink-0">
                {item.icon}
              </div>
              <div>
                <div className="text-3xl sm:text-4xl font-extrabold text-medical-dark tracking-tight">
                  {item.value}
                </div>
                <div className="text-slate-500 font-semibold text-xs sm:text-sm mt-1 leading-relaxed">
                  {item.label}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
