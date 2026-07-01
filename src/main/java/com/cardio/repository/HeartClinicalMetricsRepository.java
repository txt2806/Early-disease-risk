package com.cardio.repository;

import com.cardio.model.HeartClinicalMetrics;
import com.cardio.model.ConsultationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface HeartClinicalMetricsRepository extends JpaRepository<HeartClinicalMetrics, Integer> {
    Optional<HeartClinicalMetrics> findByRecord(ConsultationRecord record);
    java.util.List<HeartClinicalMetrics> findByRecordRecordId(Integer recordId);
}