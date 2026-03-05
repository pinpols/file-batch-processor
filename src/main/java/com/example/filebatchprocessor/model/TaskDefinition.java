package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务定义表：存储任务配置信息
 * 字段包括：任务ID、任务名、优先级、是否允许并行执行、去重键等
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "task_definition")
public class TaskDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务唯一标识符
     */
    @Column(name = "task_id", nullable = false, unique = true, length = 100)
    private String taskId;

    /**
     * 任务名称（如 processFileJob、partitionedImportJob）
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /**
     * 任务描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 所属租户标识（可选）
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /**
     * 业务域（如账务、营销、风控等）
     */
    @Column(name = "biz_domain", length = 64)
    private String bizDomain;

    /**
     * 环境标签（如 dev/test/stage/prod）
     */
    @Column(name = "env", length = 32)
    private String env;

    /**
     * 优先级：HIGH, NORMAL, LOW
     */
    @Column(name = "priority", length = 20)
    private String priority;

    /**
     * 单次执行期望的最大时长（毫秒），用于 SLA 评估
     */
    @Column(name = "sla_max_duration_ms")
    private Long slaMaxDurationMs;

    /**
     * 任务在队列中允许的最大等待时间（毫秒），超过视为 SLA 违约
     */
    @Column(name = "sla_max_queue_delay_ms")
    private Long slaMaxQueueDelayMs;

    /**
     * 每分钟允许的最大启动次数（简单速率限制配置）
     */
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    @Column(name = "timeout_ms")
    private Long timeoutMs;

    @Column(name = "max_queue_wait_ms")
    private Long maxQueueWaitMs;

    @Column(name = "dependency_timeout_ms")
    private Long dependencyTimeoutMs;

    @Column(name = "rerun_window_ms")
    private Long rerunWindowMs;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "retry_backoff_ms")
    private Long retryBackoffMs;

    @Column(name = "dynamic_shard_max")
    private Integer dynamicShardMax;

    /**
     * 是否允许并行执行
     */
    @Column(name = "allow_parallel")
    private Boolean allowParallel;

    /**
     * 去重键：用于防止重复执行（仅在同一去重键下只执行一个任务）
     */
    @Column(name = "dedup_key", length = 100)
    private String dedupKey;

    /**
     * 是否启用该任务
     */
    @Column(name = "enabled")
    private Boolean enabled;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
