package com.cardio.repository;

import com.cardio.model.AIRiskPrediction;
import com.cardio.model.ConsultationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AIRiskRepository extends JpaRepository<AIRiskPrediction, Integer> {
    Optional<AIRiskPrediction> findByRecord(ConsultationRecord record);

    // Lấy tất cả cảnh báo mức HIGH chưa xử lý
    @Query("SELECT p FROM AIRiskPrediction p WHERE p.riskLevel = 'HIGH' AND p.isAlertSent = false")
    List<AIRiskPrediction> findUnhandledHighAlerts();

    // Lấy cảnh báo theo bác sĩ
    @Query("SELECT p FROM AIRiskPrediction p WHERE p.record.doctor.doctorId = :doctorId ORDER BY p.riskScore DESC")
    List<AIRiskPrediction> findByDoctorId(Integer doctorId);

    // Lấy tất cả kết quả chẩn đoán AI của bệnh nhân
    @Query("SELECT p FROM AIRiskPrediction p WHERE p.record.patient = :patient ORDER BY p.record.visitDate DESC")
    List<AIRiskPrediction> findByPatient(com.cardio.model.PatientProfile patient);
}
