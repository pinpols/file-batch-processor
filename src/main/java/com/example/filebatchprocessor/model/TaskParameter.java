package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务参数表：存储任务执行时所需的参数
 * 每个任务可以关联多个参数，以 key-value 形式存储
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "task_parameter")
public class TaskParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的任务ID（外键关联 task_definition）
     */
    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    /**
     * 参数名
     * 示例："batchDate", "outputDir", "format"
     */
    @Column(name = "param_name", nullable = false, length = 100)
    private String paramName;

    /**
     * 参数值
     * 示例："2025-01-01", "/export", "csv"
     */
    @Column(name = "param_value", length = 1000)
    private String paramValue;

    /**
     * 参数类型：STRING, INT, LONG, DOUBLE, BOOLEAN
     */
    @Column(name = "param_type", length = 50)
    private String paramType;

    /**
     * 参数描述
     */
    @Column(name = "description", length = 500)
    private String description;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
