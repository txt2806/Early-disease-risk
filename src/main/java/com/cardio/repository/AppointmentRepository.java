package com.cardio.repository;

import com.cardio.model.Appointment;
import com.cardio.model.PatientProfile;
import com.cardio.model.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    List<Appointment> findByPatientOrderByScheduledDateDescTimeSlotDesc(PatientProfile patient);
    List<Appointment> findByDoctorOrderByScheduledDateDescTimeSlotDesc(DoctorProfile doctor);
    
    @Query("SELECT a FROM Appointment a ORDER BY a.scheduledDate DESC, a.timeSlot DESC")
    List<Appointment> findAllOrderByDateAndTime();

    long countByDoctorAndScheduledDateAndStatusNot(DoctorProfile doctor, java.time.LocalDate scheduledDate, String status);
}
