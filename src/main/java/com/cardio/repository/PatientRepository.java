package com.cardio.repository;

import com.cardio.model.PatientProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<PatientProfile, Integer> {
       Optional<PatientProfile> findByUsername(String username);

       Optional<PatientProfile> findByUsernameIgnoreCase(String username);

       List<PatientProfile> findByFullNameContainingIgnoreCase(String name);

       Page<PatientProfile> findByFullNameContainingIgnoreCase(String name, Pageable pageable);

       List<PatientProfile> findByPhoneIn(List<String> phones);

       @Query("SELECT DISTINCT p FROM PatientProfile p " +
                     "WHERE p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) "
                     +
                     "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám'))")
       List<PatientProfile> findPatientsAssignedToDoctor(@Param("doctorId") Integer doctorId);

       @Query("SELECT DISTINCT p FROM PatientProfile p " +
                     "WHERE p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) "
                     +
                     "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám'))")
       Page<PatientProfile> findPatientsAssignedToDoctor(@Param("doctorId") Integer doctorId, Pageable pageable);

       @Query("SELECT DISTINCT p FROM PatientProfile p " +
                     "WHERE (p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) "
                     +
                     "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám'))) "
                     +
                     "AND (:search IS NULL OR :search = '' OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
       Page<PatientProfile> searchPatientsAssignedToDoctor(@Param("doctorId") Integer doctorId,
                     @Param("search") String search, Pageable pageable);

       @Query("SELECT DISTINCT p FROM PatientProfile p " +
                     "WHERE (" +
                     "  p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId AND (:date IS NULL OR (c.visitDate >= :startDateTime AND c.visitDate <= :endDateTime))) "
                     +
                     "  OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám') AND (:date IS NULL OR a.scheduledDate = :date))"
                     +
                     ") " +
                     "AND (:search IS NULL OR :search = '' OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
       Page<PatientProfile> searchAssignedPatients(
                     @Param("doctorId") Integer doctorId,
                     @Param("search") String search,
                     @Param("date") java.time.LocalDate date,
                     @Param("startDateTime") java.time.LocalDateTime startDateTime,
                     @Param("endDateTime") java.time.LocalDateTime endDateTime,
                     Pageable pageable);

       // [FIX] Bỏ filter status appointment — bác sĩ phải xem được hồ sơ bệnh nhân
       // ngay khi có bất kỳ lịch hẹn nào (kể cả Pending/Scheduled chưa khám),
       // không chỉ sau khi đã Completed. Trước đây bị redirect "không có quyền"
       // vì bệnh nhân chỉ có appointment mới đặt, chưa có ConsultationRecord nào.
       @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PatientProfile p " +
                     "WHERE p.patientId = :patientId AND (" +
                     "p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) "
                     +
                     "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId)"
                     +
                     ")")
       boolean isPatientAssignedToDoctor(@Param("patientId") Integer patientId, @Param("doctorId") Integer doctorId);

        @Query("SELECT p, " +
               " (SELECT MAX(c.visitDate) FROM ConsultationRecord c WHERE c.patient = p) AS lastVisitDate, " +
               " (SELECT MAX(c2.doctor.fullName) FROM ConsultationRecord c2 WHERE c2.patient = p AND c2.visitDate = (SELECT MAX(c3.visitDate) FROM ConsultationRecord c3 WHERE c3.patient = p)) AS doctorName, " +
               " (SELECT MAX(ar.riskLevel) FROM ConsultationRecord cr2 JOIN cr2.aiRiskPrediction ar WHERE cr2.patient = p AND cr2.visitDate = (SELECT MAX(cr3.visitDate) FROM ConsultationRecord cr3 WHERE cr3.patient = p)) AS aiRisk " +
               "FROM PatientProfile p " +
               "WHERE (:search IS NULL OR :search = '' OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
               "AND (:doctorSearch IS NULL OR :doctorSearch = '' OR LOWER(" +
               "   (SELECT MAX(c2b.doctor.fullName) FROM ConsultationRecord c2b WHERE c2b.patient = p AND c2b.visitDate = (SELECT MAX(c3b.visitDate) FROM ConsultationRecord c3b WHERE c3b.patient = p))" +
               "  ) LIKE LOWER(CONCAT('%', :doctorSearch, '%'))) " +
               "AND (" +
               "     :tab = 'all' " +
               "     OR (:tab = 'waiting' AND p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.scheduledDate = :today AND a.status IN ('Pending', 'Confirmed', 'CheckedIn', 'InProgress'))) " +
               "     OR (:tab = 'high_risk' AND p.patientId IN (SELECT DISTINCT ar.record.patient.patientId FROM AIRiskPrediction ar WHERE ar.riskLevel = 'HIGH')) " +
               "     OR (:tab = 'today' AND p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.scheduledDate = :today AND a.status != 'Cancelled')) " +
               "     OR (:tab = 'completed' AND p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.scheduledDate = :today AND a.status = 'Completed')) " +
               ")")
        Page<Object[]> findPatientsWithLastVisitAndTab(
            @Param("tab") String tab,
            @Param("search") String search,
            @Param("doctorSearch") String doctorSearch,
            @Param("today") java.time.LocalDate today,
            Pageable pageable);

        @Query("SELECT p, " +
               " (SELECT MAX(c.visitDate) FROM ConsultationRecord c WHERE c.patient = p) AS lastVisitDate, " +
               " (SELECT MAX(c2.doctor.fullName) FROM ConsultationRecord c2 WHERE c2.patient = p AND c2.visitDate = (SELECT MAX(c3.visitDate) FROM ConsultationRecord c3 WHERE c3.patient = p)) AS doctorName, " +
               " (SELECT MAX(ar.riskLevel) FROM ConsultationRecord cr2 JOIN cr2.aiRiskPrediction ar WHERE cr2.patient = p AND cr2.visitDate = (SELECT MAX(cr3.visitDate) FROM ConsultationRecord cr3 WHERE cr3.patient = p)) AS aiRisk " +
               "FROM PatientProfile p " +
               "ORDER BY CASE " +
               "  WHEN (SELECT MAX(ar2.riskLevel) FROM ConsultationRecord cr2b JOIN cr2b.aiRiskPrediction ar2 WHERE cr2b.patient = p AND cr2b.visitDate = (SELECT MAX(cr3b.visitDate) FROM ConsultationRecord cr3b WHERE cr3b.patient = p)) = 'HIGH' THEN 1 " +
               "  WHEN (SELECT MAX(ar2.riskLevel) FROM ConsultationRecord cr2b JOIN cr2b.aiRiskPrediction ar2 WHERE cr2b.patient = p AND cr2b.visitDate = (SELECT MAX(cr3b.visitDate) FROM ConsultationRecord cr3b WHERE cr3b.patient = p)) = 'MEDIUM' THEN 2 " +
               "  WHEN (SELECT MAX(ar2.riskLevel) FROM ConsultationRecord cr2b JOIN cr2b.aiRiskPrediction ar2 WHERE cr2b.patient = p AND cr2b.visitDate = (SELECT MAX(cr3b.visitDate) FROM ConsultationRecord cr3b WHERE cr3b.patient = p)) = 'LOW' THEN 3 " +
               "  ELSE 4 " +
               "END ASC, p.fullName ASC")
        Page<Object[]> findPatientsPrioritizedByRisk(Pageable pageable);
}