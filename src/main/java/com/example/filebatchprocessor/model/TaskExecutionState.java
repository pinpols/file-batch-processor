package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 任务执行状态机（持久化），用于依赖编排、补跑窗口和失败传播。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "task_execution_state")
public class TaskExecutionState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "batch_date", nullable = false, length = 32)
    private String batchDate;

    @Column(name = "rerun_id", nullable = false, length = 128)
    private String rerunId = "";

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "biz_domain", length = 64)
    private String bizDomain;

    @Column(nullable = false, length = 32)
    private String status;

    private Integer attempt = 0;
    private Integer maxAttempts = 1;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "window_end")
    private LocalDateTime windowEnd;

    @Column(length = 1000)
    private String lastError;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
