package com.cardio.service;

import com.cardio.model.PatientProfile;
import com.cardio.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

// ── SERVICE tầng nghiệp vụ bệnh nhân ─────────────────
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    public List<PatientProfile> getAllPatients() {
        return patientRepository.findAll();
    }

    public Page<PatientProfile> getAllPatients(Pageable pageable) {
        return patientRepository.findAll(pageable);
    }

    public Optional<PatientProfile> findById(Integer id) {
        return patientRepository.findById(id);
    }

    public Page<PatientProfile> searchByName(String name, Pageable pageable) {
        return patientRepository.findByFullNameContainingIgnoreCase(name, pageable);
    }

    public List<PatientProfile> getPatientsAssignedToDoctor(Integer doctorId) {
        return patientRepository.findPatientsAssignedToDoctor(doctorId);
    }

    public Page<PatientProfile> getPatientsAssignedToDoctor(Integer doctorId, Pageable pageable) {
        return patientRepository.findPatientsAssignedToDoctor(doctorId, pageable);
    }

    public Page<PatientProfile> searchPatientsAssignedToDoctor(Integer doctorId, String search, Pageable pageable) {
        return patientRepository.searchPatientsAssignedToDoctor(doctorId, search, pageable);
    }

    public Page<PatientProfile> searchAssignedPatients(Integer doctorId, String search, java.time.LocalDate date, Pageable pageable) {
        java.time.LocalDateTime startDateTime = null;
        java.time.LocalDateTime endDateTime = null;
        if (date != null) {
            startDateTime = date.atStartOfDay();
            endDateTime = date.atTime(java.time.LocalTime.MAX);
        }
        return patientRepository.searchAssignedPatients(doctorId, search, date, startDateTime, endDateTime, pageable);
    }

    public boolean isPatientAssignedToDoctor(Integer patientId, Integer doctorId) {
        return patientRepository.isPatientAssignedToDoctor(patientId, doctorId);
    }
    
    public PatientProfile save(PatientProfile patient) {
        // Hash password mặc định nếu chưa có
        if (patient.getPasswordHash() == null || patient.getPasswordHash().isEmpty()) {
            patient.setPasswordHash(passwordEncoder.encode("changeme123"));
        }
        return patientRepository.save(patient);
    }
}
