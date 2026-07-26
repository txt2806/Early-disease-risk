enum AlertSeverity {
  critical, // Cấp tính / Khẩn cấp
  warning,  // Cảnh báo sớm
  info      // Thông tin theo dõi
}

class HealthAlertItem {
  final String id;
  final String title;
  final String description;
  final AlertSeverity severity;
  final DateTime timestamp;
  final String recommendation;
  final String relatedMetric;
  final String duration;
  final String notes;
  final int severityScore;
  bool isRead;

  HealthAlertItem({
    required this.id,
    required this.title,
    required this.description,
    required this.severity,
    required this.timestamp,
    required this.recommendation,
    required this.relatedMetric,
    this.duration = '',
    this.notes = '',
    this.severityScore = 0,
    this.isRead = false,
  });
}
