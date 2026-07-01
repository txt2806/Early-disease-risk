package com.cardio.repository;

import com.cardio.model.Invoice;
import com.cardio.model.PatientProfile;
import com.cardio.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {
    List<Invoice> findByPatientOrderByCreatedDateDesc(PatientProfile patient);
    Optional<Invoice> findByAppointment(Appointment appointment);
    Optional<Invoice> findByReferenceCode(String referenceCode);
}
