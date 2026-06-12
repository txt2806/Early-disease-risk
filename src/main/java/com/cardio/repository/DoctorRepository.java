package com.cardio.repository;

import com.cardio.model.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// ── REPOSITORY (tầng truy cập DB) ────────────────────
public interface DoctorRepository extends JpaRepository<DoctorProfile, Integer> {
    Optional<DoctorProfile> findByUsername(String username);
    Optional<DoctorProfile> findByUsernameIgnoreCase(String username);
}
