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
@Table(name = "migration_status")
public class MigrationStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "migration_name", nullable = false, length = 128, unique = true)
    private String migrationName;

    @Column(name = "migration_phase", nullable = false, length = 32)
    private String migrationPhase;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "total_records")
    private Long totalRecords;

    @Column(name = "processed_records")
    private Long processedRecords;

    @Column(name = "failed_records")
    private Long failedRecords;

    @Column(name = "last_processed_id")
    private Long lastProcessedId;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static MigrationStatus create(String name, String phase) {
        MigrationStatus m = new MigrationStatus();
        m.setMigrationName(name);
        m.setMigrationPhase(phase);
        m.setStatus("PENDING");
        m.setProgressPercent(0);
        return m;
    }

    public void start() {
        this.status = "IN_PROGRESS";
        this.startedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = "COMPLETED";
        this.progressPercent = 100;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String error) {
        this.status = "FAILED";
        this.errorSummary = error;
        this.completedAt = LocalDateTime.now();
    }

    public void updateProgress(long processed, long failed, long lastId) {
        this.processedRecords = processed;
        this.failedRecords = failed;
        this.lastProcessedId = lastId;
        if (this.totalRecords != null && this.totalRecords > 0) {
            this.progressPercent = (int) ((processed * 100) / this.totalRecords);
        }
    }
}
