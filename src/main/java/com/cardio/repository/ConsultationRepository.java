package com.cardio.repository;

import com.cardio.model.ConsultationRecord;
import com.cardio.model.DoctorProfile;
import com.cardio.model.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConsultationRepository extends JpaRepository<ConsultationRecord, Integer> {
    List<ConsultationRecord> findByDoctorOrderByVisitDateDesc(DoctorProfile doctor);
    List<ConsultationRecord> findByPatientOrderByVisitDateDesc(PatientProfile patient);
    List<ConsultationRecord> findByPatientPatientIdAndStatusOrderByVisitDateDesc(Integer patientId, String status);
}
