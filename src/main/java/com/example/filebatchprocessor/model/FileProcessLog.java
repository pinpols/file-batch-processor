package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_process_log", indexes = {
        @Index(name = "idx_file_process_log_file", columnList = "file_record_id,created_at"),
        @Index(name = "idx_file_process_log_task", columnList = "task_id,created_at"),
        @Index(name = "idx_file_process_log_result", columnList = "result,created_at")
})
public class FileProcessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_record_id", nullable = false)
    private Long fileRecordId;

    @Column(name = "step_name", nullable = false, length = 128)
    private String stepName;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "status_from", length = 32)
    private String statusFrom;

    @Column(name = "status_to", length = 32)
    private String statusTo;

    @Column(nullable = false, length = 32)
    private String result;

    @Column(name = "task_id", length = 128)
    private String taskId;

    @Column(name = "job_name", length = 128)
    private String jobName;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "step_execution_id")
    private Long stepExecutionId;

    @Column(length = 128)
    private String operator;

    @Column(name = "run_key", length = 200)
    private String runKey;

    @Column(name = "retry_no", nullable = false)
    private Integer retryNo = 0;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_msg", length = 1000)
    private String errorMsg;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String extra;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
