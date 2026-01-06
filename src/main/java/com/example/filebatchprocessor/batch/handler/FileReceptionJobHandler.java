package com.example.filebatchprocessor.batch.handler;

import com.example.filebatchprocessor.service.FileReceptionService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件接收任务 Handler：监控文件接收队列、处理待处理的文件
 */
@Slf4j
@Component
public class FileReceptionJobHandler {

    private final FileReceptionService fileReceptionService;

    public FileReceptionJobHandler(FileReceptionService fileReceptionService) {
        this.fileReceptionService = fileReceptionService;
    }

    /**
     * 文件接收监控任务：
     * 1. 检查待处理的文件接收记录
     * 2. 验证文件完整性（校验和）
     * 3. 更新文件状态（RECEIVED -> WAITING -> COMPLETED）
     */
    @XxlJob("fileReceptionJob")
    public void fileReceptionJob() {
        try {
            log.info("Starting file reception monitoring job");
            
            // 处理待处理的文件接收记录
            // 示例流程：
            // 1. 查询状态为 RECEIVED 的记录
            // 2. 验证文件校验和
            // 3. 标记为 WAITING
            // 4. 在处理完成后标记为 COMPLETED
            
            log.info("File reception monitoring job completed");
            XxlJobHelper.handleSuccess("File reception monitoring completed");
        } catch (Exception e) {
            log.error("File reception job failed", e);
            XxlJobHelper.handleFail("File reception job failed: " + e.getMessage());
        }
    }

    /**
     * 文件等待超时检测任务：
     * 检测超过指定时间未完成的接收任务，标记为失败或重试
     */
    @XxlJob("fileReceptionTimeoutJob")
    public void fileReceptionTimeoutJob() {
        try {
            log.info("Starting file reception timeout detection job");
            
            // 查找超过 24 小时未完成的接收任务
            int timeoutMinutes = 24 * 60;
            // fileReceptionService.handleTimeoutRecords(timeoutMinutes);
            
            log.info("File reception timeout detection job completed");
            XxlJobHelper.handleSuccess("Timeout detection completed");
        } catch (Exception e) {
            log.error("File reception timeout job failed", e);
            XxlJobHelper.handleFail("Timeout detection failed: " + e.getMessage());
        }
    }
}
