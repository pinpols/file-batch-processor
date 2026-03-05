package com.example.filebatchprocessor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 批量恢复服务：支持按 executionId 重启，以及按作业名重启最近一次失败执行。
 */
@Slf4j
@Service
public class BatchRecoveryService {

    private final JobOperator jobOperator;
    private final JobExplorer jobExplorer;

    public BatchRecoveryService(JobOperator jobOperator, JobExplorer jobExplorer) {
        this.jobOperator = jobOperator;
        this.jobExplorer = jobExplorer;
    }

    public Long restartByExecutionId(long executionId) throws Exception {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        if (!(execution.getStatus() == BatchStatus.FAILED || execution.getStatus() == BatchStatus.STOPPED)) {
            throw new IllegalStateException("Execution is not restartable: " + execution.getStatus());
        }
        String failedSteps = execution.getStepExecutions().stream()
                .filter(step -> step.getStatus() == BatchStatus.FAILED || step.getStatus() == BatchStatus.STOPPED)
                .map(step -> step.getStepName() + ":" + step.getStatus())
                .collect(Collectors.joining(","));
        log.info("Restarting executionId={} failedSteps={}", executionId, failedSteps);
        long restartedId = jobOperator.restart(executionId);
        log.info("Restarted execution {} -> {}", executionId, restartedId);
        return restartedId;
    }

    public Long restartLatestFailed(String jobName) throws Exception {
        List<JobExecution> executions = jobExplorer.findRunningJobExecutions(jobName).stream().toList();
        if (!executions.isEmpty()) {
            throw new IllegalStateException("Job is currently running: " + jobName);
        }
        List<JobExecution> all = jobExplorer.getJobInstances(jobName, 0, 100).stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .filter(exec -> exec.getStatus() == BatchStatus.FAILED || exec.getStatus() == BatchStatus.STOPPED)
                .sorted(Comparator.comparing(JobExecution::getCreateTime).reversed())
                .toList();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("No failed/stopped execution found for job: " + jobName);
        }
        return restartByExecutionId(all.get(0).getId());
    }
}
