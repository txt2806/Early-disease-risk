import 'package:flutter_test/flutter_test.dart';
import 'package:patient_app/main.dart';

void main() {
  testWidgets('App renders patient portal title', (WidgetTester tester) async {
    await tester.pumpWidget(const CardioCarePatientApp());
    expect(find.text('Trang chủ'), findsOneWidget);
  });
}
