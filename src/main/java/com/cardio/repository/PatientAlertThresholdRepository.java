package com.cardio.repository;

import com.cardio.model.PatientAlertThreshold;
import com.cardio.model.PatientProfile;
import com.cardio.model.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PatientAlertThresholdRepository
        extends JpaRepository<PatientAlertThreshold, Integer> {

    Optional<PatientAlertThreshold> findByPatientAndDoctor(
            PatientProfile patient, DoctorProfile doctor);
}