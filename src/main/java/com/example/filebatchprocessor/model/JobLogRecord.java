package com.example.filebatchprocessor.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 批量作业执行日志（记录到数据库，用于审计与排查）
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "job_log_records")
public class JobLogRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 作业名称，如 processFileJob / dataExportJob */
    private String jobName;

    /** XXL 下发或编排传入的原始参数 */
    @Column(length = 2000)
    private String params;

    /** 执行最终状态：COMPLETED/FAILED 等 */
    private String status;

    /** 简要信息或错误原因 */
    @Column(length = 1000)
    private String message;

    /** 作业开始时间 */
    private LocalDateTime startTime;

    /** 作业结束时间 */
    private LocalDateTime endTime;

    /** 记录创建时间 */
    private LocalDateTime createdAt = LocalDateTime.now();
}


