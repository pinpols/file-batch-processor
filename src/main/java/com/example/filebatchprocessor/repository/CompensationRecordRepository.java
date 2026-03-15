package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.CompensationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, Long> {

    List<CompensationRecord> findByTargetJobInstanceIdOrderByCreatedAtDesc(Long targetJobInstanceId);

    List<CompensationRecord> findByRelatedDlqRecordIdOrderByCreatedAtDesc(Long relatedDlqRecordId);

    List<CompensationRecord> findByLegacyDistributionTaskIdOrderByCreatedAtDesc(Long legacyDistributionTaskId);
}
