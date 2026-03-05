package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BatchRunRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface BatchRunRecordRepository extends JpaRepository<BatchRunRecord, Long> {

    Optional<BatchRunRecord> findByJobExecutionId(Long jobExecutionId);
    long countByStatusAndCreatedAtAfter(String status, LocalDateTime since);
    long countByStatusIn(List<String> statuses);
    List<BatchRunRecord> findTop200ByOrderByCreatedAtDesc();
    long deleteByCreatedAtBefore(LocalDateTime cutoffTime);
}
