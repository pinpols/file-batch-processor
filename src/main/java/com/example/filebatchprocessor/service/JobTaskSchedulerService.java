package com.example.filebatchprocessor.service;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
    private final JobInstanceService jobInstanceService;
    private final RetryCompensationService retryCompensationService;

    public JobTaskSchedulerService(
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            BatchJobResolver batchJobResolver,
            JobOperator jobOperator,
            TaskConfigService taskConfigService,
            JobInstanceService jobInstanceService,
            RetryCompensationService retryCompensationService) {
        this.jobLauncher = jobLauncher;
        this.batchJobResolver = batchJobResolver;
        this.jobOperator = jobOperator;
        this.taskConfigService = taskConfigService;
        this.jobInstanceService = jobInstanceService;
        this.retryCompensationService = retryCompensationService;
    }

    /**
     * 触发任务执行
     */
    public String triggerJob(String taskId, Map<String, Object> parameters, String triggeredBy) {
        Long businessJobInstanceId = null;
        try {
            if (taskId == null || taskId.isBlank()) {
                return "Failed to trigger job: taskId is required";
            }
            log.info("Triggering job: {} with parameters: {} by: {}", taskId, parameters, triggeredBy);
            String jobName = resolveJobName(taskId);
            Job job = batchJobResolver
                    .resolve(jobName)
                    .map(BatchJobResolver.ResolvedJob::job)
                    .orElseThrow(() -> new IllegalArgumentException("No job found for name " + jobName + ", available="
                            + batchJobResolver.describeAvailableJobs()));
            Map<String, Object> mergedParameters = mergeParameters(taskId, parameters);
            String batchDate = stringValue(mergedParameters.get("batchDate"));
            String batchNo = stringValue(mergedParameters.get("batchNo"));
            String runKey = "manual-" + taskId + "-" + System.nanoTime();
            var businessInstance = jobInstanceService.createTriggeredInstance(new JobInstanceService.CreateRequest(
                    taskId,
                    jobName,
                    "MANUAL",
                    triggeredBy,
                    batchDate,
                    batchNo,
                    runKey,
                    stringValue(mergedParameters.get("rerunId")) != null,
                    false,
                    true,
                    jobInstanceService.resolveRelatedFileId(mergedParameters),
                    mergedParameters));
            businessJobInstanceId = businessInstance.getId();
            JobParameters jobParameters = buildJobParameters(
                    mergedParameters, businessInstance.getId(), businessInstance.getJobInstanceNo(), triggeredBy);
            JobExecution execution = jobLauncher.run(job, jobParameters);
            return "Job triggered: taskId=" + taskId + ", jobName=" + jobName + ", executionId=" + execution.getId()
                    + ", status=" + execution.getStatus();
        } catch (Exception e) {
            log.error("Failed to trigger job: {}", taskId, e);
            jobInstanceService.markLaunchFailed(businessJobInstanceId, e.getMessage());
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
            long restartedExecutionId = retryCompensationService.restartExecution(
                    executionId, triggeredBy, "Manual retry from jobTaskSchedulerService");
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

    private Map<String, Object> mergeParameters(String taskId, Map<String, Object> rawParameters) {
        Map<String, Object> merged = new HashMap<>();
        try {
            merged.putAll(taskConfigService.getTaskParametersAsMap(taskId));
        } catch (Exception ex) {
            log.debug("No persisted task parameters loaded for taskId={}", taskId);
        }
        if (rawParameters != null) {
            merged.putAll(rawParameters);
        }
        return merged;
    }

    private JobParameters buildJobParameters(
            Map<String, Object> merged, Long businessJobInstanceId, String businessJobInstanceNo, String triggeredBy) {
        JobParametersBuilder builder = new JobParametersBuilder();

        merged.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID.equals(key)
                    || JobInstanceParameters.BUSINESS_JOB_INSTANCE_NO.equals(key)
                    || JobInstanceParameters.TRIGGER_SOURCE.equals(key)
                    || JobInstanceParameters.TRIGGERED_BY.equals(key)
                    || JobInstanceParameters.RELATED_FILE_ID.equals(key)) {
                return;
            }
            if (value instanceof Number number) {
                builder.addLong(key, number.longValue());
            } else {
                builder.addString(key, String.valueOf(value));
            }
        });
        builder.addLong(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID, businessJobInstanceId);
        builder.addString(JobInstanceParameters.BUSINESS_JOB_INSTANCE_NO, businessJobInstanceNo);
        builder.addString(JobInstanceParameters.TRIGGER_SOURCE, "MANUAL");
        if (triggeredBy != null && !triggeredBy.isBlank()) {
            builder.addString(JobInstanceParameters.TRIGGERED_BY, triggeredBy);
        }
        Long relatedFileId = jobInstanceService.resolveRelatedFileId(merged);
        if (relatedFileId != null) {
            builder.addLong(JobInstanceParameters.RELATED_FILE_ID, relatedFileId);
        }
        if (!merged.containsKey("run.id")) {
            builder.addLong("run.id", System.currentTimeMillis());
        }
        return builder.toJobParameters();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
