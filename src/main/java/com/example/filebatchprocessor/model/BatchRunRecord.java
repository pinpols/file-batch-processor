package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 批次运行审计记录：用于追踪每次作业执行状态与核心指标。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "batch_run_records",
        indexes = {
            @Index(name = "idx_batch_run_job_name", columnList = "jobName"),
            @Index(name = "idx_batch_run_status", columnList = "status")
        })
public class BatchRunRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long jobExecutionId;

    @Column(nullable = false, length = 128)
    private String jobName;

    @Column(length = 64)
    private String tenantId;

    @Column(length = 64)
    private String bizDomain;

    @Column(length = 2000)
    private String jobParams;

    @Column(nullable = false, length = 32)
    private String status;

    private Long readCount = 0L;
    private Long writeCount = 0L;
    private Long skipCount = 0L;
    private Long parseErrorCount = 0L;
    private Long durationMs = 0L;
    private Double throughputRps = 0.0;
    private Long rollbackCount = 0L;
    private Long commitCount = 0L;
    private Long retryCount = 0L;

    private Boolean qualityPassed = Boolean.TRUE;

    @Column(length = 500)
    private String qualityMessage;

    @Column(length = 1000)
    private String errorMessage;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Explicit getters and setters as workaround for Lombok annotation processing issue

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public void setJobExecutionId(Long jobExecutionId) {
        this.jobExecutionId = jobExecutionId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getBizDomain() {
        return bizDomain;
    }

    public void setBizDomain(String bizDomain) {
        this.bizDomain = bizDomain;
    }

    public String getJobParams() {
        return jobParams;
    }

    public void setJobParams(String jobParams) {
        this.jobParams = jobParams;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getReadCount() {
        return readCount;
    }

    public void setReadCount(Long readCount) {
        this.readCount = readCount;
    }

    public Long getWriteCount() {
        return writeCount;
    }

    public void setWriteCount(Long writeCount) {
        this.writeCount = writeCount;
    }

    public Long getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(Long skipCount) {
        this.skipCount = skipCount;
    }

    public Long getParseErrorCount() {
        return parseErrorCount;
    }

    public void setParseErrorCount(Long parseErrorCount) {
        this.parseErrorCount = parseErrorCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Double getThroughputRps() {
        return throughputRps;
    }

    public void setThroughputRps(Double throughputRps) {
        this.throughputRps = throughputRps;
    }

    public Long getRollbackCount() {
        return rollbackCount;
    }

    public void setRollbackCount(Long rollbackCount) {
        this.rollbackCount = rollbackCount;
    }

    public Long getCommitCount() {
        return commitCount;
    }

    public void setCommitCount(Long commitCount) {
        this.commitCount = commitCount;
    }

    public Long getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Long retryCount) {
        this.retryCount = retryCount;
    }

    public Boolean getQualityPassed() {
        return qualityPassed;
    }

    public void setQualityPassed(Boolean qualityPassed) {
        this.qualityPassed = qualityPassed;
    }

    public String getQualityMessage() {
        return qualityMessage;
    }

    public void setQualityMessage(String qualityMessage) {
        this.qualityMessage = qualityMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
