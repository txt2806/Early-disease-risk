package com.cardio.repository;

import com.cardio.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    List<Appointment> findAllByOrderByScheduledDateDescTimeSlotAsc();
}
