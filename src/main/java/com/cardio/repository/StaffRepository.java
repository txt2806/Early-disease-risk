package com.cardio.repository;

import com.cardio.model.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<StaffProfile, Integer> {
    Optional<StaffProfile> findByUsername(String username);
}
