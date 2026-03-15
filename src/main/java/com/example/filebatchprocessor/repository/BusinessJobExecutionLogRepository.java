package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BusinessJobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessJobExecutionLogRepository extends JpaRepository<BusinessJobExecutionLog, Long> {

    List<BusinessJobExecutionLog> findByJobInstanceIdOrderByCreatedAtAsc(Long jobInstanceId);
}
