class SymptomReport {
  final String id;
  final List<String> symptoms;
  final int severityScore; // 1 to 10 scale
  final String duration;
  final String additionalNotes;
  final DateTime timestamp;
  final String aiRiskAssessment;
  final String aiAdvice;

  SymptomReport({
    required this.id,
    required this.symptoms,
    required this.severityScore,
    required this.duration,
    required this.additionalNotes,
    required this.timestamp,
    required this.aiRiskAssessment,
    required this.aiAdvice,
  });
}
