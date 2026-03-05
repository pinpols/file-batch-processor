package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reconcile_run_records", indexes = {
        @Index(name = "idx_reconcile_run_created_at", columnList = "created_at"),
        @Index(name = "idx_reconcile_run_job_batch", columnList = "job_name,batch_date")
})
public class ReconcileRunRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    @Column(name = "batch_date", length = 20)
    private String batchDate;

    @Column(name = "input_file_name", length = 1024)
    private String inputFileName;

    @Column(name = "source_count", nullable = false)
    private Long sourceCount = 0L;

    @Column(name = "target_count", nullable = false)
    private Long targetCount = 0L;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "target_hash", length = 64)
    private String targetHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
