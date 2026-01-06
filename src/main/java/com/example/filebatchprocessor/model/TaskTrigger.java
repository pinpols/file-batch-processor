package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务触发器表：存储任务的执行计划
 * 支持多种触发方式：CRON、FIXED_RATE、ONE_TIME
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "task_trigger")
public class TaskTrigger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的任务ID（外键关联 task_definition）
     */
    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    /**
     * 触发器类型：CRON、FIXED_RATE、ONE_TIME
     */
    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    /**
     * CRON 表达式（当 triggerType 为 CRON 时使用）
     * 示例："0 0 1 * * ?" 表示每天凌晨 1 点
     */
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    /**
     * 固定频率（毫秒，当 triggerType 为 FIXED_RATE 时使用）
     * 示例：600000 表示每 10 分钟执行一次
     */
    @Column(name = "fixed_rate_ms")
    private Long fixedRateMs;

    /**
     * 一次性执行时间（当 triggerType 为 ONE_TIME 时使用）
     */
    @Column(name = "one_time_at")
    private LocalDateTime oneTimeAt;

    /**
     * 是否启用该触发器
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
