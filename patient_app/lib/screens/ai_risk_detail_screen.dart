import 'package:flutter/material.dart';
import '../models/medical_history.dart';

class AIRiskDetailScreen extends StatelessWidget {
  final MedicalHistoryItem item;

  const AIRiskDetailScreen({super.key, required this.item});

  @override
  Widget build(BuildContext context) {
    final double score = item.riskScore ?? 0.0;
    final String level = item.riskLevel ?? 'LOW';
    
    Color accentColor = const Color(0xFF10B981);
    if (level.toUpperCase().contains('HIGH')) {
      accentColor = const Color(0xFFDC3545);
    } else if (level.toUpperCase().contains('MEDIUM')) {
      accentColor = const Color(0xFFF59E0B);
    }

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Chi tiết Cảnh báo AI', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFFF59E0B),
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // Score Circle
            Container(
              padding: const EdgeInsets.symmetric(vertical: 32),
              width: double.infinity,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFE2E8F0)),
                boxShadow: [
                  BoxShadow(color: Colors.black.withValues(alpha: 0.02), blurRadius: 10, offset: const Offset(0, 4)),
                ],
              ),
              child: Column(
                children: [
                  const Text('ĐIỂM RỦI RO BỆNH TIM', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF64748B))),
                  const SizedBox(height: 16),
                  Stack(
                    alignment: Alignment.center,
                    children: [
                      SizedBox(
                        width: 150,
                        height: 150,
                        child: CircularProgressIndicator(
                          value: score / 100,
                          strokeWidth: 12,
                          backgroundColor: const Color(0xFFF1F5F9),
                          color: accentColor,
                        ),
                      ),
                      Column(
                        children: [
                          Text('$score%', style: TextStyle(fontSize: 32, fontWeight: FontWeight.w900, color: accentColor)),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            margin: const EdgeInsets.only(top: 4),
                            decoration: BoxDecoration(
                              color: accentColor.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Text(level, style: TextStyle(color: accentColor, fontSize: 12, fontWeight: FontWeight.bold)),
                          ),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ),
            
            const SizedBox(height: 24),
            
            // AI Explanation
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.psychology, color: Color(0xFFF59E0B)),
                      SizedBox(width: 8),
                      Text('Giải thích từ AI', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF1E293B))),
                    ],
                  ),
                  const Divider(height: 24),
                  Text(item.riskExplanation?.isNotEmpty == true ? item.riskExplanation! : 'Chưa có giải thích chi tiết từ mô hình AI.', style: const TextStyle(fontSize: 14, color: Color(0xFF334155), height: 1.5)),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // Doctor Advice
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.medical_information, color: Color(0xFF3B82F6)),
                      SizedBox(width: 8),
                      Text('Lời khuyên y tế', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF1E293B))),
                    ],
                  ),
                  const Divider(height: 24),
                  Text(item.healthAdvice?.isNotEmpty == true ? item.healthAdvice! : 'N/A', style: const TextStyle(fontSize: 14, color: Color(0xFF334155), height: 1.5)),
                  const SizedBox(height: 16),
                  const Text('Chế độ dinh dưỡng:', style: TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF1E293B))),
                  const SizedBox(height: 4),
                  Text(item.dietaryAdvice?.isNotEmpty == true ? item.dietaryAdvice! : 'N/A', style: const TextStyle(fontSize: 14, color: Color(0xFF334155), height: 1.5)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
