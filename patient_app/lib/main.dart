import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'screens/login_screen.dart';
import 'screens/home_screen.dart';
import 'screens/medical_history_screen.dart';
import 'screens/alerts_screen.dart';
import 'screens/symptom_update_screen.dart';
import 'screens/profile_screen.dart';
import 'services/patient_service.dart';

void main() {
  runApp(const CardioCarePatientApp());
}

class CardioCarePatientApp extends StatelessWidget {
  const CardioCarePatientApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CardioCare Patient Portal',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFFDC3545), // Red theme
          primary: const Color(0xFFDC3545),
          secondary: const Color(0xFF6C757D),
          surface: const Color(0xFFF8FAFC),
        ),
        textTheme: GoogleFonts.outfitTextTheme(
          Theme.of(context).textTheme,
        ),
        appBarTheme: const AppBarTheme(
          elevation: 0,
          centerTitle: false,
          backgroundColor: Colors.white,
          foregroundColor: Color(0xFFDC3545),
        ),
      ),
      home: const AuthenticationWrapper(),
    );
  }
}

class AuthenticationWrapper extends StatefulWidget {
  const AuthenticationWrapper({super.key});

  @override
  State<AuthenticationWrapper> createState() => _AuthenticationWrapperState();
}

class _AuthenticationWrapperState extends State<AuthenticationWrapper> {
  final PatientService _service = PatientService();

  @override
  void initState() {
    super.initState();
    _service.addListener(_onAuthChange);
  }

  @override
  void dispose() {
    _service.removeListener(_onAuthChange);
    super.dispose();
  }

  void _onAuthChange() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    if (!_service.isLoggedIn) {
      return const LoginScreen();
    }
    return const MainNavigationShell();
  }
}

class MainNavigationShell extends StatefulWidget {
  const MainNavigationShell({super.key});

  @override
  State<MainNavigationShell> createState() => _MainNavigationShellState();
}

class _MainNavigationShellState extends State<MainNavigationShell> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    HomeScreen(),
    MedicalHistoryScreen(),
    AlertsScreen(),
    SymptomUpdateScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.08),
              blurRadius: 10,
              offset: const Offset(0, -2),
            ),
          ],
        ),
        child: BottomNavigationBar(
          currentIndex: _currentIndex,
          onTap: (index) {
            setState(() {
              _currentIndex = index;
            });
          },
          type: BottomNavigationBarType.fixed,
          backgroundColor: Colors.white,
          selectedItemColor: const Color(0xFFDC3545),
          unselectedItemColor: Colors.grey.shade500,
          selectedFontSize: 11,
          unselectedFontSize: 11,
          selectedLabelStyle: const TextStyle(fontWeight: FontWeight.bold),
          items: const [
            BottomNavigationBarItem(
              icon: Icon(Icons.home_outlined),
              activeIcon: Icon(Icons.home_rounded),
              label: 'Trang chủ',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.history_edu_outlined),
              activeIcon: Icon(Icons.history_edu_rounded),
              label: 'Lịch sử',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.notifications_active_outlined),
              activeIcon: Icon(Icons.notifications_active_rounded),
              label: 'Cảnh báo',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.medical_information_outlined),
              activeIcon: Icon(Icons.medical_information_rounded),
              label: 'Triệu chứng',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.person_outline),
              activeIcon: Icon(Icons.person_rounded),
              label: 'Cá nhân',
            ),
          ],
        ),
      ),
    );
  }
}
