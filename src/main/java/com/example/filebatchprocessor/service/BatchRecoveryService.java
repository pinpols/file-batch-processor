package com.example.filebatchprocessor.service;

import org.springframework.stereotype.Service;

/**
 * 批量恢复服务：支持按 executionId 重启，以及按作业名重启最近一次失败执行。
 */
@Service
public class BatchRecoveryService {

    private final RetryCompensationService retryCompensationService;

    public BatchRecoveryService(RetryCompensationService retryCompensationService) {
        this.retryCompensationService = retryCompensationService;
    }

    public Long restartByExecutionId(long executionId) throws Exception {
        return restartByExecutionId(executionId, "SYSTEM", "Legacy recovery request");
    }

    public Long restartByExecutionId(long executionId, String operatorName, String reason) throws Exception {
        return retryCompensationService.restartExecution(executionId, operatorName, reason);
    }

    public Long restartLatestFailed(String jobName) throws Exception {
        return restartLatestFailed(jobName, "SYSTEM", "Legacy recovery request");
    }

    public Long restartLatestFailed(String jobName, String operatorName, String reason) throws Exception {
        return retryCompensationService.restartLatestFailed(jobName, operatorName, reason);
    }
}
