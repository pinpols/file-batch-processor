package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ExecutionDedupRecord;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionDedupRecordRepository extends JpaRepository<ExecutionDedupRecord, Long> {
    long deleteByCreatedAtBefore(LocalDateTime cutoffTime);
}
