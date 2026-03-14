package com.example.filebatchprocessor.service;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务调度服务
 */
@Service("jobTaskSchedulerService")
@Slf4j
public class JobTaskSchedulerService {

    private final JobLauncher jobLauncher;
    private final BatchJobResolver batchJobResolver;
    private final JobOperator jobOperator;
    private final TaskConfigService taskConfigService;

    public JobTaskSchedulerService(@Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
                                   BatchJobResolver batchJobResolver,
                                   JobOperator jobOperator,
                                   TaskConfigService taskConfigService) {
        this.jobLauncher = jobLauncher;
        this.batchJobResolver = batchJobResolver;
        this.jobOperator = jobOperator;
        this.taskConfigService = taskConfigService;
    }

    /**
     * 触发任务执行
     */
    public String triggerJob(String taskId, Map<String, Object> parameters, String triggeredBy) {
        try {
            if (taskId == null || taskId.isBlank()) {
                return "Failed to trigger job: taskId is required";
            }
            log.info("Triggering job: {} with parameters: {} by: {}", taskId, parameters, triggeredBy);
            String jobName = resolveJobName(taskId);
            Job job = batchJobResolver.resolve(jobName)
                    .map(BatchJobResolver.ResolvedJob::job)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No job found for name " + jobName + ", available=" + batchJobResolver.describeAvailableJobs()));
            JobParameters jobParameters = buildJobParameters(taskId, parameters);
            JobExecution execution = jobLauncher.run(job, jobParameters);
            return "Job triggered: taskId=" + taskId + ", jobName=" + jobName + ", executionId=" + execution.getId()
                    + ", status=" + execution.getStatus();
        } catch (Exception e) {
            log.error("Failed to trigger job: {}", taskId, e);
            return "Failed to trigger job: " + taskId + ", reason=" + e.getMessage();
        }
    }

    /**
     * 重试任务执行
     */
    public String retryJobExecution(Long executionId, String triggeredBy) {
        try {
            if (executionId == null || executionId <= 0) {
                return "Failed to retry job execution: executionId is required";
            }
            log.info("Retrying job execution: {} by: {}", executionId, triggeredBy);
            long restartedExecutionId = jobOperator.restart(executionId);
            return "Job retried: executionId=" + executionId + ", restartedExecutionId=" + restartedExecutionId;
        } catch (Exception e) {
            log.error("Failed to retry job execution: {}", executionId, e);
            return "Failed to retry job execution: " + executionId + ", reason=" + e.getMessage();
        }
    }

    /**
     * 停止任务执行
     */
    public void stopJobExecution(Long executionId, String triggeredBy) {
        try {
            if (executionId == null || executionId <= 0) {
                log.warn("Ignore stop request: invalid executionId={}", executionId);
                return;
            }
            log.info("Stopping job execution: {} by: {}", executionId, triggeredBy);
            jobOperator.stop(executionId);
        } catch (Exception e) {
            log.error("Failed to stop job execution: {}", executionId, e);
        }
    }

    private String resolveJobName(String taskId) {
        try {
            return taskConfigService.getTaskDefinition(taskId).getJobName();
        } catch (Exception ex) {
            log.warn("Task definition not found for taskId={}, fallback to taskId as jobName", taskId);
            return taskId;
        }
    }

    private JobParameters buildJobParameters(String taskId, Map<String, Object> rawParameters) {
        JobParametersBuilder builder = new JobParametersBuilder();
        Map<String, Object> merged = new HashMap<>();
        try {
            merged.putAll(taskConfigService.getTaskParametersAsMap(taskId));
        } catch (Exception ex) {
            log.debug("No persisted task parameters loaded for taskId={}", taskId);
        }
        if (rawParameters != null) {
            merged.putAll(rawParameters);
        }

        merged.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null) {
                    return;
                }
                if (value instanceof Number number) {
                    builder.addLong(key, number.longValue());
                } else {
                    builder.addString(key, String.valueOf(value));
                }
            });
        if (!merged.containsKey("run.id")) {
            builder.addLong("run.id", System.currentTimeMillis());
        }
        return builder.toJobParameters();
    }
}
