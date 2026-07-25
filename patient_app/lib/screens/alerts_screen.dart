import 'package:flutter/material.dart';
import '../services/patient_service.dart';
import '../models/alert_item.dart';
import '../widgets/alert_card.dart';

class AlertsScreen extends StatefulWidget {
  const AlertsScreen({super.key});

  @override
  State<AlertsScreen> createState() => _AlertsScreenState();
}

class _AlertsScreenState extends State<AlertsScreen> {
  final PatientService _service = PatientService();
  AlertSeverity? _selectedSeverity;

  @override
  void initState() {
    super.initState();
    _service.addListener(_onServiceUpdate);
  }

  @override
  void dispose() {
    _service.removeListener(_onServiceUpdate);
    super.dispose();
  }

  void _onServiceUpdate() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final alerts = _service.alertsList.where((a) {
      if (_selectedSeverity == null) return true;
      return a.severity == _selectedSeverity;
    }).toList();

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Cảnh báo bệnh sớm & Cấp tính',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
        ),
        backgroundColor: const Color(0xFFDC2626),
        foregroundColor: Colors.white,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.done_all_rounded),
            tooltip: 'Đánh dấu tất cả là đã đọc',
            onPressed: () {
              _service.markAllAlertsAsRead();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Đã đánh dấu tất cả cảnh báo là đã đọc'),
                  duration: Duration(seconds: 2),
                ),
              );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // Emergency Call Banner
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: Colors.red.shade50,
              border: Border(bottom: BorderSide(color: Colors.red.shade200)),
            ),
            child: Row(
              children: [
                const Icon(Icons.phone_in_talk_rounded, color: Colors.red, size: 24),
                const SizedBox(width: 10),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Đường dây nóng Cấp tính Tim mạch',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.red),
                      ),
                      Text(
                        'Gọi 115 hoặc Trạm y tế CardioCare khẩn cấp',
                        style: TextStyle(fontSize: 11, color: Colors.black87),
                      ),
                    ],
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: () {
                    showDialog(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: const Row(
                          children: [
                            Icon(Icons.warning, color: Colors.red),
                            SizedBox(width: 8),
                            Text('Gọi cấp cứu 115'),
                          ],
                        ),
                        content: const Text('Bạn có chắc chắn muốn kích hoạt cuộc gọi cấp cứu khẩn cấp không?'),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx),
                            child: const Text('Hủy'),
                          ),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
                            onPressed: () {
                              Navigator.pop(ctx);
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Đang kết nối cuộc gọi cấp cứu khẩn cấp 115...')),
                              );
                            },
                            child: const Text('Gọi ngay'),
                          ),
                        ],
                      ),
                    );
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                  icon: const Icon(Icons.call, size: 16),
                  label: const Text('GỌI 115', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                ),
              ],
            ),
          ),

          // Filter Segment Chips
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 10.0),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _buildFilterChip(label: 'Tất cả (${_service.alertsList.length})', severity: null),
                  const SizedBox(width: 8),
                  _buildFilterChip(label: '🔴 Cấp tính', severity: AlertSeverity.critical),
                  const SizedBox(width: 8),
                  _buildFilterChip(label: '🟠 Cảnh báo sớm', severity: AlertSeverity.warning),
                  const SizedBox(width: 8),
                  _buildFilterChip(label: '🔵 Thông tin', severity: AlertSeverity.info),
                ],
              ),
            ),
          ),

          // Alerts List
          Expanded(
            child: alerts.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.shield_outlined, size: 60, color: Colors.green.shade400),
                        const SizedBox(height: 12),
                        const Text(
                          'Không có cảnh báo sức khỏe nào trong mục này',
                          style: TextStyle(color: Colors.grey, fontSize: 14),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    physics: const BouncingScrollPhysics(),
                    itemCount: alerts.length,
                    itemBuilder: (context, index) {
                      final item = alerts[index];
                      return AlertCard(
                        alert: item,
                        onTap: () {
                          _service.markAlertAsRead(item.id);
                          _showAlertDetailDialog(context, item);
                        },
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip({required String label, required AlertSeverity? severity}) {
    final isSelected = _selectedSeverity == severity;
    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (selected) {
        if (selected) {
          setState(() {
            _selectedSeverity = severity;
          });
        }
      },
      selectedColor: Colors.red.shade100,
      labelStyle: TextStyle(
        color: isSelected ? Colors.red.shade900 : Colors.grey.shade800,
        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        fontSize: 12,
      ),
    );
  }

  void _showAlertDetailDialog(BuildContext context, HealthAlertItem alert) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 30),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    alert.severity == AlertSeverity.critical
                        ? Icons.warning_rounded
                        : Icons.info_outline,
                    color: alert.severity == AlertSeverity.critical ? Colors.red : Colors.orange,
                    size: 28,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      alert.title,
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                  ),
                ],
              ),
              const Divider(height: 24),
              Text(
                'Nội dung cảnh báo:',
                style: TextStyle(fontWeight: FontWeight.bold, color: Colors.grey.shade700),
              ),
              const SizedBox(height: 4),
              Text(alert.description, style: const TextStyle(fontSize: 14)),
              if (alert.severityScore > 0) ...[
                const SizedBox(height: 8),
                Text('Mức độ đau/khó chịu: ${alert.severityScore}/10',
                    style: const TextStyle(fontSize: 13, color: Colors.black87)),
              ],
              if (alert.duration.isNotEmpty) ...[
                const SizedBox(height: 6),
                Text('Thời gian: ${alert.duration}',
                    style: const TextStyle(fontSize: 13, color: Colors.black87)),
              ],
              const SizedBox(height: 6),
              Text('Mô tả chi tiết: ${alert.notes.isNotEmpty ? alert.notes : "Không có"}',
                  style: const TextStyle(fontSize: 13, color: Colors.black87)),
              const SizedBox(height: 16),
              Text(
                'Chỉ số ghi nhận:',
                style: TextStyle(fontWeight: FontWeight.bold, color: Colors.grey.shade700),
              ),
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  alert.relatedMetric,
                  style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.teal),
                ),
              ),
              const SizedBox(height: 16),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.teal.shade50,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.teal.shade200),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.psychology, color: Colors.teal.shade800, size: 20),
                        const SizedBox(width: 6),
                        Text(
                          'Lời khuyên xử lý từ AI CardioCare:',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                            color: Colors.teal.shade900,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      alert.recommendation,
                      style: TextStyle(fontSize: 13, color: Colors.teal.shade900, height: 1.4),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.teal.shade700,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Đóng'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
