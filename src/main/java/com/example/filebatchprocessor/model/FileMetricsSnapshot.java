package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_metrics_snapshot")
public class FileMetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "received_count", nullable = false)
    private Long receivedCount = 0L;

    @Column(name = "processed_count", nullable = false)
    private Long processedCount = 0L;

    @Column(name = "failed_count", nullable = false)
    private Long failedCount = 0L;

    @Column(name = "processing_count", nullable = false)
    private Long processingCount = 0L;

    @Column(name = "pending_count", nullable = false)
    private Long pendingCount = 0L;

    @Column(name = "avg_processing_duration_sec")
    private Double avgProcessingDurationSec;

    @Column(name = "max_processing_duration_sec")
    private Double maxProcessingDurationSec;

    @Column(name = "min_processing_duration_sec")
    private Double minProcessingDurationSec;

    @Column(name = "dispatch_count", nullable = false)
    private Long dispatchCount = 0L;

    @Column(name = "dispatch_success_count", nullable = false)
    private Long dispatchSuccessCount = 0L;

    @Column(name = "dispatch_failed_count", nullable = false)
    private Long dispatchFailedCount = 0L;

    @Column(name = "dispatch_pending_count", nullable = false)
    private Long dispatchPendingCount = 0L;

    @Column(name = "ack_timeout_count", nullable = false)
    private Long ackTimeoutCount = 0L;

    @Column(name = "archive_count", nullable = false)
    private Long archiveCount = 0L;

    @Column(name = "archive_failed_count", nullable = false)
    private Long archiveFailedCount = 0L;

    @Column(name = "dlq_count", nullable = false)
    private Long dlqCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (snapshotTime == null) {
            snapshotTime = LocalDateTime.now();
        }
        if (metricDate == null) {
            metricDate = LocalDate.now();
        }
    }
}
