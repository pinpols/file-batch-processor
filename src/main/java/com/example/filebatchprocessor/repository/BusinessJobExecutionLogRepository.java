package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.BusinessJobExecutionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessJobExecutionLogRepository extends JpaRepository<BusinessJobExecutionLog, Long> {

    List<BusinessJobExecutionLog> findByJobInstanceIdOrderByCreatedAtAsc(Long jobInstanceId);
}
