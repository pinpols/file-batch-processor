package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "task_execution_audit",
        indexes = {
            @Index(name = "idx_task_exec_audit_task_created", columnList = "task_id, created_at"),
            @Index(name = "idx_task_exec_audit_event", columnList = "event_type, created_at")
        })
public class TaskExecutionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    @Column(name = "batch_date", length = 32)
    private String batchDate;

    @Column(name = "run_key", length = 200)
    private String runKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "params")
    private String params;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
