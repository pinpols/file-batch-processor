package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "job_execution_log",
        indexes = {
            @Index(name = "idx_job_execution_log_job_created", columnList = "job_instance_id,created_at"),
            @Index(name = "idx_job_execution_log_step_created", columnList = "job_step_instance_id,created_at"),
            @Index(name = "idx_job_execution_log_event", columnList = "event_type,created_at")
        })
public class BusinessJobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_instance_id", nullable = false)
    private Long jobInstanceId;

    @Column(name = "job_step_instance_id")
    private Long jobStepInstanceId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String level = "INFO";

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
