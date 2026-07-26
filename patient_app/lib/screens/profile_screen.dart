import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../services/patient_service.dart';
import '../main.dart';
import 'account_management_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final PatientService _service = PatientService();
  final _formKey = GlobalKey<FormState>();

  late TextEditingController _fullNameController;
  late TextEditingController _phoneController;
  late TextEditingController _emailController;
  late TextEditingController _addressController;
  late TextEditingController _emergencyNameController;
  late TextEditingController _emergencyPhoneController;

  bool _isSaving = false;

  bool _isValidPhone(String phone) {
    final cleanPhone = phone.trim().replaceAll(' ', '');
    final phoneRegex = RegExp(r'^(?:\+?84|0)[35789]\d{8}$');
    return phoneRegex.hasMatch(cleanPhone);
  }

  @override
  void initState() {
    super.initState();
    final profile = _service.profile;
    _fullNameController = TextEditingController(text: profile.fullName);
    _phoneController = TextEditingController(text: profile.phone);
    _emailController = TextEditingController(text: profile.email);
    _addressController = TextEditingController(text: profile.address);
    _emergencyNameController = TextEditingController(text: profile.emergencyContactName);
    _emergencyPhoneController = TextEditingController(text: profile.emergencyContactPhone);
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _phoneController.dispose();
    _emailController.dispose();
    _addressController.dispose();
    _emergencyNameController.dispose();
    _emergencyPhoneController.dispose();
    super.dispose();
  }

  Future<void> _saveProfile() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isSaving = true;
    });

    final success = await _service.updatePersonalInfo(
      fullName: _fullNameController.text.trim(),
      phone: _phoneController.text.trim(),
      email: _emailController.text.trim(),
      address: _addressController.text.trim(),
      emergencyContactName: _emergencyNameController.text.trim(),
      emergencyContactPhone: _emergencyPhoneController.text.trim(),
    );

    setState(() {
      _isSaving = false;
    });

    if (mounted && success) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Row(
            children: [
              Icon(Icons.check_circle, color: Colors.white),
              SizedBox(width: 10),
              Text('Cập nhật thông tin cá nhân thành công!'),
            ],
          ),
          backgroundColor: Colors.teal,
          duration: Duration(seconds: 3),
        ),
      );
    }
  }

  void _handleLogout() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.logout_rounded, color: Colors.red),
            SizedBox(width: 8),
            Text('Đăng xuất tài khoản'),
          ],
        ),
        content: const Text('Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng không?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
            onPressed: () {
              Navigator.pop(ctx);
              _service.logout();
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (_) => const CardioCarePatientApp()),
                (route) => false,
              );
            },
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final profile = _service.profile;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Thông tin tài khoản',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
        ),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFFDC3545),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout_rounded),
            tooltip: 'Đăng xuất',
            onPressed: _handleLogout,
          ),
        ],
      ),
      body: SingleChildScrollView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header Card
              Center(
                child: Column(
                  children: [
                    Stack(
                      children: [
                        CircleAvatar(
                          radius: 45,
                          backgroundColor: const Color(0xFFDC3545).withValues(alpha: 0.1),
                          child: const Icon(Icons.person, size: 50, color: Color(0xFFDC3545)),
                        ),
                        Positioned(
                          bottom: 0,
                          right: 0,
                          child: Container(
                            padding: const EdgeInsets.all(6),
                            decoration: const BoxDecoration(
                              color: Color(0xFFDC3545),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(Icons.camera_alt, color: Colors.white, size: 16),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Text(
                      profile.fullName,
                      style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Color(0xFF1E293B)),
                    ),
                    Text(
                      'Mã BN: #${profile.patientId} | Nhóm máu: ${profile.bloodType}',
                      style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 24),
              _buildSectionTitle('Quản lý bảo mật'),
              const SizedBox(height: 12),
              
              // Change Password Button
              SizedBox(
                width: double.infinity,
                height: 50,
                child: OutlinedButton.icon(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const AccountManagementScreen()),
                    );
                  },
                  style: OutlinedButton.styleFrom(
                    foregroundColor: const Color(0xFF1E293B),
                    side: const BorderSide(color: Color(0xFFE2E8F0)),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    backgroundColor: Colors.white,
                  ),
                  icon: const Icon(Icons.lock_outline_rounded, color: Color(0xFFF59E0B)),
                  label: const Text('Đổi mật khẩu & Bảo mật', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ),

              const SizedBox(height: 24),
              _buildSectionTitle('Thông tin liên lạc & cá nhân'),
              const SizedBox(height: 12),

              // Read-only Medical Identity: DOB & Gender
              Row(
                children: [
                  Expanded(
                    child: _buildReadOnlyField(
                      label: 'Ngày sinh (Cố định)',
                      value: DateFormat('dd/MM/yyyy').format(profile.dob),
                      icon: Icons.cake_outlined,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildReadOnlyField(
                      label: 'Giới tính (Cố định)',
                      value: profile.gender,
                      icon: Icons.wc_outlined,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // Full Name
              _buildTextField(
                controller: _fullNameController,
                label: 'Họ và tên bệnh nhân',
                icon: Icons.person_outline,
                validator: (val) {
                  if (val == null || val.trim().isEmpty) return 'Vui lòng nhập họ và tên';
                  return null;
                },
              ),
              const SizedBox(height: 14),

              // Phone Number
              _buildTextField(
                controller: _phoneController,
                label: 'Số điện thoại liên hệ',
                icon: Icons.phone_android_outlined,
                keyboardType: TextInputType.phone,
                validator: (val) {
                  if (val == null || val.trim().isEmpty) return 'Vui lòng nhập số điện thoại';
                  if (!_isValidPhone(val)) return 'SĐT không hợp lệ (VD: 0912345678)';
                  return null;
                },
              ),
              const SizedBox(height: 14),

              // Email Address
              _buildTextField(
                controller: _emailController,
                label: 'Địa chỉ Email',
                icon: Icons.email_outlined,
                keyboardType: TextInputType.emailAddress,
                validator: (val) {
                  if (val == null || val.trim().isEmpty) return 'Vui lòng nhập email';
                  if (!val.contains('@') || !val.contains('.')) return 'Email không hợp lệ';
                  return null;
                },
              ),
              const SizedBox(height: 14),

              // Home Address
              _buildTextField(
                controller: _addressController,
                label: 'Địa chỉ thường trú',
                icon: Icons.location_on_outlined,
                validator: (val) {
                  if (val == null || val.trim().isEmpty) return 'Vui lòng nhập địa chỉ';
                  return null;
                },
              ),

              const SizedBox(height: 24),
              _buildSectionTitle('Thông tin người liên hệ khẩn cấp'),
              const SizedBox(height: 12),

              // Emergency Contact Name
              _buildTextField(
                controller: _emergencyNameController,
                label: 'Tên người thân / Thân nhân',
                icon: Icons.family_restroom_outlined,
              ),
              const SizedBox(height: 14),

              // Emergency Contact Phone
              _buildTextField(
                controller: _emergencyPhoneController,
                label: 'SĐT người liên hệ khẩn cấp',
                icon: Icons.contact_phone_outlined,
                keyboardType: TextInputType.phone,
                validator: (val) {
                  if (val != null && val.trim().isNotEmpty && !_isValidPhone(val)) {
                    return 'SĐT khẩn cấp không hợp lệ (VD: 0912345678)';
                  }
                  return null;
                },
              ),

              const SizedBox(height: 30),

              // Save Button
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton.icon(
                  onPressed: _isSaving ? null : _saveProfile,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFFDC3545),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 2,
                  ),
                  icon: _isSaving
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                        )
                      : const Icon(Icons.save_rounded),
                  label: Text(
                    _isSaving ? 'Đang lưu cập nhật...' : 'Lưu thông tin cá nhân',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
              ),

              const SizedBox(height: 16),

              // Logout Button
              SizedBox(
                width: double.infinity,
                height: 48,
                child: OutlinedButton.icon(
                  onPressed: _handleLogout,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.red.shade700,
                    side: BorderSide(color: Colors.red.shade300),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  icon: const Icon(Icons.logout_rounded, size: 20),
                  label: const Text('Đăng xuất khỏi tài khoản', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ),

              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Row(
      children: [
        Container(
          width: 4,
          height: 18,
          decoration: BoxDecoration(
            color: const Color(0xFFDC3545),
            borderRadius: BorderRadius.circular(4),
          ),
        ),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.bold,
            color: Color(0xFF1E293B),
          ),
        ),
      ],
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    TextInputType? keyboardType,
    int maxLines = 1,
    String? Function(String?)? validator,
  }) {
    return TextFormField(
      controller: controller,
      keyboardType: keyboardType,
      maxLines: maxLines,
      validator: validator,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon, color: const Color(0xFFDC3545)),
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: Colors.grey.shade300),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: Colors.grey.shade300),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFFDC3545), width: 2),
        ),
      ),
    );
  }

  Widget _buildReadOnlyField({
    required String label,
    required String value,
    required IconData icon,
  }) {
    return TextFormField(
      initialValue: value,
      enabled: false,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon, color: Colors.grey.shade600),
        filled: true,
        fillColor: Colors.grey.shade100,
        disabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: Colors.grey.shade300),
        ),
      ),
      style: TextStyle(color: Colors.grey.shade800, fontWeight: FontWeight.bold),
    );
  }
}
