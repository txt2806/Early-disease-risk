import 'package:flutter_test/flutter_test.dart';
import 'package:patient_app/main.dart';

void main() {
  testWidgets('App renders patient login screen title', (WidgetTester tester) async {
    await tester.pumpWidget(const CardioCarePatientApp());
    expect(find.text('CardioCare Patient'), findsOneWidget);
  });
}
