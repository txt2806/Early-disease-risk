package com.cardio.repository;

import com.cardio.model.PatientProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<PatientProfile, Integer> {
    Optional<PatientProfile> findByUsername(String username);
<<<<<<< Updated upstream
=======
    Optional<PatientProfile> findByUsernameIgnoreCase(String username);
    List<PatientProfile> findByFullNameContainingIgnoreCase(String name);
>>>>>>> Stashed changes
    Page<PatientProfile> findByFullNameContainingIgnoreCase(String name, Pageable pageable);
}

