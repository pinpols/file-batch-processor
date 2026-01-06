package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 文件分发任务：管理文件发送到目标系统的任务
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file_distribution_task", indexes = {
        @Index(name = "idx_status_dist", columnList = "status"),
        @Index(name = "idx_target_system", columnList = "target_system"),
        @Index(name = "idx_created_at_dist", columnList = "created_at")
})
public class FileDistributionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "export_file_id")
    private Long exportFileId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    /**
     * 目标系统：SFTP, HTTP, FTP, EMAIL 等
     */
    @Column(name = "target_system", nullable = false, length = 100)
    private String targetSystem;

    /**
     * 目标地址：IP、URL 或邮箱
     */
    @Column(name = "target_address", length = 500)
    private String targetAddress;

    /**
     * 任务状态：PENDING, IN_PROGRESS, SUCCESS, FAILED, RETRY
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    /**
     * 重试策略：
     * - 重试次数
     * - 重试间隔
     */
    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "retry_interval_seconds")
    private Long retryIntervalSeconds = 300L; // 5分钟

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 最后一次尝试时间
     */
    @Column(name = "last_attempt_time")
    private LocalDateTime lastAttemptTime;

    /**
     * 完成时间
     */
    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    /**
     * 错误信息
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
