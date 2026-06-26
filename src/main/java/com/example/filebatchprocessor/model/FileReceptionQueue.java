package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 文件接收队列：用于接收、存储和等待处理的文件
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "file_reception_queue",
        indexes = {
            @Index(name = "idx_status", columnList = "status"),
            @Index(name = "idx_created_at", columnList = "created_at"),
            @Index(name = "uk_file_name_received", columnList = "file_name", unique = true)
        })
public class FileReceptionQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "status", length = 20)
    private String status = "RECEIVED";

    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "expected_process_time")
    private LocalDateTime expectedProcessTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "wait_reason", length = 500)
    private String waitReason;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "file_record_id")
    private Long fileRecordId;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
