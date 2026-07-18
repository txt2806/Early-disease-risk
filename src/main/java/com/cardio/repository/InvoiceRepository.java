package com.cardio.repository;

import com.cardio.model.Invoice;
import com.cardio.model.PatientProfile;
import com.cardio.model.Appointment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {
    @EntityGraph(attributePaths = {"patient", "appointment"})
    List<Invoice> findByPatientOrderByCreatedDateDesc(PatientProfile patient);

    @EntityGraph(attributePaths = {"patient", "appointment"})
    Optional<Invoice> findByAppointment(Appointment appointment);

    @EntityGraph(attributePaths = {"patient", "appointment"})
    Optional<Invoice> findByReferenceCode(String referenceCode);

    @Override
    @EntityGraph(attributePaths = {"patient", "appointment"})
    List<Invoice> findAll();
}
