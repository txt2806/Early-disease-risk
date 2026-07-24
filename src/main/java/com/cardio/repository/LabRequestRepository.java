package com.cardio.repository;

import com.cardio.model.DoctorProfile;
import com.cardio.model.LabRequest;
import com.cardio.model.PatientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LabRequestRepository extends JpaRepository<LabRequest, Integer> {
    List<LabRequest> findByPatientOrderByCreatedAtDesc(PatientProfile patient);
    List<LabRequest> findByDoctorOrderByCreatedAtDesc(DoctorProfile doctor);
    List<LabRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<LabRequest> findAllByOrderByCreatedAtDesc();
    List<LabRequest> findAllByOrderByCreatedAtAsc();
}
