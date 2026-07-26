class MedicalHistoryItem {
  final String id;
  final DateTime visitDate;
  final String doctorName;
  final String doctorSpecialty;
  final String diagnosis;
  final String icdCode;
  final String treatmentPlan;
  final List<String> medications;
  final String labResults;
  final String doctorNotes;
  final String clinicRoom;

  // New Clinical & AI Fields
  final double? riskScore;
  final String? riskLevel;
  final String? riskExplanation;
  final String? healthAdvice;
  final String? dietaryAdvice;

  final String? chestPainType;
  final String? restingBP;
  final double? cholesterol;
  final double? fastingBloodSugar;
  final String? restingECG;
  final int? maxHeartRate;
  final String? exerciseAngina;
  
  final double? spO2;
  final String? bloodTest;
  final String? urineTest;
  final String? xray;
  final String? ultrasound;
  final String? mri;
  final String? ct;

  MedicalHistoryItem({
    required this.id,
    required this.visitDate,
    required this.doctorName,
    required this.doctorSpecialty,
    required this.diagnosis,
    required this.icdCode,
    required this.treatmentPlan,
    required this.medications,
    required this.labResults,
    required this.doctorNotes,
    required this.clinicRoom,
    this.riskScore,
    this.riskLevel,
    this.riskExplanation,
    this.healthAdvice,
    this.dietaryAdvice,
    this.chestPainType,
    this.restingBP,
    this.cholesterol,
    this.fastingBloodSugar,
    this.restingECG,
    this.maxHeartRate,
    this.exerciseAngina,
    this.spO2,
    this.bloodTest,
    this.urineTest,
    this.xray,
    this.ultrasound,
    this.mri,
    this.ct,
  });
}
