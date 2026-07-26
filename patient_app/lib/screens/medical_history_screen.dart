import 'package:flutter/material.dart';
import '../services/patient_service.dart';
import '../widgets/history_card.dart';

class MedicalHistoryScreen extends StatefulWidget {
  const MedicalHistoryScreen({super.key});

  @override
  State<MedicalHistoryScreen> createState() => _MedicalHistoryScreenState();
}

class _MedicalHistoryScreenState extends State<MedicalHistoryScreen> {
  final PatientService _service = PatientService();
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _service.addListener(_onServiceUpdate);
    _loadHistory();
  }

  @override
  void dispose() {
    _service.removeListener(_onServiceUpdate);
    super.dispose();
  }

  void _onServiceUpdate() {
    if (mounted) setState(() {});
  }

  Future<void> _loadHistory() async {
    await _service.fetchLiveHistoryFromSpringBoot(_service.profile.patientId);
  }

  @override
  Widget build(BuildContext context) {
    final historyList = _service.historyList.where((item) {
      if (_searchQuery.isEmpty) return true;
      final query = _searchQuery.toLowerCase();
      return item.diagnosis.toLowerCase().contains(query) ||
          item.doctorName.toLowerCase().contains(query) ||
          item.doctorSpecialty.toLowerCase().contains(query) ||
          item.icdCode.toLowerCase().contains(query);
    }).toList();

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Lịch sử khám bệnh & Điều trị',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFFDC3545),
        elevation: 0,
      ),
      body: Column(
        children: [
          // Search & Header summary
          Container(
            color: Colors.white,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Column(
              children: [
                TextField(
                  onChanged: (val) {
                    setState(() {
                      _searchQuery = val;
                    });
                  },
                  decoration: InputDecoration(
                    hintText: 'Tìm theo chẩn đoán, tên BS, mã ICD...',
                    prefixIcon: const Icon(Icons.search, color: Color(0xFFDC3545)),
                    filled: true,
                    fillColor: const Color(0xFFF1F5F9),
                    contentPadding: const EdgeInsets.symmetric(vertical: 10),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(30),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Tổng số lượt khám: ${historyList.length}',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: Colors.grey.shade700,
                    fontSize: 13,
                  ),
                ),
                Text(
                  'Sắp xếp: Mới nhất',
                  style: TextStyle(
                    color: Colors.teal.shade700,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),

          // Main list view
          Expanded(
            child: historyList.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.folder_off_outlined, size: 60, color: Colors.grey.shade400),
                        const SizedBox(height: 12),
                        Text(
                          'Không tìm thấy lịch sử khám phù hợp',
                          style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    physics: const BouncingScrollPhysics(),
                    itemCount: historyList.length,
                    itemBuilder: (context, index) {
                      return HistoryCard(item: historyList[index]);
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
