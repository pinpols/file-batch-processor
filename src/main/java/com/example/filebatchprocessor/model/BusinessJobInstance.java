package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "job_instance",
        indexes = {
            @Index(name = "uk_job_instance_no", columnList = "job_instance_no", unique = true),
            @Index(name = "idx_job_instance_task_created", columnList = "task_id,created_at"),
            @Index(name = "idx_job_instance_status_created", columnList = "status,created_at"),
            @Index(name = "idx_job_instance_related_file", columnList = "related_file_id,created_at"),
            @Index(name = "idx_job_instance_run_key", columnList = "run_key")
        })
public class BusinessJobInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_instance_no", nullable = false, length = 64)
    private String jobInstanceNo;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    @Column(name = "trigger_source", nullable = false, length = 32)
    private String triggerSource;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "biz_date", length = 32)
    private String bizDate;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "run_key", length = 200)
    private String runKey;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "rerun_flag", nullable = false)
    private Boolean rerunFlag = Boolean.FALSE;

    @Column(name = "retry_flag", nullable = false)
    private Boolean retryFlag = Boolean.FALSE;

    @Column(name = "manual_flag", nullable = false)
    private Boolean manualFlag = Boolean.FALSE;

    @Column(name = "related_file_id")
    private Long relatedFileId;

    @Column(name = "spring_batch_execution_id")
    private Long springBatchExecutionId;

    @Column(name = "spring_batch_instance_id")
    private Long springBatchInstanceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary", columnDefinition = "jsonb")
    private String resultSummary;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
