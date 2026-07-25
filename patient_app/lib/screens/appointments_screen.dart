import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../services/patient_service.dart';

class AppointmentsScreen extends StatefulWidget {
  const AppointmentsScreen({super.key});

  @override
  State<AppointmentsScreen> createState() => _AppointmentsScreenState();
}

class _AppointmentsScreenState extends State<AppointmentsScreen> {
  final PatientService _service = PatientService();
  bool _isLoading = true;
  List<dynamic> _appointments = [];
  List<dynamic> _doctors = [];
  bool _isLoadingDoctors = false;

  @override
  void initState() {
    super.initState();
    _fetchAppointments();
    _fetchDoctors();
  }

  Future<void> _fetchAppointments() async {
    setState(() => _isLoading = true);
    final data = await _service.fetchAppointments();
    if (mounted) {
      setState(() {
        _appointments = data;
        _isLoading = false;
      });
    }
  }

  Future<void> _fetchDoctors() async {
    setState(() => _isLoadingDoctors = true);
    final docs = await _service.fetchDoctors();
    if (mounted) {
      setState(() {
        _doctors = docs;
        _isLoadingDoctors = false;
      });
    }
  }

  Future<void> _cancelAppointment(int id) async {
    final messenger = ScaffoldMessenger.of(context);
    
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Hủy lịch khám'),
        content: const Text('Bạn có chắc chắn muốn hủy lịch khám này không?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Không')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFDC3545), foregroundColor: Colors.white),
            onPressed: () async {
              Navigator.pop(ctx);
              setState(() => _isLoading = true);
              final res = await _service.cancelAppointment(id);
              if (res['status'] == 'success') {
                messenger.showSnackBar(const SnackBar(content: Text('Đã hủy lịch khám')));
                _fetchAppointments();
              } else {
                if (mounted) setState(() => _isLoading = false);
                messenger.showSnackBar(SnackBar(content: Text(res['message'] ?? 'Lỗi khi hủy lịch')));
              }
            },
            child: const Text('Hủy lịch'),
          ),
        ],
      ),
    );
  }

  void _showBookingDialog() async {
    if (_doctors.isEmpty) {
      await _fetchDoctors();
    }

    if (!mounted) return;

    if (_doctors.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Chưa thể tải danh sách Bác sĩ từ hệ thống. Vui lòng thử lại sau!')),
      );
      return;
    }

    DateTime? selectedDate;
    TimeOfDay? selectedTime;
    int? selectedDoctorId = _doctors.isNotEmpty ? _doctors.first['doctorId'] as int? : null;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (dialogCtx, setStateSB) {
          return AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            title: const Text('Đặt lịch khám mới', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Bác sĩ', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Color(0xFFDC3545))),
                  const SizedBox(height: 4),
                  DropdownButtonFormField<int>(
                    value: selectedDoctorId,
                    isExpanded: true,
                    decoration: InputDecoration(
                      filled: true,
                      fillColor: Colors.grey.shade100,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                    ),
                    items: _doctors.map<DropdownMenuItem<int>>((d) {
                      final name = d['fullName'] ?? 'Bác sĩ';
                      final spec = d['specialty'] ?? 'Tim mạch';
                      final id = d['doctorId'] as int;
                      return DropdownMenuItem<int>(
                        value: id,
                        child: Text('$name ($spec)', overflow: TextOverflow.ellipsis),
                      );
                    }).toList(),
                    onChanged: (val) => setStateSB(() => selectedDoctorId = val),
                  ),
                  const SizedBox(height: 16),
                  const Text('Ngày khám', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Color(0xFFDC3545))),
                  const SizedBox(height: 4),
                  InkWell(
                    onTap: () async {
                      final now = DateTime.now();
                      final d = await showDatePicker(
                        context: dialogCtx,
                        initialDate: selectedDate ?? now,
                        firstDate: now,
                        lastDate: now.add(const Duration(days: 60)),
                      );
                      if (d != null) setStateSB(() => selectedDate = d);
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                      decoration: BoxDecoration(
                        color: Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            selectedDate == null ? 'Chọn ngày khám' : DateFormat('dd/MM/yyyy').format(selectedDate!),
                            style: TextStyle(color: selectedDate == null ? Colors.grey.shade600 : Colors.black, fontWeight: FontWeight.w500),
                          ),
                          const Icon(Icons.calendar_month, color: Color(0xFFDC3545), size: 20),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text('Giờ khám', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Color(0xFFDC3545))),
                  const SizedBox(height: 4),
                  InkWell(
                    onTap: () async {
                      final t = await showTimePicker(
                        context: dialogCtx,
                        initialTime: selectedTime ?? const TimeOfDay(hour: 8, minute: 0),
                      );
                      if (t != null) setStateSB(() => selectedTime = t);
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                      decoration: BoxDecoration(
                        color: Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            selectedTime == null ? 'Chọn giờ khám' : selectedTime!.format(dialogCtx),
                            style: TextStyle(color: selectedTime == null ? Colors.grey.shade600 : Colors.black, fontWeight: FontWeight.w500),
                          ),
                          const Icon(Icons.access_time, color: Color(0xFFDC3545), size: 20),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Hủy', style: TextStyle(color: Colors.grey)),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFDC3545),
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);

                  // ─── VALIDATION ───
                  if (selectedDoctorId == null) {
                    messenger.showSnackBar(const SnackBar(content: Text('Vui lòng chọn Bác sĩ khám')));
                    return;
                  }

                  if (selectedDate == null) {
                    messenger.showSnackBar(const SnackBar(content: Text('Vui lòng chọn Ngày khám')));
                    return;
                  }

                  final now = DateTime.now();
                  final todayZero = DateTime(now.year, now.month, now.day);
                  final selectedDateZero = DateTime(selectedDate!.year, selectedDate!.month, selectedDate!.day);

                  if (selectedDateZero.isBefore(todayZero)) {
                    messenger.showSnackBar(const SnackBar(content: Text('Ngày khám phải từ ngày hôm nay trở đi')));
                    return;
                  }

                  if (selectedTime == null) {
                    messenger.showSnackBar(const SnackBar(content: Text('Vui lòng chọn Giờ khám')));
                    return;
                  }

                  // If selecting today, validate that time is in the future
                  if (selectedDateZero.isAtSameMomentAs(todayZero)) {
                    final selectedMinutes = selectedTime!.hour * 60 + selectedTime!.minute;
                    final currentMinutes = now.hour * 60 + now.minute;
                    if (selectedMinutes <= currentMinutes) {
                      messenger.showSnackBar(const SnackBar(content: Text('Giờ khám hôm nay phải muộn hơn giờ hiện tại')));
                      return;
                    }
                  }

                  // Close dialog before calling API
                  Navigator.pop(ctx);
                  if (mounted) setState(() => _isLoading = true);

                  final timeStr = '${selectedTime!.hour.toString().padLeft(2, '0')}:${selectedTime!.minute.toString().padLeft(2, '0')}';
                  final dateStr = DateFormat('yyyy-MM-dd').format(selectedDate!);

                  final res = await _service.bookAppointment(selectedDoctorId!, dateStr, timeStr);

                  if (res['status'] == 'success') {
                    messenger.showSnackBar(const SnackBar(content: Text('Đặt lịch khám thành công!')));
                    _fetchAppointments();
                  } else {
                    if (mounted) setState(() => _isLoading = false);
                    messenger.showSnackBar(SnackBar(content: Text(res['message'] ?? 'Đặt lịch thất bại')));
                  }
                },
                child: const Text('Xác nhận'),
              ),
            ],
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Quản lý Lịch Khám', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFFDC3545),
        elevation: 0,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFFDC3545)))
          : _appointments.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.calendar_month_outlined, size: 64, color: Colors.grey.shade400),
                      const SizedBox(height: 16),
                      Text('Bạn chưa có lịch khám nào', style: TextStyle(color: Colors.grey.shade600, fontSize: 15)),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: _appointments.length,
                  itemBuilder: (context, index) {
                    final appt = _appointments[index];
                    final String rawStatus = (appt['status'] ?? '').toString();
                    final isCancelled = rawStatus.toUpperCase() == 'CANCELLED' || rawStatus == 'Đã hủy';
                    final isCompleted = rawStatus.toUpperCase() == 'COMPLETED' || rawStatus == 'Đã khám';

                    String dateDisplay = appt['date'] ?? '';
                    if (dateDisplay.isEmpty) {
                      final dStr = appt['appointmentDate'] ?? '';
                      final tStr = appt['appointmentTime'] ?? '';
                      dateDisplay = '$dStr $tStr'.trim();
                    }

                    String statusText = 'Sắp tới';
                    Color statusBg = Colors.blue.shade100;
                    Color statusFg = Colors.blue.shade700;

                    if (isCancelled) {
                      statusText = 'Đã Hủy';
                      statusBg = Colors.red.shade100;
                      statusFg = Colors.red.shade700;
                    } else if (isCompleted) {
                      statusText = 'Đã Khám';
                      statusBg = Colors.green.shade100;
                      statusFg = Colors.green.shade700;
                    }

                    return Card(
                      color: Colors.white,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                        side: BorderSide(color: Colors.grey.shade200),
                      ),
                      margin: const EdgeInsets.only(bottom: 12),
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Row(
                                  children: [
                                    const Icon(Icons.event, size: 18, color: Color(0xFFDC3545)),
                                    const SizedBox(width: 6),
                                    Text(
                                      dateDisplay.isNotEmpty ? dateDisplay : 'Ngày khám chưa xác định',
                                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                    ),
                                  ],
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: statusBg,
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Text(
                                    statusText,
                                    style: TextStyle(
                                      color: statusFg,
                                      fontSize: 12,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Text('Bác sĩ: ${appt['doctorName'] ?? "CardioCare Doctor"}', style: const TextStyle(fontSize: 14, color: Color(0xFF334155))),
                            if (appt['department'] != null && appt['department'].toString().isNotEmpty)
                              Padding(
                                padding: const EdgeInsets.only(top: 4),
                                child: Text('Chuyên khoa: ${appt['department']}', style: TextStyle(fontSize: 13, color: Colors.grey.shade600)),
                              ),
                            const SizedBox(height: 12),
                            if (!isCancelled && !isCompleted && appt['appointmentId'] != null)
                              Align(
                                alignment: Alignment.centerRight,
                                child: OutlinedButton(
                                  onPressed: () => _cancelAppointment(appt['appointmentId'] is int ? appt['appointmentId'] : int.parse(appt['appointmentId'].toString())),
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor: const Color(0xFFDC3545),
                                    side: const BorderSide(color: Color(0xFFDC3545)),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                                  ),
                                  child: const Text('Hủy lịch'),
                                ),
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showBookingDialog,
        backgroundColor: const Color(0xFFDC3545),
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: const Text('Đặt lịch mới'),
      ),
    );
  }
}
