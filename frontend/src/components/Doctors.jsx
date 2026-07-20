import React from 'react';
import { Award, Calendar, ChevronRight, Stethoscope } from 'lucide-react';

export default function Doctors() {
  const doctors = [
    {
      name: 'PGS.TS.BS. Nguyễn Văn An',
      title: 'Trưởng khoa Tim mạch Can thiệp',
      specialty: 'Khám tim tổng quát & Điều trị mạch vành',
      exp: '25+ năm kinh nghiệm',
      grad: 'Đại học Y Hà Nội & Học viện Tim quốc gia Pháp',
      avatarText: 'AN'
    },
    {
      name: 'ThS.BS. Trần Thị Bình',
      title: 'Phó khoa Chẩn đoán Hình ảnh',
      specialty: 'Siêu âm tim Doppler & Tầm soát tim bẩm sinh',
      exp: '15+ năm kinh nghiệm',
      grad: 'Đại học Y Dược TP.HCM',
      avatarText: 'BI'
    },
    {
      name: 'TS.BS. Lê Hoàng Đức',
      title: 'Chuyên gia Nhịp học Lâm sàng',
      specialty: 'Đo điện tâm đồ (ECG) & Loạn nhịp tim',
      exp: '18+ năm kinh nghiệm',
      grad: 'Đại học Y khoa Saint-Antoine, Pháp',
      avatarText: 'DU'
    }
  ];

  return (
    <section id="doctors" className="py-20 bg-medical-gray">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <h2 className="text-3xl sm:text-4xl font-extrabold text-medical-dark tracking-tight mb-4">
            Đội ngũ bác sĩ tim mạch đầu ngành
          </h2>
          <p className="text-slate-500 text-base sm:text-lg font-medium">
            Quy tụ các phó giáo sư, tiến sĩ và thạc sĩ được đào tạo chuyên sâu tại Việt Nam và nước ngoài, tận tâm vì sức khỏe người bệnh.
          </p>
        </div>

        {/* Doctor Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {doctors.map((doc, idx) => (
            <div 
              key={idx}
              className="bg-white rounded-3xl overflow-hidden border border-slate-100 shadow-md hover:shadow-xl transition-all duration-300 hover:-translate-y-1 flex flex-col h-full text-left"
            >
              
              {/* Avatar section - Designed with premium medical brand color gradients */}
              <div className="h-48 bg-gradient-to-br from-medical-dark to-slate-800 flex items-center justify-center relative">
                <div className="absolute top-4 right-4 bg-white/15 backdrop-blur-md px-3.5 py-1.5 rounded-full border border-white/10 flex items-center gap-1.5">
                  <Award className="h-4 w-4 text-medical-coral" />
                  <span className="text-[10px] text-white font-bold uppercase tracking-wider">Chuyên gia cấp cao</span>
                </div>
                <div className="w-24 h-24 rounded-full bg-white flex items-center justify-center text-medical-trust font-extrabold text-3xl shadow-lg border-4 border-slate-700/50">
                  {doc.avatarText}
                </div>
              </div>

              {/* Doctor Details */}
              <div className="p-8 flex-1 flex flex-col justify-between">
                <div>
                  <h3 className="text-xl font-bold text-medical-dark mb-1">
                    {doc.name}
                  </h3>
                  <div className="text-sm font-bold text-medical-coral mb-4">
                    {doc.title}
                  </div>
                  
                  <div className="space-y-3.5 mb-6 text-sm text-slate-500">
                    <div className="flex items-start gap-2">
                      <Stethoscope className="h-4.5 w-4.5 text-medical-trust flex-shrink-0 mt-0.5" />
                      <div><strong>Chuyên môn:</strong> {doc.specialty}</div>
                    </div>
                    <div className="flex items-start gap-2">
                      <Calendar className="h-4.5 w-4.5 text-medical-trust flex-shrink-0 mt-0.5" />
                      <div><strong>Kinh nghiệm:</strong> {doc.exp}</div>
                    </div>
                  </div>
                </div>

                <div className="pt-6 border-t border-slate-50">
                  <a 
                    href="#booking"
                    className="w-full inline-flex items-center justify-center bg-medical-trust hover:bg-medical-accent text-white py-3 px-6 rounded-2xl text-sm font-bold transition-all duration-200"
                  >
                    Đặt lịch với bác sĩ
                    <ChevronRight className="ml-1.5 h-4 w-4" />
                  </a>
                </div>
              </div>

            </div>
          ))}
        </div>

      </div>
    </section>
  );
}
