package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.ExecutionDedupRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ExecutionDedupRecordRepository extends JpaRepository<ExecutionDedupRecord, Long> {
    long deleteByCreatedAtBefore(LocalDateTime cutoffTime);
}
