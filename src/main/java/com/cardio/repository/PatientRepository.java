package com.cardio.repository;

import com.cardio.model.PatientProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<PatientProfile, Integer> {
<<<<<<< Updated upstream
    Optional<PatientProfile> findByUsername(String username);
    List<PatientProfile> findByFullNameContainingIgnoreCase(String name);
=======
    Optional<PatientProfile> findByUsernameIgnoreCase(String username);
>>>>>>> Stashed changes
    Page<PatientProfile> findByFullNameContainingIgnoreCase(String name, Pageable pageable);
    java.util.List<PatientProfile> findByPhoneIn(java.util.Collection<String> phones);
}

