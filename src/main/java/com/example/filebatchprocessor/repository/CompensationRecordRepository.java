package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.CompensationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, Long> {

    List<CompensationRecord> findByTargetJobInstanceIdOrderByCreatedAtDesc(Long targetJobInstanceId);

    List<CompensationRecord> findByRelatedDlqRecordIdOrderByCreatedAtDesc(Long relatedDlqRecordId);

    List<CompensationRecord> findByLegacyDistributionTaskIdOrderByCreatedAtDesc(Long legacyDistributionTaskId);
}
