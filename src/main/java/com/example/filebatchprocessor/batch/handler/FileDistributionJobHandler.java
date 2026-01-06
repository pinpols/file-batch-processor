package com.example.filebatchprocessor.batch.handler;

import com.example.filebatchprocessor.service.FileDistributionService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件分发任务 Handler：处理待分发的文件任务、重传机制
 */
@Slf4j
@Component
public class FileDistributionJobHandler {

    private final FileDistributionService fileDistributionService;

    public FileDistributionJobHandler(FileDistributionService fileDistributionService) {
        this.fileDistributionService = fileDistributionService;
    }

    /**
     * 待分发任务处理：
     * 1. 查询状态为 PENDING 的分发任务
     * 2. 根据目标系统（SFTP/HTTP/FTP）执行分发
     * 3. 更新任务状态（PENDING -> IN_PROGRESS -> SUCCESS/RETRY/FAILED）
     */
    @XxlJob("fileDistributionJob")
    public void fileDistributionJob() {
        try {
            log.info("Starting file distribution job");
            
            // 查询待分发任务
            var pendingTasks = fileDistributionService.findPendingTasks();
            log.info("Found {} pending distribution tasks", pendingTasks.size());
            
            for (var task : pendingTasks) {
                try {
                    log.info("Processing distribution task: id={}, target={}", task.getId(), task.getTargetSystem());
                    
                    // 根据目标系统类型分发
                    if ("SFTP".equalsIgnoreCase(task.getTargetSystem())) {
                        // 示例：从任务配置中解析主机、端口、用户名、密码等
                        // fileDistributionService.distributeBySFTP(task.getId(), host, port, username, password, remoteDir);
                        log.info("SFTP distribution for task {} - implementation requires SFTP config", task.getId());
                    } else if ("HTTP".equalsIgnoreCase(task.getTargetSystem())) {
                        // fileDistributionService.distributeByHTTP(task.getId(), url, method);
                        log.info("HTTP distribution for task {} - not yet implemented", task.getId());
                    } else if ("FTP".equalsIgnoreCase(task.getTargetSystem())) {
                        // fileDistributionService.distributeByFTP(task.getId(), host, port, username, password, remoteDir);
                        log.info("FTP distribution for task {} - not yet implemented", task.getId());
                    } else {
                        log.warn("Unknown distribution target: {}", task.getTargetSystem());
                    }
                } catch (Exception e) {
                    log.error("Failed to process distribution task: id={}", task.getId(), e);
                }
            }
            
            log.info("File distribution job completed");
            XxlJobHelper.handleSuccess("File distribution job completed");
        } catch (Exception e) {
            log.error("File distribution job failed", e);
            XxlJobHelper.handleFail("File distribution job failed: " + e.getMessage());
        }
    }

    /**
     * 重传机制任务：
     * 查找需要重试的分发任务（状态为 RETRY）并重新分发
     * 参数: 重试间隔分钟数，默认 5 分钟
     */
    @XxlJob("fileDistributionRetryJob")
    public void fileDistributionRetryJob() {
        try {
            log.info("Starting file distribution retry job");
            
            // 默认查找 5 分钟内应该重试的任务
            int minutesInterval = 5;
            var retryTasks = fileDistributionService.findRetryableTasks(minutesInterval);
            log.info("Found {} retryable distribution tasks", retryTasks.size());
            
            for (var task : retryTasks) {
                try {
                    log.info("Retrying distribution task: id={}, retryCount={}/{}", 
                            task.getId(), task.getRetryCount(), task.getMaxRetries());
                    
                    fileDistributionService.retryFailedTask(task.getId());
                } catch (Exception e) {
                    log.error("Failed to retry distribution task: id={}", task.getId(), e);
                }
            }
            
            log.info("File distribution retry job completed");
            XxlJobHelper.handleSuccess("Retry job completed");
        } catch (Exception e) {
            log.error("File distribution retry job failed", e);
            XxlJobHelper.handleFail("Retry job failed: " + e.getMessage());
        }
    }

    /**
     * 超时任务处理：
     * 查找超过指定时间未完成的分发任务，标记为失败
     * 参数: 超时分钟数，默认 24 小时
     */
    @XxlJob("fileDistributionTimeoutJob")
    public void fileDistributionTimeoutJob() {
        try {
            log.info("Starting file distribution timeout job");
            
            // 默认 24 小时超时
            int timeoutMinutes = 24 * 60;
            var timeoutTasks = fileDistributionService.findTimeoutTasks(timeoutMinutes);
            log.info("Found {} timeout distribution tasks", timeoutTasks.size());
            
            for (var task : timeoutTasks) {
                try {
                    log.info("Marking timeout task as failed: id={}", task.getId());
                    fileDistributionService.markAsFailed(task.getId(), "Task timeout after " + timeoutMinutes + " minutes");
                } catch (Exception e) {
                    log.error("Failed to mark timeout task: id={}", task.getId(), e);
                }
            }
            
            log.info("File distribution timeout job completed");
            XxlJobHelper.handleSuccess("Timeout handling completed");
        } catch (Exception e) {
            log.error("File distribution timeout job failed", e);
            XxlJobHelper.handleFail("Timeout job failed: " + e.getMessage());
        }
    }
}
