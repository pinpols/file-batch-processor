package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行记录实体
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "job_execution")
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务ID
     */
    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    /**
     * 任务名称
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /**
     * 执行状态
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * 开始时间
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 执行参数（JSON格式）
     */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    /**
     * 错误信息
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * 执行时长（毫秒）
     */
    @Column(name = "duration")
    private Long duration;

    /**
     * 触发者
     */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    /**
     * 退出码
     */
    @Column(name = "exit_code")
    private Integer exitCode;

    /**
     * 总读取数
     */
    @Column(name = "total_read")
    private Long totalRead;

    /**
     * 总处理数
     */
    @Column(name = "total_processed")
    private Long totalProcessed;

    /**
     * 总失败数
     */
    @Column(name = "total_failed")
    private Long totalFailed;

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
}
