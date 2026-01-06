package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务定义表：存储 XXL-Job 任务的配置信息
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
     * 任务名称（XXL-Job 中的 jobName，如 processFileJob、partitionedImportJob）
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /**
     * 任务描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 优先级：HIGH, NORMAL, LOW
     */
    @Column(name = "priority", length = 20)
    private String priority;

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
