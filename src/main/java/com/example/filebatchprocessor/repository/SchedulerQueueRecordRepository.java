package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.SchedulerQueueRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerQueueRecordRepository extends JpaRepository<SchedulerQueueRecord, String> {
}
