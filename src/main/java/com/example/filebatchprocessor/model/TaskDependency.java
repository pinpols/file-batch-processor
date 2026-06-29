package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务依赖关系表：
 * 一条记录表示「taskId 依赖 dependsOnTaskId」。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "task_dependency")
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务自身 ID（task_definition.task_id）
     */
    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    /**
     * 被依赖的任务 ID（task_definition.task_id）
     */
    @Column(name = "depends_on_task_id", nullable = false, length = 100)
    private String dependsOnTaskId;

    /**
     * Optional per-dependency timeout in milliseconds.
     */
    @Column(name = "dependency_timeout_ms")
    private Long dependencyTimeoutMs;

    /**
     * 相对当前 batchDate 的依赖账期偏移；0 表示同日，-1 表示依赖 T-1。
     */
    @Column(name = "dependency_batch_date_offset_days")
    private Integer dependencyBatchDateOffsetDays = 0;

    /**
     * 预留日历编码，用于文档和配置表达；当前单体实现按自然日偏移解析。
     */
    @Column(name = "dependency_calendar_code", length = 64)
    private String dependencyCalendarCode;

    /**
     * On dependency failed behavior: FAIL / SKIP / IGNORE.
     */
    @Column(name = "on_failure_action", length = 16)
    private String onFailureAction = "FAIL";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
