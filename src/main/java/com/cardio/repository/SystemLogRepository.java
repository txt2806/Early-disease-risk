package com.cardio.repository;

import com.cardio.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SystemLogRepository extends JpaRepository<SystemLog, Integer> {
    List<SystemLog> findAllByOrderByTimestampDesc();
}
