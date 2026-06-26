package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BatchRunRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchRunRecordRepository extends JpaRepository<BatchRunRecord, Long> {

    Optional<BatchRunRecord> findByJobExecutionId(Long jobExecutionId);

    long countByStatusAndCreatedAtAfter(String status, LocalDateTime since);

    long countByStatusIn(List<String> statuses);

    List<BatchRunRecord> findTop200ByOrderByCreatedAtDesc();

    long deleteByCreatedAtBefore(LocalDateTime cutoffTime);
}
