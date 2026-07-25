import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../services/patient_service.dart';
import '../models/alert_item.dart';
import 'medical_history_screen.dart';
import 'alerts_screen.dart';
import 'appointments_screen.dart';
import 'chat_screen.dart';
import 'lab_imaging_screen.dart';
import 'ai_risk_detail_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final PatientService _service = PatientService();

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
    final profile = _service.profile;
    final unreadAlerts = _service.unreadAlertsCount;
    final historyList = _service.historyList;
    final alertsList = _service.alertsList;
    final hasActiveAlert = alertsList.any((a) => a.severity == AlertSeverity.critical);

    // Latest risk prediction from history
    String latestRiskLevel = 'MEDIUM (69.7%)';
    if (historyList.isNotEmpty) {
      final latestNotes = historyList.first.doctorNotes;
      if (latestNotes.contains('Mức nguy cơ AI:')) {
        final lines = latestNotes.split('\n');
        final riskLine = lines.firstWhere((l) => l.contains('Mức nguy cơ AI:'), orElse: () => '');
        if (riskLine.isNotEmpty) {
          latestRiskLevel = riskLine.replaceAll('📌 Mức nguy cơ AI:', '').trim();
        }
      }
    }

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC), // Light Slate Background
      appBar: AppBar(
        elevation: 0,
        backgroundColor: Colors.white,
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFFDC3545), Color(0xFFC82333)],
                ),
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFFDC3545).withValues(alpha: 0.3),
                    blurRadius: 10,
                  ),
                ],
              ),
              child: const Icon(Icons.favorite, color: Colors.white, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    profile.fullName.isNotEmpty ? profile.fullName : 'Bệnh nhân CardioCare',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF1E293B),
                    ),
                  ),
                  Text(
                    'Mã BN: #${profile.patientId > 0 ? profile.patientId : "1001"} • CardioCare Portal',
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF64748B),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          Stack(
            children: [
              IconButton(
                icon: const Icon(Icons.notifications_none_rounded, color: Color(0xFF1E293B)),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const AlertsScreen()),
                  );
                },
              ),
              if (unreadAlerts > 0)
                Positioned(
                  right: 8,
                  top: 8,
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: Color(0xFFDC3545),
                      shape: BoxShape.circle,
                    ),
                    constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                    child: Text(
                      '$unreadAlerts',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          if (profile.patientId > 0) {
            await _service.fetchLiveHistoryFromSpringBoot(profile.patientId);
            await _service.fetchLiveAlertsFromSpringBoot(profile.patientId);
          }
        },
        child: SingleChildScrollView(
          physics: const BouncingScrollPhysics(),
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 1. Web Style 4-Stats Grid Cards
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: 2.3,
                children: [
                  _buildWebStatCard(
                    title: 'Nhịp tim gần nhất',
                    value: '78 BPM',
                    icon: Icons.monitor_heart_outlined,
                    accentColor: const Color(0xFFDC3545),
                  ),
                  GestureDetector(
                    onTap: () {
                      if (historyList.isNotEmpty) {
                        Navigator.push(context, MaterialPageRoute(builder: (_) => AIRiskDetailScreen(item: historyList.first)));
                      }
                    },
                    child: _buildWebStatCard(
                      title: 'Đánh giá rủi ro AI',
                      value: latestRiskLevel,
                      icon: Icons.psychology_outlined,
                      accentColor: const Color(0xFFF59E0B),
                    ),
                  ),
                  _buildWebStatCard(
                    title: 'Tổng số lần khám',
                    value: '${historyList.length} lần',
                    icon: Icons.calendar_month_outlined,
                    accentColor: const Color(0xFF3B82F6),
                  ),
                  _buildWebStatCard(
                    title: 'Trạng thái hiện tại',
                    value: hasActiveAlert ? '🚨 Cảnh báo' : '🟢 Bình thường',
                    icon: hasActiveAlert ? Icons.warning_amber_rounded : Icons.check_circle_outline,
                    accentColor: hasActiveAlert ? const Color(0xFFDC3545) : const Color(0xFF10B981),
                  ),
                ],
              ),

              const SizedBox(height: 24),

              // 2. Navigation Actions Grid (Web Theme Style)
              const Text(
                'DANH MỤC CHỨC NĂNG',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF64748B),
                  letterSpacing: 0.8,
                ),
              ),
              const SizedBox(height: 12),

              Row(
                children: [
                  _buildWebActionButton(
                    context,
                    label: 'Đặt lịch khám',
                    icon: Icons.calendar_today_rounded,
                    color: const Color(0xFF3B82F6),
                    onTap: () {
                      Navigator.push(context, MaterialPageRoute(builder: (_) => const AppointmentsScreen()));
                    },
                  ),
                  const SizedBox(width: 10),
                  _buildWebActionButton(
                    context,
                    label: 'Trợ lý AI Y tế',
                    icon: Icons.smart_toy_rounded,
                    color: const Color(0xFF10B981),
                    onTap: () {
                      Navigator.push(context, MaterialPageRoute(builder: (_) => const ChatScreen()));
                    },
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  _buildWebActionButton(
                    context,
                    label: 'Kết quả xét nghiệm',
                    icon: Icons.science_outlined,
                    color: const Color(0xFF8B5CF6),
                    onTap: () {
                      if (historyList.isNotEmpty) {
                        Navigator.push(context, MaterialPageRoute(builder: (_) => LabImagingScreen(item: historyList.first)));
                      } else {
                        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Chưa có lịch sử khám bệnh')));
                      }
                    },
                  ),
                  const SizedBox(width: 10),
                  _buildWebActionButton(
                    context,
                    label: 'Cảnh báo sức khỏe',
                    icon: Icons.notification_important_outlined,
                    color: const Color(0xFFDC3545),
                    badgeCount: unreadAlerts,
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const AlertsScreen()),
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 24),

              // 3. Web Table Card: Hồ sơ & Bệnh án gần đây
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.04),
                      blurRadius: 15,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Row(
                          children: [
                            Icon(Icons.assignment_late_outlined, color: Color(0xFFDC3545), size: 20),
                            SizedBox(width: 8),
                            Text(
                              'Hồ sơ & Bệnh án của tôi',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                color: Color(0xFF1E293B),
                              ),
                            ),
                          ],
                        ),
                        TextButton(
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const MedicalHistoryScreen()),
                          ),
                          child: const Text('Xem tất cả →', style: TextStyle(color: Color(0xFF3B82F6), fontSize: 13)),
                        ),
                      ],
                    ),
                    const Divider(color: Color(0xFFF1F5F9), height: 20),

                    if (historyList.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(24.0),
                        child: Center(
                          child: Text(
                            'Chưa có lịch sử bệnh án trong hệ thống.',
                            style: TextStyle(color: Color(0xFF64748B), fontSize: 14),
                          ),
                        ),
                      )
                    else
                      ...historyList.take(3).map(
                            (item) => Padding(
                              padding: const EdgeInsets.only(bottom: 12.0),
                              child: Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFF8FAFC),
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(color: const Color(0xFFE2E8F0)),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                        Text(
                                          DateFormat('dd/MM/yyyy HH:mm').format(item.visitDate),
                                          style: const TextStyle(
                                            fontSize: 13,
                                            fontWeight: FontWeight.w600,
                                            color: Color(0xFF64748B),
                                          ),
                                        ),
                                        Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                          decoration: BoxDecoration(
                                            color: const Color(0xFFF59E0B).withValues(alpha: 0.15),
                                            borderRadius: BorderRadius.circular(6),
                                            border: Border.all(color: const Color(0xFFF59E0B).withValues(alpha: 0.3)),
                                          ),
                                          child: const Text(
                                            'MEDIUM',
                                            style: TextStyle(
                                              color: Color(0xFFD97706),
                                              fontSize: 11,
                                              fontWeight: FontWeight.bold,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 6),
                                    Text(
                                      item.doctorName,
                                      style: const TextStyle(
                                        fontSize: 15,
                                        fontWeight: FontWeight.bold,
                                        color: Color(0xFF1E293B),
                                      ),
                                    ),
                                    Text(
                                      item.doctorSpecialty,
                                      style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                                    ),
                                    const SizedBox(height: 8),
                                    Text(
                                      'Chẩn đoán: ${item.diagnosis}',
                                      style: const TextStyle(fontSize: 13, color: Color(0xFF334155)),
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                  ],
                ),
              ),

              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildWebStatCard({
    required String title,
    required String value,
    required IconData icon,
    required Color accentColor,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                title,
                style: const TextStyle(fontSize: 12, color: Color(0xFF64748B), fontWeight: FontWeight.w500),
              ),
              Icon(icon, color: accentColor, size: 20),
            ],
          ),
          Text(
            value,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: accentColor,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  Widget _buildWebActionButton(
    BuildContext context, {
    required String label,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
    int badgeCount = 0,
  }) {
    return Expanded(
      child: Material(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: const Color(0xFFE2E8F0)),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.02),
                  blurRadius: 5,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(icon, color: color, size: 22),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    label,
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF1E293B),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (badgeCount > 0)
                  Container(
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: Color(0xFFDC3545),
                      shape: BoxShape.circle,
                    ),
                    constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
                    child: Center(
                      child: Text(
                        '$badgeCount',
                        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
