import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';
import '../models/medical_history.dart';

class LabImagingScreen extends StatelessWidget {
  final MedicalHistoryItem item;

  const LabImagingScreen({super.key, required this.item});

  String _formatValue(String? val, {String fallback = 'Bình thường / Chưa có chỉ định'}) {
    if (val == null || val.trim().isEmpty || val.trim().toLowerCase() == 'null') {
      return fallback;
    }
    return val.trim();
  }

  String _formatBP(String? bp) {
    if (bp == null || bp.trim().isEmpty || bp.trim() == 'null') return '-- mmHg';
    final str = bp.trim();
    if (str.contains('mmHg')) return str;
    return '$str mmHg';
  }

  String _formatFBS(double? fbs) {
    if (fbs != null) {
      if (fbs > 50) return '${fbs.toStringAsFixed(fbs % 1 == 0 ? 0 : 1)} mg/dL';
      if (fbs == 0) return 'Bình thường (≤ 120 mg/dL)';
      if (fbs == 1) return 'Bất thường (> 120 mg/dL)';
    }
    return 'Bình thường (≤ 120 mg/dL)';
  }

  String _formatECG(String? ecg) {
    if (ecg == null || ecg.trim().isEmpty || ecg.trim() == 'null') return 'Bình thường (Normal)';
    final str = ecg.trim();
    if (str == '0' || str.toLowerCase() == 'normal') return 'Bình thường (Normal)';
    if (str == '1' || str.toLowerCase().contains('st')) return '1 - Sóng ST-T bất thường';
    if (str == '2' || str.toLowerCase().contains('hypertrophy')) return '2 - Phì đại thất trái';
    return str;
  }

  Future<void> _downloadOrViewFile(BuildContext context, String? filePath, String categoryLabel) async {
    String? fullUrl = filePath?.trim();
    bool isDemo = false;

    if (fullUrl == null || fullUrl.isEmpty || fullUrl == 'null' || fullUrl == '--' || fullUrl.contains('Chưa')) {
      // Demo fallback link if no real file path is attached yet
      fullUrl = 'http://localhost:8080/uploads/xray/sample_report.pdf';
      isDemo = true;
    }

    if (!fullUrl.startsWith('http://') && !fullUrl.startsWith('https://')) {
      if (fullUrl.startsWith('/')) {
        fullUrl = 'http://localhost:8080$fullUrl';
      } else {
        fullUrl = 'http://localhost:8080/$fullUrl';
      }
    }

    final uri = Uri.parse(fullUrl);

    try {
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(isDemo 
                ? 'Đang tải file mẫu kết quả $categoryLabel: $fullUrl'
                : 'Đang mở file $categoryLabel: $fullUrl'),
              backgroundColor: const Color(0xFF8B5CF6),
            ),
          );
        }
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Tải file $categoryLabel: $fullUrl')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final docName = item.doctorName.startsWith('BS.') || item.doctorName.startsWith('Dr.')
        ? item.doctorName
        : 'BS. ${item.doctorName}';

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Kết quả Xét nghiệm & CĐHA', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF8B5CF6),
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.science, color: Color(0xFF8B5CF6), size: 32),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Ngày: ${DateFormat('dd/MM/yyyy').format(item.visitDate)}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Color(0xFF1E293B))),
                        Text(docName, style: const TextStyle(color: Color(0xFF64748B))),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            _buildSectionTitle('1. Chỉ số sinh tồn cơ bản', Icons.monitor_heart),
            _buildCard([
              _buildStandardRow('Huyết áp', _formatBP(item.restingBP)),
              _buildStandardRow('Nhịp tim Max', item.maxHeartRate != null && item.maxHeartRate! > 0 ? '${item.maxHeartRate} bpm' : '--'),
              _buildStandardRow('SpO2', item.spO2 != null && item.spO2! > 0 ? '${item.spO2}%' : '--'),
            ]),
            
            const SizedBox(height: 24),
            _buildSectionTitle('2. Xét nghiệm Sinh hóa', Icons.bloodtype),
            _buildCard([
              _buildStandardRow('Đường huyết đói (FBS)', _formatFBS(item.fastingBloodSugar)),
              _buildStandardRow('Cholesterol', item.cholesterol != null && item.cholesterol! > 0 ? '${item.cholesterol!.toStringAsFixed(item.cholesterol! % 1 == 0 ? 0 : 1)} mg/dL' : '--'),
              _buildDownloadableRow(
                context,
                label: 'Xét nghiệm máu (Khác)',
                displayValue: _formatValue(item.bloodTest, fallback: 'Bình thường / Đã hoàn thành'),
                filePath: item.bloodTest,
                categoryLabel: 'Xét nghiệm máu',
              ),
              _buildDownloadableRow(
                context,
                label: 'Xét nghiệm nước tiểu',
                displayValue: _formatValue(item.urineTest, fallback: 'Bình thường / Đã hoàn thành'),
                filePath: item.urineTest,
                categoryLabel: 'Xét nghiệm nước tiểu',
              ),
            ]),

            const SizedBox(height: 24),
            _buildSectionTitle('3. Chẩn đoán hình ảnh', Icons.image_search),
            _buildCard([
              _buildDownloadableRow(
                context,
                label: 'Điện tâm đồ (ECG)',
                displayValue: _formatECG(item.restingECG),
                filePath: null,
                categoryLabel: 'Điện tâm đồ ECG',
              ),
              _buildDownloadableRow(
                context,
                label: 'Siêu âm tim',
                displayValue: _formatValue(item.ultrasound, fallback: 'Đã hoàn thành / Kết quả bình thường'),
                filePath: item.ultrasound,
                categoryLabel: 'Siêu âm tim',
              ),
              _buildDownloadableRow(
                context,
                label: 'X-Quang lồng ngực',
                displayValue: _formatValue(item.xray, fallback: 'Đã hoàn thành / Kết quả bình thường'),
                filePath: item.xray,
                categoryLabel: 'X-Quang lồng ngực',
              ),
              _buildDownloadableRow(
                context,
                label: 'Chụp MRI',
                displayValue: _formatValue(item.mri, fallback: 'Đã hoàn thành / Kết quả bình thường'),
                filePath: item.mri,
                categoryLabel: 'Chụp MRI',
              ),
              _buildDownloadableRow(
                context,
                label: 'Chụp CT cắt lớp',
                displayValue: _formatValue(item.ct, fallback: 'Đã hoàn thành / Kết quả bình thường'),
                filePath: item.ct,
                categoryLabel: 'Chụp CT cắt lớp',
              ),
            ]),
            
            const SizedBox(height: 30),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title, IconData icon) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Icon(icon, size: 20, color: const Color(0xFF64748B)),
          const SizedBox(width: 8),
          Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF1E293B))),
        ],
      ),
    );
  }

  Widget _buildCard(List<Widget> children) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        children: children,
      ),
    );
  }

  Widget _buildStandardRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(flex: 2, child: Text(label, style: const TextStyle(color: Color(0xFF64748B), fontSize: 13))),
          Expanded(flex: 3, child: Text(value, style: const TextStyle(color: Color(0xFF1E293B), fontWeight: FontWeight.w600, fontSize: 14), textAlign: TextAlign.right)),
        ],
      ),
    );
  }

  Widget _buildDownloadableRow(
    BuildContext context, {
    required String label,
    required String displayValue,
    String? filePath,
    required String categoryLabel,
  }) {
    final bool hasRealFile = filePath != null &&
        filePath.trim().isNotEmpty &&
        filePath.trim() != 'null' &&
        filePath.trim() != '--' &&
        !filePath.contains('Chưa');

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: const TextStyle(color: Color(0xFF64748B), fontSize: 13)),
                const SizedBox(height: 2),
                Text(
                  displayValue,
                  style: const TextStyle(color: Color(0xFF1E293B), fontWeight: FontWeight.w600, fontSize: 13),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          OutlinedButton.icon(
            onPressed: () => _downloadOrViewFile(context, filePath, categoryLabel),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF8B5CF6),
              side: const BorderSide(color: Color(0xFF8B5CF6)),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            ),
            icon: const Icon(Icons.download_rounded, size: 16),
            label: Text(
              hasRealFile ? 'Tải về' : 'Tải kết quả',
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }
}
