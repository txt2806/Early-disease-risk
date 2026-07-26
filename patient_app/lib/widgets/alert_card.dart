import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/alert_item.dart';

class AlertCard extends StatelessWidget {
  final HealthAlertItem alert;
  final VoidCallback? onTap;

  const AlertCard({
    super.key,
    required this.alert,
    this.onTap,
  });

  Color get _borderColor {
    switch (alert.severity) {
      case AlertSeverity.critical:
        return const Color(0xFFEF4444);
      case AlertSeverity.warning:
        return const Color(0xFFF59E0B);
      case AlertSeverity.info:
        return const Color(0xFF0EA5E9);
    }
  }

  Color get _bgColor {
    switch (alert.severity) {
      case AlertSeverity.critical:
        return const Color(0xFFFEF2F2);
      case AlertSeverity.warning:
        return const Color(0xFFFFFBEB);
      case AlertSeverity.info:
        return const Color(0xFFF0F9FF);
    }
  }

  IconData get _icon {
    switch (alert.severity) {
      case AlertSeverity.critical:
        return Icons.warning_rounded;
      case AlertSeverity.warning:
        return Icons.error_outline_rounded;
      case AlertSeverity.info:
        return Icons.info_outline_rounded;
    }
  }

  String get _badgeText {
    switch (alert.severity) {
      case AlertSeverity.critical:
        return 'CẤP TÍNH';
      case AlertSeverity.warning:
        return 'CẢNH BÁO SỚM';
      case AlertSeverity.info:
        return 'THÔNG TIN';
    }
  }

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('HH:mm - dd/MM/yyyy');

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6, horizontal: 16),
      elevation: alert.isRead ? 0.5 : 2,
      color: _bgColor,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: alert.isRead ? _borderColor.withValues(alpha: 0.3) : _borderColor,
          width: alert.isRead ? 1.0 : 1.8,
        ),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: _borderColor.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(_icon, color: _borderColor, size: 22),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: _borderColor,
                                borderRadius: BorderRadius.circular(20),
                              ),
                              child: Text(
                                _badgeText,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                            const Spacer(),
                            Text(
                              dateFormat.format(alert.timestamp.toLocal()),
                              style: TextStyle(
                                fontSize: 11,
                                color: Colors.grey[600],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          alert.title,
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                            color: alert.severity == AlertSeverity.critical
                                ? const Color(0xFF991B1B)
                                : Colors.black87,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                alert.description,
                style: const TextStyle(fontSize: 13, color: Colors.black87, height: 1.3),
              ),
              const SizedBox(height: 10),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.grey.withValues(alpha: 0.2)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.psychology_outlined, color: Colors.teal.shade700, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        alert.recommendation,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                          color: Colors.grey[800],
                          height: 1.3,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
