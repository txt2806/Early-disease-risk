import 'package:flutter_test/flutter_test.dart';
import 'package:patient_app/services/patient_service.dart';

void main() {
  group('PatientService Unit Tests', () {
    late PatientService service;

    setUp(() {
      service = PatientService();
    });

    test('PatientService Singleton instance check', () {
      final s1 = PatientService();
      final s2 = PatientService();
      expect(s1, equals(s2));
    });

    test('Initial Patient Profile has default blood type O+', () {
      expect(service.profile.bloodType, equals('O+'));
    });

    test('Unread alerts count starts at 0 or positive integer', () {
      expect(service.unreadAlertsCount, greaterThanOrEqualTo(0));
    });

    test('Submit symptom update creates local report item', () async {
      final report = await service.submitSymptomUpdate(
        selectedSymptoms: ['Đau ngực dữ dội'],
        severityScore: 9,
        duration: 'Kéo dài trên 24 giờ',
        notes: 'Cần cấp cứu khẩn cấp',
      );

      expect(report.symptoms, contains('Đau ngực dữ dội'));
      expect(report.severityScore, equals(9));
      expect(service.symptomReports, isNotEmpty);
    });
  });
}
