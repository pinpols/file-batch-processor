package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskExecutionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionAuditRepository extends JpaRepository<TaskExecutionAudit, Long> {
    List<TaskExecutionAudit> findTop200ByOrderByCreatedAtDesc();
    List<TaskExecutionAudit> findTop200ByTaskIdOrderByCreatedAtDesc(String taskId);
}
