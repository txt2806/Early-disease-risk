package com.cardio.repository;

import com.cardio.model.RecordIcd;
import com.cardio.model.RecordIcdKey;
import com.cardio.model.ConsultationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecordIcdRepository extends JpaRepository<RecordIcd, RecordIcdKey> {
    List<RecordIcd> findByRecord(ConsultationRecord record);
    List<RecordIcd> findByRecordRecordId(Integer recordId);
}
