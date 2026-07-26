import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../services/patient_service.dart';
import '../widgets/symptom_chip.dart';
import '../models/symptom_report.dart';

class SymptomUpdateScreen extends StatefulWidget {
  const SymptomUpdateScreen({super.key});

  @override
  State<SymptomUpdateScreen> createState() => _SymptomUpdateScreenState();
}

class _SymptomUpdateScreenState extends State<SymptomUpdateScreen> {
  final PatientService _service = PatientService();

  final List<String> _commonSymptoms = [
    'Đau ngực dữ dội',
    'Khó thở / Thở dốc',
    'Chóng mặt / Xây xẩm',
    'Hồi hộp đánh trống ngực',
    'Vã mồ hôi lạnh',
    'Mệt mỏi kiệt sức',
    'Đau lan ra vai / tay trái',
    'Tê bì chân tay',
    'Buồn nôn / Nôn',
    'Đau đầu dồn dập',
  ];

  final List<String> _urgentSymptoms = [
    'Đau ngực dữ dội',
    'Khó thở / Thở dốc',
    'Đau lan ra vai / tay trái',
  ];

  final List<String> _selectedSymptoms = [];
  double _severityScore = 5.0;
  final TextEditingController _heartRateController = TextEditingController(text: '80');
  String _selectedDuration = 'Khoảng 30 phút';
  final TextEditingController _notesController = TextEditingController();
  bool _isSubmitting = false;

  final List<String> _durations = [
    'Vừa xuất hiện (< 15 phút)',
    'Khoảng 30 phút',
    'Từ 1 đến 3 giờ',
    'Từ 3 đến 6 giờ',
    'Kéo dài trên 24 giờ',
  ];

  @override
  void dispose() {
    _notesController.dispose();
    _heartRateController.dispose();
    super.dispose();
  }

  Future<void> _submitSymptomReport() async {
    if (_selectedSymptoms.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vui lòng chọn ít nhất 1 triệu chứng bạn đang gặp phải'),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    final parsedHr = int.tryParse(_heartRateController.text.trim()) ?? 80;

    setState(() {
      _isSubmitting = true;
    });

    final report = await _service.submitSymptomUpdate(
      selectedSymptoms: List.from(_selectedSymptoms),
      severityScore: _severityScore.round(),
      heartRate: parsedHr,
      duration: _selectedDuration,
      notes: _notesController.text.trim(),
    );

    setState(() {
      _isSubmitting = false;
    });

    if (mounted) {
      _showResultModal(context, report);
    }
  }

  void _showResultModal(BuildContext context, SymptomReport report) {
    final bool isHighRisk = report.severityScore >= 8 ||
        report.symptoms.any((s) => _urgentSymptoms.contains(s));

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Row(
          children: [
            Icon(
              isHighRisk ? Icons.warning_amber_rounded : Icons.check_circle_outline,
              color: isHighRisk ? Colors.red : Colors.teal,
              size: 30,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                isHighRisk ? 'CẢNH BÁO NGUY CƠ CAO' : 'Đã ghi nhận triệu chứng',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: isHighRisk ? Colors.red : Colors.teal.shade900,
                ),
              ),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: isHighRisk ? Colors.red.shade50 : Colors.teal.shade50,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: isHighRisk ? Colors.red.shade200 : Colors.teal.shade200,
                ),
              ),
              child: Text(
                report.aiRiskAssessment,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: isHighRisk ? Colors.red.shade900 : Colors.teal.shade900,
                ),
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              'Triệu chứng đã chọn:',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
            ),
            const SizedBox(height: 4),
            Text(report.symptoms.join(' • '), style: const TextStyle(fontSize: 13)),
            const SizedBox(height: 8),
            Text('Mức độ đau/khó chịu: ${report.severityScore}/10',
                style: const TextStyle(fontSize: 13)),
            const SizedBox(height: 8),
            Text('Thời gian: ${report.duration}',
                style: const TextStyle(fontSize: 13)),
            const SizedBox(height: 14),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFF0FDF4),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFBBF7D0)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.psychology, color: Color(0xFF166534), size: 18),
                      SizedBox(width: 6),
                      Text('Lời khuyên xử lý từ AI:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Color(0xFF166534))),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    report.aiAdvice,
                    style: const TextStyle(fontSize: 12, color: Color(0xFF1E293B), height: 1.4),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          if (isHighRisk)
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red,
                foregroundColor: Colors.white,
              ),
              onPressed: () {
                Navigator.pop(ctx);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Đang kết nối số điện thoại bác sĩ tư vấn khẩn cấp...')),
                );
              },
              icon: const Icon(Icons.call, size: 18),
              label: const Text('GỌI BÁC SĨ TƯ VẤN'),
            ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              setState(() {
                _selectedSymptoms.clear();
                _severityScore = 5.0;
                _selectedDuration = 'Khoảng 30 phút';
                _notesController.clear();
              });
            },
            child: const Text('Đóng'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('HH:mm - dd/MM/yyyy');

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Cập nhật triệu chứng mới',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
        ),
        backgroundColor: const Color(0xFF0D9488),
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Info Header
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.teal.shade50,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: Colors.teal.shade200),
              ),
              child: Row(
                children: [
                  const Icon(Icons.info_outline, color: Colors.teal, size: 24),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Thời gian ghi nhận: ${dateFormat.format(DateTime.now())}\nBáo cáo sẽ tự động phân tích nguy cơ cấp tính và cảnh báo cho Bác sĩ.',
                      style: const TextStyle(fontSize: 12, color: Colors.black87),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),
            const Text(
              '1. Chọn triệu chứng bạn đang gặp phải (*)',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
            ),
            const SizedBox(height: 6),
            const Text(
              'Các triệu chứng màu đỏ có thể là dấu hiệu tim mạch cấp tính.',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
            const SizedBox(height: 12),

            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: _commonSymptoms.map((symptom) {
                final isUrgent = _urgentSymptoms.contains(symptom);
                final isSelected = _selectedSymptoms.contains(symptom);
                return SymptomChip(
                  label: symptom,
                  isSelected: isSelected,
                  isUrgent: isUrgent,
                  onSelected: (selected) {
                    setState(() {
                      if (selected) {
                        _selectedSymptoms.add(symptom);
                      } else {
                        _selectedSymptoms.remove(symptom);
                      }
                    });
                  },
                );
              }).toList(),
            ),

            const SizedBox(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '2. Mức độ khó chịu / Đau',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: _severityScore >= 8
                        ? Colors.red
                        : (_severityScore >= 5 ? Colors.orange : Colors.teal),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Text(
                    '${_severityScore.round()} / 10',
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Slider(
              value: _severityScore,
              min: 1.0,
              max: 10.0,
              divisions: 9,
              activeColor: _severityScore >= 8
                  ? Colors.red
                  : (_severityScore >= 5 ? Colors.orange : Colors.teal),
              inactiveColor: Colors.grey.shade300,
              label: '${_severityScore.round()}',
              onChanged: (val) {
                setState(() {
                  _severityScore = val;
                });
              },
            ),
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('1 - Rất nhẹ', style: TextStyle(fontSize: 11, color: Colors.grey)),
                Text('5 - Vừa phải', style: TextStyle(fontSize: 11, color: Colors.grey)),
                Text('10 - Rất dữ dội', style: TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),

            const SizedBox(height: 24),
            const Text(
              '3. Nhịp tim hiện tại (BPM)',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _heartRateController,
                    keyboardType: TextInputType.number,
                    decoration: InputDecoration(
                      hintText: 'Nhập nhịp tim (ví dụ: 75, 90, 120...)',
                      suffixText: 'BPM',
                      suffixStyle: const TextStyle(fontWeight: FontWeight.bold, color: Colors.teal),
                      filled: true,
                      fillColor: Colors.white,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(color: Colors.grey.shade300),
                      ),
                    ),
                    onChanged: (val) {
                      setState(() {});
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text(
              '4. Thời gian xuất hiện triệu chứng',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: _selectedDuration,
              decoration: InputDecoration(
                filled: true,
                fillColor: Colors.white,
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(color: Colors.grey.shade300),
                ),
              ),
              items: _durations
                  .map((d) => DropdownMenuItem(value: d, child: Text(d, style: const TextStyle(fontSize: 13))))
                  .toList(),
              onChanged: (val) {
                if (val != null) {
                  setState(() {
                    _selectedDuration = val;
                  });
                }
              },
            ),

            const SizedBox(height: 24),
            const Text(
              '4. Mô tả chi tiết bổ sung (không bắt buộc)',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _notesController,
              maxLines: 3,
              decoration: InputDecoration(
                hintText: 'Ví dụ: Triệu chứng đau nhói từng cơn, vã mồ hôi khi làm việc nặng...',
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(color: Colors.grey.shade300),
                ),
              ),
            ),

            const SizedBox(height: 30),
            SizedBox(
              width: double.infinity,
              height: 52,
              child: ElevatedButton.icon(
                onPressed: _isSubmitting ? null : _submitSymptomReport,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF0D9488),
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  elevation: 2,
                ),
                icon: _isSubmitting
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                      )
                    : const Icon(Icons.send_rounded),
                label: Text(
                  _isSubmitting ? 'Đang phân tích & gửi...' : 'Gửi cập nhật triệu chứng',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
            ),

            const SizedBox(height: 24),

            // History Log Section
            if (_service.symptomReports.isNotEmpty) ...[
              const Divider(height: 30),
              const Text(
                'Lịch sử khai báo triệu chứng gần đây',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
              ),
              const SizedBox(height: 10),
              ..._service.symptomReports.map(
                (report) => Card(
                  margin: const EdgeInsets.only(bottom: 10),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: report.severityScore >= 8 ? Colors.red.shade100 : Colors.teal.shade100,
                      child: Icon(
                        Icons.medical_services_outlined,
                        color: report.severityScore >= 8 ? Colors.red : Colors.teal,
                      ),
                    ),
                    title: Text(
                      report.symptoms.join(', '),
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                    ),
                    subtitle: Text(
                      'Mức độ: ${report.severityScore}/10 • ${DateFormat("HH:mm - dd/MM").format(report.timestamp)}',
                      style: const TextStyle(fontSize: 11),
                    ),
                  ),
                ),
              ),
            ],

            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}
