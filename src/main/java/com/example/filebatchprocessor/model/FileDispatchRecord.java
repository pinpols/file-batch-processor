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
@Table(name = "file_dispatch_record", indexes = {
        @Index(name = "uk_file_dispatch_record_dispatch_no", columnList = "dispatch_no", unique = true),
        @Index(name = "uk_file_dispatch_record_dispatch_key", columnList = "dispatch_key", unique = true),
        @Index(name = "idx_file_dispatch_record_file", columnList = "file_record_id,created_at"),
        @Index(name = "idx_file_dispatch_record_status", columnList = "dispatch_status,ack_status"),
        @Index(name = "idx_file_dispatch_record_target", columnList = "target_system,dispatch_channel")
})
public class FileDispatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispatch_no", nullable = false, length = 64)
    private String dispatchNo;

    @Column(name = "dispatch_key", nullable = false, length = 200)
    private String dispatchKey;

    @Column(name = "file_record_id", nullable = false)
    private Long fileRecordId;

    @Column(name = "legacy_distribution_task_id")
    private Long legacyDistributionTaskId;

    @Column(name = "created_job_instance_id")
    private Long createdJobInstanceId;

    @Column(name = "last_dispatch_job_instance_id")
    private Long lastDispatchJobInstanceId;

    @Column(name = "last_ack_job_instance_id")
    private Long lastAckJobInstanceId;

    @Column(name = "target_system", nullable = false, length = 100)
    private String targetSystem;

    @Column(name = "dispatch_channel", nullable = false, length = 32)
    private String dispatchChannel;

    @Column(name = "target_address", length = 500)
    private String targetAddress;

    @Column(name = "file_version_no", nullable = false)
    private Integer fileVersionNo = 1;

    @Column(name = "dispatch_status", nullable = false, length = 32)
    private String dispatchStatus = "PENDING";

    @Column(name = "ack_status", nullable = false, length = 32)
    private String ackStatus = "NOT_REQUIRED";

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;

    @Column(name = "ack_required", nullable = false)
    private Boolean ackRequired = Boolean.FALSE;

    @Column(name = "ack_timeout_minutes", nullable = false)
    private Integer ackTimeoutMinutes = 120;

    @Column(name = "last_dispatch_time")
    private LocalDateTime lastDispatchTime;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "ack_time")
    private LocalDateTime ackTime;

    @Column(name = "ack_deadline_at")
    private LocalDateTime ackDeadlineAt;

    @Column(name = "ack_message", length = 1000)
    private String ackMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ack_payload", columnDefinition = "jsonb")
    private String ackPayload;

    @Column(name = "resend_count", nullable = false)
    private Integer resendCount = 0;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_msg", length = 1000)
    private String errorMsg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
