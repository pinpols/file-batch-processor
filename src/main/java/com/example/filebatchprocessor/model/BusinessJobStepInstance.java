package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
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
@Table(name = "job_step_instance", indexes = {
        @Index(name = "uk_job_step_instance_job_step_attempt", columnList = "job_instance_id,step_code,attempt_no", unique = true),
        @Index(name = "idx_job_step_instance_job_order", columnList = "job_instance_id,step_order_no"),
        @Index(name = "idx_job_step_instance_status", columnList = "status")
})
public class BusinessJobStepInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_instance_id", nullable = false)
    private Long jobInstanceId;

    @Column(name = "step_code", nullable = false, length = 128)
    private String stepCode;

    @Column(name = "step_name", nullable = false, length = 128)
    private String stepName;

    @Column(name = "step_order_no", nullable = false)
    private Integer stepOrderNo = 1;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    @Column(name = "spring_step_execution_id")
    private Long springStepExecutionId;

    @Column(name = "read_count", nullable = false)
    private Long readCount = 0L;

    @Column(name = "write_count", nullable = false)
    private Long writeCount = 0L;

    @Column(name = "filter_count", nullable = false)
    private Long filterCount = 0L;

    @Column(name = "skip_count", nullable = false)
    private Long skipCount = 0L;

    @Column(name = "commit_count", nullable = false)
    private Long commitCount = 0L;

    @Column(name = "rollback_count", nullable = false)
    private Long rollbackCount = 0L;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
