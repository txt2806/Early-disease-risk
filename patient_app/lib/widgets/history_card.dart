import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/medical_history.dart';
import '../screens/lab_imaging_screen.dart';
import '../screens/ai_risk_detail_screen.dart';

class HistoryCard extends StatefulWidget {
  final MedicalHistoryItem item;

  const HistoryCard({super.key, required this.item});

  @override
  State<HistoryCard> createState() => _HistoryCardState();
}

class _HistoryCardState extends State<HistoryCard> {
  bool _isExpanded = false;

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('dd/MM/yyyy');

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      elevation: 0,
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFFE2E8F0)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFDC3545).withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.local_hospital_rounded, color: Color(0xFFDC3545), size: 24),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.item.doctorSpecialty,
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFFDC3545),
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        widget.item.doctorName,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF1E293B),
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFF1E293B),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    dateFormat.format(widget.item.visitDate),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(height: 24, color: Color(0xFFF1F5F9)),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Padding(
                  padding: EdgeInsets.only(top: 2),
                  child: Icon(Icons.medical_services_outlined, size: 16, color: Color(0xFF64748B)),
                ),
                const SizedBox(width: 6),
                const Text(
                  'Chẩn đoán: ',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Color(0xFF1E293B)),
                ),
                Expanded(
                  child: Text(
                    widget.item.diagnosis,
                    style: const TextStyle(fontSize: 13, color: Color(0xFF334155)),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                const Icon(Icons.meeting_room_outlined, size: 16, color: Color(0xFF64748B)),
                const SizedBox(width: 6),
                Text(
                  widget.item.clinicRoom,
                  style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                ),
                const Spacer(),
                Text(
                  'Mã hs: ${widget.item.id}',
                  style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
                ),
              ],
            ),
            if (_isExpanded) ...[
              const SizedBox(height: 12),
              
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        Navigator.push(context, MaterialPageRoute(builder: (_) => LabImagingScreen(item: widget.item)));
                      },
                      icon: const Icon(Icons.science, size: 16, color: Color(0xFF8B5CF6)),
                      label: const Text('KQ Xét nghiệm & CĐHA', style: TextStyle(fontSize: 11, color: Color(0xFF1E293B))),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        Navigator.push(context, MaterialPageRoute(builder: (_) => AIRiskDetailScreen(item: widget.item)));
                      },
                      icon: const Icon(Icons.psychology, size: 16, color: Color(0xFFF59E0B)),
                      label: const Text('Cảnh báo AI', style: TextStyle(fontSize: 11, color: Color(0xFF1E293B))),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '💊 Phác đồ & Đơn thuốc điều trị:',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12, color: Color(0xFFDC3545)),
                    ),
                    const SizedBox(height: 4),
                    ...widget.item.medications.map(
                      (m) => Padding(
                        padding: const EdgeInsets.only(left: 8.0, top: 3.0),
                        child: Row(
                          children: [
                            const Icon(Icons.check_circle_outline, size: 14, color: Color(0xFFDC3545)),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(m, style: const TextStyle(fontSize: 12, color: Color(0xFF334155))),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      '📝 Ghi chú của Bác sĩ:',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12, color: Color(0xFFDC3545)),
                    ),
                    const SizedBox(height: 4),
                    Padding(
                      padding: const EdgeInsets.only(left: 8.0),
                      child: Text(
                        widget.item.doctorNotes,
                        style: const TextStyle(fontSize: 12, color: Color(0xFF334155), fontStyle: FontStyle.italic),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.center,
              child: TextButton.icon(
                onPressed: () {
                  setState(() {
                    _isExpanded = !_isExpanded;
                  });
                },
                icon: Icon(
                  _isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                  color: const Color(0xFFDC3545),
                  size: 20,
                ),
                label: Text(
                  _isExpanded ? 'Thu gọn chi tiết' : 'Xem chi tiết đợt điều trị',
                  style: const TextStyle(color: Color(0xFFDC3545), fontSize: 12, fontWeight: FontWeight.bold),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
