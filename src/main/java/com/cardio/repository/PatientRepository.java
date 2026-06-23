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
           "WHERE p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) " +
           "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám'))")
    List<PatientProfile> findPatientsAssignedToDoctor(@Param("doctorId") Integer doctorId);

    @Query("SELECT DISTINCT p FROM PatientProfile p " +
           "WHERE p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) " +
           "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám'))")
    Page<PatientProfile> findPatientsAssignedToDoctor(@Param("doctorId") Integer doctorId, Pageable pageable);

    @Query("SELECT DISTINCT p FROM PatientProfile p " +
           "WHERE (p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) " +
           "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám'))) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PatientProfile> searchPatientsAssignedToDoctor(@Param("doctorId") Integer doctorId, @Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT p FROM PatientProfile p " +
           "WHERE (" +
           "  p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId AND (:date IS NULL OR (c.visitDate >= :startDateTime AND c.visitDate <= :endDateTime))) " +
           "  OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám') AND (:date IS NULL OR a.scheduledDate = :date))" +
           ") " +
           "AND (:search IS NULL OR :search = '' OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PatientProfile> searchAssignedPatients(
            @Param("doctorId") Integer doctorId, 
            @Param("search") String search, 
            @Param("date") java.time.LocalDate date, 
            @Param("startDateTime") java.time.LocalDateTime startDateTime, 
            @Param("endDateTime") java.time.LocalDateTime endDateTime, 
            Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PatientProfile p " +
           "WHERE p.patientId = :patientId AND (" +
           "p.patientId IN (SELECT c.patient.patientId FROM ConsultationRecord c WHERE c.doctor.doctorId = :doctorId) " +
           "OR p.patientId IN (SELECT a.patient.patientId FROM Appointment a WHERE a.doctor.doctorId = :doctorId AND a.status IN ('InProgress', 'Completed', 'Đã khám', 'Đã khám xong', 'Đang khám'))" +
           ")")
    boolean isPatientAssignedToDoctor(@Param("patientId") Integer patientId, @Param("doctorId") Integer doctorId);
}

