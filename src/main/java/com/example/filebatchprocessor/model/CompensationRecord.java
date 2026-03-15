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
@Table(name = "compensation_record", indexes = {
        @Index(name = "uk_compensation_record_no", columnList = "compensation_no", unique = true),
        @Index(name = "idx_compensation_record_status_created", columnList = "status,created_at"),
        @Index(name = "idx_compensation_record_action_created", columnList = "action_type,created_at"),
        @Index(name = "idx_compensation_record_target_job", columnList = "target_job_instance_id,created_at")
})
public class CompensationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "compensation_no", nullable = false, length = 64)
    private String compensationNo;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "target_job_instance_id")
    private Long targetJobInstanceId;

    @Column(name = "target_step_instance_id")
    private Long targetStepInstanceId;

    @Column(name = "related_file_id")
    private Long relatedFileId;

    @Column(name = "related_dlq_record_id")
    private Long relatedDlqRecordId;

    @Column(name = "legacy_distribution_task_id")
    private Long legacyDistributionTaskId;

    @Column(name = "source_spring_execution_id")
    private Long sourceSpringExecutionId;

    @Column(name = "restarted_spring_execution_id")
    private Long restartedSpringExecutionId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    private String resultPayload;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
