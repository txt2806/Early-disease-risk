class PatientProfile {
  final int patientId;
  String fullName;
  String username;
  String phone;
  String email;
  String address;
  DateTime dob;
  String gender;
  String emergencyContactName;
  String emergencyContactPhone;
  String bloodType;

  PatientProfile({
    required this.patientId,
    required this.fullName,
    required this.username,
    required this.phone,
    required this.email,
    required this.address,
    required this.dob,
    required this.gender,
    required this.emergencyContactName,
    required this.emergencyContactPhone,
    required this.bloodType,
  });

  PatientProfile copyWith({
    String? fullName,
    String? phone,
    String? email,
    String? address,
    DateTime? dob,
    String? gender,
    String? emergencyContactName,
    String? emergencyContactPhone,
    String? bloodType,
  }) {
    return PatientProfile(
      patientId: patientId,
      fullName: fullName ?? this.fullName,
      username: username,
      phone: phone ?? this.phone,
      email: email ?? this.email,
      address: address ?? this.address,
      dob: dob ?? this.dob,
      gender: gender ?? this.gender,
      emergencyContactName: emergencyContactName ?? this.emergencyContactName,
      emergencyContactPhone: emergencyContactPhone ?? this.emergencyContactPhone,
      bloodType: bloodType ?? this.bloodType,
    );
  }
}
