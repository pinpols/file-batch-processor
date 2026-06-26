package com.example.filebatchprocessor.repository;

import com.example.filebatchprocessor.model.TaskExecutionAudit;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskExecutionAuditRepository extends JpaRepository<TaskExecutionAudit, Long> {
    List<TaskExecutionAudit> findTop200ByOrderByCreatedAtDesc();

    List<TaskExecutionAudit> findTop200ByTaskIdOrderByCreatedAtDesc(String taskId);

    Page<TaskExecutionAudit> findByTaskId(String taskId, Pageable pageable);

    Page<TaskExecutionAudit> findByTaskIdAndCreatedAtBetween(
            String taskId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query(
            "SELECT a FROM TaskExecutionAudit a WHERE a.taskId = :taskId AND a.status = :status ORDER BY a.createdAt DESC")
    List<TaskExecutionAudit> findByTaskIdAndStatus(
            @Param("taskId") String taskId, @Param("status") String status, Pageable pageable);

    @Query("SELECT a FROM TaskExecutionAudit a WHERE a.status = :status ORDER BY a.createdAt DESC")
    Page<TaskExecutionAudit> findByStatus(@Param("status") String status, Pageable pageable);

    @Query(
            "SELECT COUNT(a) FROM TaskExecutionAudit a WHERE a.taskId = :taskId AND a.status = :status AND a.createdAt >= :since")
    long countByTaskIdAndStatusSince(
            @Param("taskId") String taskId, @Param("status") String status, @Param("since") LocalDateTime since);
}
