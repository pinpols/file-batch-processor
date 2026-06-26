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
        name = "quality_gate_results",
        indexes = {
            @Index(name = "idx_quality_gate_created_at", columnList = "created_at"),
            @Index(name = "idx_quality_gate_job_batch", columnList = "job_name,batch_date"),
            @Index(name = "idx_quality_gate_status", columnList = "status")
        })
public class QualityGateResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gate_type", nullable = false, length = 64)
    private String gateType;

    @Column(name = "job_name", length = 128)
    private String jobName;

    @Column(name = "step_name", length = 128)
    private String stepName;

    @Column(name = "batch_date", length = 32)
    private String batchDate;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "step_execution_id")
    private Long stepExecutionId;

    @Column(name = "read_count", nullable = false)
    private long readCount;

    @Column(name = "parse_error_count", nullable = false)
    private long parseErrorCount;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    @Column(name = "error_rate", nullable = false)
    private double errorRate;

    @Column(name = "max_rate", nullable = false)
    private double maxRate;

    @Column(name = "min_lines", nullable = false)
    private long minLines;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Explicit getters and setters as workaround for Lombok annotation processing issue

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGateType() {
        return gateType;
    }

    public void setGateType(String gateType) {
        this.gateType = gateType;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getBatchDate() {
        return batchDate;
    }

    public void setBatchDate(String batchDate) {
        this.batchDate = batchDate;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public void setJobExecutionId(Long jobExecutionId) {
        this.jobExecutionId = jobExecutionId;
    }

    public Long getStepExecutionId() {
        return stepExecutionId;
    }

    public void setStepExecutionId(Long stepExecutionId) {
        this.stepExecutionId = stepExecutionId;
    }

    public long getReadCount() {
        return readCount;
    }

    public void setReadCount(long readCount) {
        this.readCount = readCount;
    }

    public long getParseErrorCount() {
        return parseErrorCount;
    }

    public void setParseErrorCount(long parseErrorCount) {
        this.parseErrorCount = parseErrorCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getMaxRate() {
        return maxRate;
    }

    public void setMaxRate(double maxRate) {
        this.maxRate = maxRate;
    }

    public long getMinLines() {
        return minLines;
    }

    public void setMinLines(long minLines) {
        this.minLines = minLines;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
