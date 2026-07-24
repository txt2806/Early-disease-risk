package com.cardio.repository;

import com.cardio.model.Appointment;
import com.cardio.model.PatientProfile;
import com.cardio.dto.AppointmentViewDTO;
import com.cardio.model.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    List<Appointment> findByPatientOrderByScheduledDateDescTimeSlotDesc(PatientProfile patient);
    List<Appointment> findByDoctorOrderByScheduledDateDescTimeSlotDesc(DoctorProfile doctor);
    
    @Query("SELECT a FROM Appointment a ORDER BY a.scheduledDate DESC, a.timeSlot DESC")
    List<Appointment> findAllOrderByDateAndTime();

    long countByDoctorAndScheduledDateAndStatusNot(DoctorProfile doctor, java.time.LocalDate scheduledDate, String status);

    List<Appointment> findAllByOrderByScheduledDateDescTimeSlotAsc();
    @Query("SELECT a FROM Appointment a WHERE " +
           "(:date IS NULL OR a.scheduledDate = :date) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(a.patient.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.patient.phone) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY a.scheduledDate DESC, a.timeSlot DESC")
    List<Appointment> findByDateAndPatientNameOrPhone(
            @Param("date") java.time.LocalDate date,
            @Param("search") String search);

    boolean existsByDoctorAndScheduledDateAndTimeSlotAndStatusNot(DoctorProfile doctor, java.time.LocalDate scheduledDate, java.time.LocalTime timeSlot, String status);

    boolean existsByDoctorAndScheduledDateAndTimeSlotAndStatusNotAndAppointmentIdNot(DoctorProfile doctor, java.time.LocalDate scheduledDate, java.time.LocalTime timeSlot, String status, Integer appointmentId);

    boolean existsByPatientAndScheduledDateAndTimeSlotAndStatusNot(PatientProfile patient, java.time.LocalDate scheduledDate, java.time.LocalTime timeSlot, String status);

    boolean existsByPatientAndScheduledDateAndTimeSlotAndStatusNotAndAppointmentIdNot(PatientProfile patient, java.time.LocalDate scheduledDate, java.time.LocalTime timeSlot, String status, Integer appointmentId);

    boolean existsByDoctorAndStatus(DoctorProfile doctor, String status);

    boolean existsByDoctorAndScheduledDateAndStatusAndAppointmentIdNot(DoctorProfile doctor, java.time.LocalDate scheduledDate, String status, Integer appointmentId);

    // --- METHOD ADDED FROM USER'S MERGE REQUEST ---
    // This query is used by the AppointmentController for the patient mobile app.
    @Query(value = "SELECT d.FullName AS doctorName, d.Specialty AS specialty, " +
                   "a.ScheduledDate AS scheduledDate, a.TimeSlot AS timeSlot, a.Status AS status " +
                   "FROM Appointment a " +
                   "JOIN Doctor_Profile d ON a.DoctorID = d.DoctorID " +
                   "WHERE a.PatientID = :patientId " +
                   "ORDER BY a.ScheduledDate DESC, a.TimeSlot DESC", nativeQuery = true)
    List<AppointmentViewDTO> findAppointmentDetailsByPatientId(@Param("patientId") Integer patientId);
}
