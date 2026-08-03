import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/patient.dart';
import '../models/medical_history.dart';
import '../models/alert_item.dart';
import '../models/symptom_report.dart';

class PatientService extends ChangeNotifier {
  static final PatientService _instance = PatientService._internal();
  factory PatientService() => _instance;
  PatientService._internal();

  // 💡 HƯỚNG DẪN CẤU HÌNH API URL:
  // - Code dưới đây ĐÃ TỰ ĐỘNG CHUYỂN ĐỔI: Chrome dùng localhost, Android Giả lập dùng 10.0.2.2.
  // - Bạn KHÔNG CẦN xóa dấu // hay sửa gì cả khi chuyển giữa Chrome và Android Emulator!
  // - Nếu test trên ĐIỆN THOẠI THẬT: Bỏ comment dòng Ngrok bên dưới.
  static String get baseUrl {
    // return 'https://your-ngrok-url.ngrok-free.app/api'; // (Chỉ mở nếu test Điện thoại thật)

    if (kIsWeb) {
      return 'http://localhost:8080/api'; // Tự động dùng cho Chrome Web
    }
    return 'http://10.0.2.2:8080/api';   // Tự động dùng cho Android Emulator
  }

  static String get authUrl => '$baseUrl/auth';
  static String get patientUrl => '$baseUrl/patient';

  bool _isLoggedIn = false;
  late PatientProfile _profile = PatientProfile(
    patientId: 0,
    fullName: '',
    username: '',
    phone: '',
    email: '',
    address: '',
    dob: DateTime.now(),
    gender: 'Nam',
    emergencyContactName: '',
    emergencyContactPhone: '',
    bloodType: 'O+',
  );

  final List<MedicalHistoryItem> _historyList = [];
  final List<HealthAlertItem> _alertsList = [];
  final List<SymptomReport> _symptomReports = [];
  final bool _isLoading = false;

  bool get isLoggedIn => _isLoggedIn;
  PatientProfile get profile => _profile;
  List<MedicalHistoryItem> get historyList => List.unmodifiable(_historyList);
  List<HealthAlertItem> get alertsList => List.unmodifiable(_alertsList);
  List<SymptomReport> get symptomReports => List.unmodifiable(_symptomReports);
  bool get isLoading => _isLoading;

  int get unreadAlertsCount => _alertsList.where((a) => !a.isRead).length;

  // Authenticate user with Supabase DB via Spring Boot API
  Future<Map<String, dynamic>> login({required String username, required String password}) async {
    try {
      final response = await http.post(
        Uri.parse('$authUrl/login'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'username': username,
          'password': password,
        }),
      ).timeout(const Duration(seconds: 5));

      if (response.statusCode == 200) {
        final jsonRes = jsonDecode(response.body);
        if (jsonRes['status'] == 'success') {
          bool isFirstTime = jsonRes['isFirstLogin'] == true;
          int patientId = jsonRes['patientId'] ?? 0;

          // Clear previous data and fetch real data from Supabase DB
          _historyList.clear();
          _alertsList.clear();
          _symptomReports.clear();

          await fetchLiveProfileFromSpringBoot(patientId);
          await fetchLiveHistoryFromSpringBoot(patientId);
          await fetchLiveAlertsFromSpringBoot(patientId);

          if (!isFirstTime) {
            _isLoggedIn = true;
            notifyListeners();
          }

          return {
            'success': true,
            'message': jsonRes['message'] ?? 'Đăng nhập hợp lệ',
            'fullName': _profile.fullName.isNotEmpty ? _profile.fullName : jsonRes['fullName'],
            'patientId': patientId,
            'username': username,
            'isFirstLogin': isFirstTime,
          };
        } else {
          return {
            'success': false,
            'message': jsonRes['message'] ?? 'Tài khoản hoặc mật khẩu không đúng!',
          };
        }
      } else {
        final jsonRes = jsonDecode(response.body);
        return {
          'success': false,
          'message': jsonRes['message'] ?? 'Đăng nhập thất bại (Mã lỗi ${response.statusCode})',
        };
      }
    } catch (e) {
      if (kDebugMode) print('SpringBoot API connection error: $e');

      return {
        'success': false,
        'message': 'Không thể kết nối đến máy chủ Spring Boot (http://localhost:8080). Vui lòng kiểm tra server đang chạy!',
      };
    }
  }

  void logout() {
    _isLoggedIn = false;
    _historyList.clear();
    _alertsList.clear();
    _symptomReports.clear();
    notifyListeners();
  }

  // Fetch Live Profile from Supabase DB via Spring Boot REST API
  Future<void> fetchLiveProfileFromSpringBoot(int patientId) async {
    try {
      final response = await http.get(Uri.parse('$patientUrl/profile/$patientId')).timeout(const Duration(seconds: 4));
      if (response.statusCode == 200) {
        final jsonMap = jsonDecode(response.body);
        if (jsonMap['status'] == 'success' && jsonMap['data'] != null) {
          final data = jsonMap['data'];
          _profile = PatientProfile(
            patientId: data['patientId'] ?? patientId,
            fullName: data['fullName'] ?? '',
            username: data['username'] ?? '',
            phone: data['phone'] ?? '',
            email: data['email'] ?? '',
            address: data['address'] ?? '',
            dob: DateTime.tryParse(data['dob'] ?? '') ?? DateTime.now(),
            gender: data['gender'] ?? 'Nam',
            emergencyContactName: data['emergencyContactName'] ?? '',
            emergencyContactPhone: data['emergencyContactPhone'] ?? '',
            bloodType: data['bloodType'] ?? 'O+',
          );
          notifyListeners();
        }
      }
    } catch (e) {
      if (kDebugMode) print('SpringBoot API profile fetch error: $e');
    }
  }

  // Fetch Live Medical History from Supabase DB via Spring Boot REST API
  Future<void> fetchLiveHistoryFromSpringBoot(int patientId) async {
    try {
      final targetId = patientId > 0 ? patientId : 17;
      final response = await http.get(Uri.parse('$patientUrl/history/$targetId')).timeout(const Duration(seconds: 5));
      if (response.statusCode == 200) {
        final jsonMap = jsonDecode(response.body);
        if (jsonMap['status'] == 'success' && jsonMap['records'] != null) {
          final List list = jsonMap['records'];
          _historyList.clear();
          for (var r in list) {
            String getVal(String key) {
              if (r[key] != null && r[key].toString().isNotEmpty) return r[key].toString();
              final lower = key.toLowerCase();
              if (r[lower] != null && r[lower].toString().isNotEmpty) return r[lower].toString();
              return '';
            }

            final recordId = getVal('RecordID');
            final visitDateRaw = getVal('VisitDate');
            final docName = getVal('DoctorName');
            final docSpec = getVal('DoctorSpecialty');
            final notes = getVal('ConsultationNotes');
            final plan = getVal('TreatmentPlan');
            final riskLevel = getVal('RiskLevel');
            final riskScore = getVal('RiskScore');
            final riskExp = getVal('RiskExplanation');
            final advice = getVal('HealthAdvice');
            final dietary = getVal('DietaryAdvice');
            final bp = getVal('RestingBP');
            final maxHr = getVal('MaxHeartRate');
            final chol = getVal('Cholesterol');
            final icdCode = getVal('ICDCode');
            final diseaseName = getVal('DiseaseName');
            final fbs = getVal('FastingBloodSugar');
            final ecg = getVal('RestingECG');
            final angina = getVal('ExerciseAngina');
            final spO2Raw = getVal('SpO2');
            final blood = getVal('BloodTest');
            final urine = getVal('UrineTest');
            final xrayRes = getVal('Xray');
            final ultra = getVal('Ultrasound');
            final mriRes = getVal('Mri');
            final ctRes = getVal('Ct');

            final doc = docName.isNotEmpty ? docName : 'Bác sĩ khám bệnh';
            final spec = docSpec.isNotEmpty ? docSpec : 'Chuyên khoa Tim mạch';
            final diag = notes.isNotEmpty ? notes : (diseaseName.isNotEmpty ? diseaseName : 'Khám sức khỏe tim mạch định kỳ');
            final icd = icdCode.isNotEmpty ? '$icdCode - $diseaseName' : 'Z00.0 - Tầm soát sức khỏe';
            final planText = plan.isNotEmpty ? plan : 'Theo dõi chỉ số sinh tồn & tái khám định kỳ';
            
            String notesText = '';
            if (riskLevel.isNotEmpty) {
              notesText += '📌 Mức nguy cơ AI: $riskLevel (${riskScore.isNotEmpty ? "$riskScore%" : ""})\n';
            }
            if (riskExp.isNotEmpty) notesText += 'Lý giải AI: $riskExp\n';
            if (advice.isNotEmpty) notesText += 'Lời khuyên Bác sĩ: $advice\n';
            if (dietary.isNotEmpty) notesText += 'Dinh dưỡng: $dietary';
            if (notesText.isEmpty) notesText = 'Theo dõi chỉ số sức khỏe tại nhà.';

            String labText = 'Huyết áp: ${bp.isNotEmpty ? "$bp mmHg" : "--"}, Nhịp tim max: ${maxHr.isNotEmpty ? "$maxHr bpm" : "--"}';
            if (chol.isNotEmpty) labText += ', Cholesterol: $chol mg/dL';

            _historyList.add(
              MedicalHistoryItem(
                id: 'MED-${recordId.isNotEmpty ? recordId : _historyList.length + 1}',
                visitDate: DateTime.tryParse(visitDateRaw) ?? DateTime.now(),
                doctorName: doc,
                doctorSpecialty: spec,
                diagnosis: diag,
                icdCode: icd,
                treatmentPlan: planText,
                medications: [planText],
                labResults: labText,
                doctorNotes: notesText.trim(),
                clinicRoom: 'Phòng khám Chuyên khoa Tim mạch',
                riskScore: double.tryParse(riskScore),
                riskLevel: riskLevel,
                riskExplanation: riskExp,
                healthAdvice: advice,
                dietaryAdvice: dietary,
                chestPainType: getVal('ChestPainType'),
                restingBP: bp,
                cholesterol: double.tryParse(chol),
                fastingBloodSugar: double.tryParse(fbs),
                restingECG: ecg,
                maxHeartRate: int.tryParse(maxHr),
                exerciseAngina: angina,
                spO2: double.tryParse(spO2Raw),
                bloodTest: blood,
                urineTest: urine,
                xray: xrayRes,
                ultrasound: ultra,
                mri: mriRes,
                ct: ctRes,
              ),
            );
          }
          notifyListeners();
        }
      }
    } catch (e) {
      if (kDebugMode) print('SpringBoot API history query error: $e');
    }
  }

  // Fetch Live Alerts from Supabase DB via Spring Boot REST API
  Future<void> fetchLiveAlertsFromSpringBoot(int patientId) async {
    try {
      final response = await http.get(Uri.parse('$patientUrl/alerts/$patientId')).timeout(const Duration(seconds: 4));
      if (response.statusCode == 200) {
        final jsonMap = jsonDecode(response.body);
        if (jsonMap['status'] == 'success' && jsonMap['alerts'] != null) {
          final List list = jsonMap['alerts'];
          _alertsList.clear();
          for (var item in list) {
            final dynamic rawAlert = item['TriggeredAlert'] ?? item['triggeredalert'] ?? item['Triggeredalert'];
            final bool isAlert = BooleanUtils.toBoolean(rawAlert);
            final int hr = item['CurrentHeartRate'] ?? item['currentheartrate'] ?? 0;
            final String symptoms = (item['Symptoms'] ?? item['symptoms'] ?? '').toString().trim();
            final String aiAdvice = (item['AIAdvice'] ?? item['aiadvice'] ?? '').toString().trim();
            final String aiRisk = (item['AIRiskAssessment'] ?? item['airiskassessment'] ?? '').toString().trim();
            final dynamic rawLogId = item['LogID'] ?? item['logid'];
            final dynamic rawLogDate = item['LogDate'] ?? item['logdate'];

            // Skip empty/meaningless records (e.g. 0 BPM, no symptoms, no AI analysis, not an alert)
            final bool hasMeaningfulData = isAlert || hr > 0 || symptoms.isNotEmpty || aiRisk.isNotEmpty || aiAdvice.isNotEmpty;
            if (!hasMeaningfulData) {
              continue;
            }

            String title = 'Thông tin sức khỏe';
            AlertSeverity severity = AlertSeverity.info;
            
            if (isAlert || hr > 100 || symptoms.contains('Đau ngực') || symptoms.contains('Khó thở')) {
                title = aiRisk.isNotEmpty ? aiRisk : 'CẢNH BÁO CẤP TÍNH: Dấu hiệu bất thường';
                severity = AlertSeverity.critical;
            } else if (hr > 85 || symptoms.isNotEmpty || aiRisk.isNotEmpty) {
                title = aiRisk.isNotEmpty ? aiRisk : 'Cảnh báo chỉ số sức khỏe';
                severity = AlertSeverity.warning;
            }

            String recommendationText = aiAdvice;
            if (recommendationText.isEmpty) {
              recommendationText = _generateAiAdviceForAlert(symptoms, hr, isAlert);
            }

            final String durationStr = (item['Duration'] ?? item['duration'] ?? '').toString().trim();
            final String notesStr = (item['Notes'] ?? item['notes'] ?? '').toString().trim();
            final int scoreVal = item['SeverityScore'] ?? item['severityscore'] ?? 0;

            _alertsList.add(
              HealthAlertItem(
                id: 'ALT-${rawLogId ?? DateTime.now().millisecondsSinceEpoch}',
                title: title,
                description: 'Triệu chứng: ${symptoms.isNotEmpty ? symptoms : "Không có"} • Nhịp tim: ${hr > 0 ? "$hr BPM" : "Theo dõi chung"}',
                severity: severity,
                timestamp: DateTime.tryParse(rawLogDate?.toString() ?? '') ?? DateTime.now(),
                recommendation: recommendationText,
                relatedMetric: hr > 0 ? 'Nhịp tim: $hr BPM' : 'Theo dõi chỉ số',
                duration: durationStr,
                notes: notesStr,
                severityScore: scoreVal,
              ),
            );
          }
          notifyListeners();
        }
      }
    } catch (e) {
      if (kDebugMode) print('SpringBoot API alerts query error: $e');
    }
  }

  String _generateAiAdviceForAlert(String symptoms, int hr, bool isAlert) {
    final lower = symptoms.toLowerCase();
    String advice = '';

    if (isAlert || lower.contains('đau ngực') || lower.contains('khó thở')) {
      advice += '• Hỗ trợ y tế khẩn cấp: Ngừng ngay mọi vận động, nghỉ ngơi ở tư thế nửa nằm nửa ngồi (đầu cao), nới lỏng trang phục.\n'
          '• An toàn: Nhờ người thân hỗ trợ hoặc bấm nút "GỌI 115" để kết nối y tế khẩn cấp.\n'
          '• Theo dõi: Giữ tinh thần bình tĩnh, hít thở nhẹ nhàng và không tự điều khiển phương tiện.';
    } else if (lower.contains('chóng mặt') || lower.contains('xây xẩm') || lower.contains('buồn nôn') || hr > 100) {
      advice += '• Nghỉ ngơi tại chỗ: Chọn nơi thoáng mát, nằm thả lỏng cơ thể và nhấp từng ngụm nước ấm nhỏ.\n'
          '• Theo dõi chỉ số: Kiểm tra lại nhịp tim và đo huyết áp sau 15-20 phút nghỉ ngơi.\n'
          '• Tham khảo bác sĩ: Nếu tình trạng kéo dài trên 30 phút, nên liên hệ bác sĩ tư vấn.';
    } else {
      advice += '• Thư giãn tinh thần: Tập hít thở sâu, duy trì uống đủ nước và ăn uống thanh nhẹ.\n'
          '• Theo dõi sức khỏe: Ghi nhận lại bất kỳ biểu hiện bất thường nào để thông báo cho bác sĩ.';
    }
    return advice;
  }

  // Update Personal Info (Push to Supabase DB via Spring Boot REST API)
  Future<bool> updatePersonalInfo({
    required String fullName,
    required String phone,
    required String email,
    required String address,
    required String emergencyContactName,
    required String emergencyContactPhone,
  }) async {
    try {
      final response = await http.put(
        Uri.parse('$patientUrl/profile/${_profile.patientId}'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'fullName': fullName,
          'phone': phone,
          'address': address,
        }),
      ).timeout(const Duration(seconds: 4));

      if (response.statusCode == 200) {
        _profile = _profile.copyWith(
          fullName: fullName,
          phone: phone,
          email: email,
          address: address,
          emergencyContactName: emergencyContactName,
          emergencyContactPhone: emergencyContactPhone,
        );
        notifyListeners();
        return true;
      }
    } catch (e) {
      if (kDebugMode) print('SpringBoot API update profile error: $e');
    }

    return false;
  }

  // Submit Symptom Update (Push to Supabase DB via Spring Boot REST API)
  Future<SymptomReport> submitSymptomUpdate({
    required List<String> selectedSymptoms,
    required int severityScore,
    int heartRate = 80,
    required String duration,
    required String notes,
  }) async {
    String riskAssessment = 'Nguy cơ Thấp: Tiếp tục theo dõi sức khỏe.';
    bool triggerAcuteAlert = false;
    final hasSevereChestPain = selectedSymptoms.any((s) => s.contains('Đau ngực'));
    final hasShortnessOfBreath = selectedSymptoms.any((s) => s.contains('Khó thở'));
    final hasRadiatingPain = selectedSymptoms.any((s) => s.contains('Đau lan'));
    final hasDizziness = selectedSymptoms.any((s) => s.contains('Chóng mặt') || s.contains('Ngất'));

    // Tham khảo tài liệu Thang điểm VAS: https://medlatec.vn/tin-tuc/thang-diem-vas-danh-gia-dau-nhung-dieu-nen-biet
    if (severityScore >= 7 || hasSevereChestPain || hasShortnessOfBreath || hasRadiatingPain) {
      riskAssessment = 'NGUY CƠ CẤP TÍNH CAO: Cần can thiệp y tế ngay lập tức!';
      triggerAcuteAlert = true;
    }

    String aiAdvice = '';
    final lowerNotes = notes.toLowerCase();
    if (lowerNotes.contains('tự sát') || lowerNotes.contains('tự tử') || lowerNotes.contains('muốn chết') || lowerNotes.contains('khủng hoảng')) {
      aiAdvice += '🆘 HỖ TRỢ KHẨN CẤP: Nếu bạn đang cảm thấy bế tắc hoặc gặp khủng hoảng tinh thần, hãy chia sẻ ngay với người thân hoặc liên hệ Tổng đài tư vấn / Gọi 115 để được hỗ trợ kịp thời!\n\n';
    }

    if (severityScore >= 7 || hasSevereChestPain || hasShortnessOfBreath || hasRadiatingPain) {
      aiAdvice += '• Giữ tĩnh lặng: Dừng mọi vận động, ngồi hoặc nằm tư thế thoải mái, nới lỏng quần áo.\n'
          '• Hỗ trợ y tế: Nhờ người thân đưa đến cơ sở y tế gần nhất hoặc bấm nút "GỌI BÁC SĨ TƯ VẤN".\n'
          '• An toàn: Không tự lái xe hay làm việc nặng khi có dấu hiệu đau ngực/khó thở.';
    } else if (severityScore >= 4 || hasDizziness || selectedSymptoms.any((s) => s.contains('Hồi hộp'))) {
      aiAdvice += '• Nghỉ ngơi tại chỗ: Nghỉ ngơi ở nơi thoáng mát, uống ít nước ấm từ từ.\n'
          '• Theo dõi chỉ số: Kiểm tra lại nhịp tim và huyết áp sau 15 phút nghỉ ngơi.\n'
          '• Tham khảo bác sĩ: Nếu triệu chứng kéo dài trên 30 phút, nên liên hệ phòng khám.';
    } else {
      aiAdvice += '• Thư giãn tinh thần: Thả lỏng cơ thể, tập hít thở sâu 5 - 10 phút.\n'
          '• Lối sống lành mạnh: Uống đủ nước, ăn nhạt, tránh thức khuya và chất kích thích.\n'
          '• Tiếp tục theo dõi: Cập nhật lại triệu chứng nếu xuất hiện biểu hiện lạ.';
    }

    try {
      final res = await http.post(
        Uri.parse('$patientUrl/symptom-update'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'patientId': _profile.patientId,
          'symptoms': selectedSymptoms,
          'heartRate': heartRate,
          'severityScore': severityScore,
          'duration': duration,
          'notes': notes,
        }),
      ).timeout(const Duration(seconds: 15));

      if (res.statusCode == 200) {
        final jsonRes = jsonDecode(res.body);
        if (jsonRes['aiRiskAssessment'] != null && jsonRes['aiRiskAssessment'].toString().isNotEmpty) {
          riskAssessment = jsonRes['aiRiskAssessment'];
        }
        if (jsonRes['aiAdvice'] != null && jsonRes['aiAdvice'].toString().isNotEmpty) {
          aiAdvice = jsonRes['aiAdvice'];
        }
      }
    } catch (e) {
      if (kDebugMode) print('SpringBoot API symptom update error: $e');
    }

    final report = SymptomReport(
      id: 'SYM-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}',
      symptoms: selectedSymptoms,
      severityScore: severityScore,
      duration: duration,
      additionalNotes: notes,
      timestamp: DateTime.now(),
      aiRiskAssessment: riskAssessment,
      aiAdvice: aiAdvice,
    );

    _symptomReports.insert(0, report);

    if (triggerAcuteAlert) {
      _alertsList.insert(
        0,
        HealthAlertItem(
          id: 'ALT-${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}',
          title: riskAssessment.isNotEmpty ? riskAssessment : 'CẢNH BÁO CẤP TÍNH VỪA GHI NHẬN',
          description: 'Triệu chứng: ${selectedSymptoms.join(", ")} (Mức độ: $severityScore/10).',
          severity: AlertSeverity.critical,
          timestamp: DateTime.now(),
          recommendation: aiAdvice.isNotEmpty ? aiAdvice : 'Đến ngay cơ sở y tế gần nhất hoặc gọi số khẩn cấp 115!',
          relatedMetric: 'Triệu chứng: ${selectedSymptoms.join(", ")}',
          duration: duration,
          notes: notes,
          severityScore: severityScore,
        ),
      );
    }

    // Refresh live alerts from Supabase DB to get the saved AI analysis
    await fetchLiveAlertsFromSpringBoot(_profile.patientId);

    notifyListeners();
    return report;
  }

  void markAlertAsRead(String alertId) {
    final index = _alertsList.indexWhere((a) => a.id == alertId);
    if (index != -1) {
      _alertsList[index].isRead = true;
      notifyListeners();
    }
  }

  void markAllAlertsAsRead() {
    for (var a in _alertsList) {
      a.isRead = true;
    }
    notifyListeners();
  }

  // --- NEW API INTEGRATIONS ---

  Future<Map<String, dynamic>> changePassword(String username, String oldPassword, String newPassword) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/auth/change-password'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'username': username,
          'oldPassword': oldPassword,
          'newPassword': newPassword,
        }),
      );
      final res = jsonDecode(utf8.decode(response.bodyBytes));
      if (res['status'] == 'success') {
        _isLoggedIn = true;
        notifyListeners();
      }
      return res;
    } catch (e) {
      return {'status': 'error', 'message': 'Lỗi kết nối mạng: $e'};
    }
  }

  Future<List<dynamic>> fetchDoctors() async {
    try {
      final response = await http.get(
        Uri.parse('$baseUrl/appointments/doctors'),
      ).timeout(const Duration(seconds: 5));
      if (response.statusCode == 200) {
        final data = jsonDecode(utf8.decode(response.bodyBytes));
        if (data['status'] == 'success') {
          return data['data'];
        }
      }
    } catch (e) {
      if (kDebugMode) print('Fetch doctors error: $e');
    }
    return [];
  }

  Future<Map<String, dynamic>> bookAppointment(int doctorId, String date, String time) async {
    try {
      final pId = _profile.patientId > 0 ? _profile.patientId : 1001;
      final response = await http.post(
        Uri.parse('$baseUrl/appointments/book'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'patientId': pId,
          'doctorId': doctorId,
          'date': date,
          'time': time,
        }),
      );
      return jsonDecode(utf8.decode(response.bodyBytes));
    } catch (e) {
      return {'status': 'error', 'message': 'Lỗi kết nối: $e'};
    }
  }

  Future<List<dynamic>> fetchAppointments() async {
    try {
      final pId = _profile.patientId > 0 ? _profile.patientId : 1001;
      final response = await http.get(
        Uri.parse('$baseUrl/appointments/patient?patientId=$pId'),
      );
      if (response.statusCode == 200) {
        final data = jsonDecode(utf8.decode(response.bodyBytes));
        if (data['status'] == 'success') {
          return data['data'];
        }
      }
    } catch (e) {
      if (kDebugMode) print('Fetch appointments error: $e');
    }
    return [];
  }

  Future<Map<String, dynamic>> cancelAppointment(int appointmentId) async {
    try {
      final response = await http.put(
        Uri.parse('$baseUrl/appointments/$appointmentId/cancel'),
      );
      return jsonDecode(utf8.decode(response.bodyBytes));
    } catch (e) {
      return {'status': 'error', 'message': 'Lỗi kết nối: $e'};
    }
  }

  Future<String> sendMessageToChatbot(String message) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/patient/chat'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'message': message, 'patientId': _profile.patientId}),
      );
      if (response.statusCode == 200) {
        final data = jsonDecode(utf8.decode(response.bodyBytes));
        return data['reply'] ?? 'Không nhận được phản hồi.';
      } else {
        return 'Lỗi máy chủ (HTTP ${response.statusCode})';
      }
    } catch (e) {
      return 'Không thể kết nối với Trợ lý AI. Vui lòng thử lại sau.';
    }
  }
}

class BooleanUtils {
  static bool toBoolean(dynamic value) {
    if (value == null) return false;
    if (value is bool) return value;
    if (value is num) return value != 0;
    if (value is String) return value.toLowerCase() == 'true' || value == '1';
    return false;
  }
}
