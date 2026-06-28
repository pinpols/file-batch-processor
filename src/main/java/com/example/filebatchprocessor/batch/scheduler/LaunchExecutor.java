package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.BatchJobResolver;
import com.example.filebatchprocessor.service.JobInstanceParameters;
import com.example.filebatchprocessor.service.JobInstanceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;

public class LaunchExecutor {
    private static final Logger log = LoggerFactory.getLogger(LaunchExecutor.class);
    private static final Set<String> RESERVED_PARAMETERS = Set.of(
            "execution.id",
            "time",
            "task.id",
            "priority",
            "shard.index",
            "shard.total",
            JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID,
            JobInstanceParameters.BUSINESS_JOB_INSTANCE_NO,
            JobInstanceParameters.TRIGGER_SOURCE,
            JobInstanceParameters.TRIGGERED_BY,
            JobInstanceParameters.RELATED_FILE_ID);

    private final JobOperator jobOperator;
    private final BatchJobResolver jobResolver;
    private final JobInstanceService jobInstanceService;
    private final Semaphore launchPermits;
    private final Map<String, Semaphore> jobLaunchLocks = new ConcurrentHashMap<>();
    private final int defaultDynamicShardMax;
    private final long defaultLaunchWarnThresholdMs;

    public LaunchExecutor(
            JobOperator jobOperator,
            BatchJobResolver jobResolver,
            JobInstanceService jobInstanceService,
            Semaphore launchPermits,
            int defaultDynamicShardMax,
            long defaultLaunchWarnThresholdMs) {
        this.jobOperator = jobOperator;
        this.jobResolver = jobResolver;
        this.jobInstanceService = jobInstanceService;
        this.launchPermits = launchPermits;
        this.defaultDynamicShardMax = Math.max(1, defaultDynamicShardMax);
        this.defaultLaunchWarnThresholdMs = Math.max(1000, defaultLaunchWarnThresholdMs);
    }

    public LaunchResult launch(OrchestrationTaskDefinition def, String batchDate, int queueSize) {
        BatchJobResolver.ResolvedJob resolvedJob =
                jobResolver.resolve(def.getJobName()).orElse(null);
        if (resolvedJob == null) {
            log.warn(
                    "No job found for taskId={} jobName={} available={}",
                    def.getId(),
                    def.getJobName(),
                    jobResolver.describeAvailableJobs());
            return LaunchResult.failed("No job found for name " + def.getJobName());
        }
        Job job = resolvedJob.job();
        Semaphore jobLaunchLock = jobLaunchLocks.computeIfAbsent(def.getJobName(), _k -> new Semaphore(1));

        Map<String, String> taskParameters = snapshotTaskParameters(def);
        String rerunId = taskParameters.getOrDefault("rerunId", "");
        long launchWarnThresholdMs = resolveLaunchWarnThresholdMs(def);
        int shardTotal = resolveShardTotal(def, queueSize);

        List<Integer> shardIndexes = new ArrayList<>();
        if (def.getShardIndex() != null) {
            shardIndexes.add(def.getShardIndex());
        } else {
            for (int i = 0; i < shardTotal; i++) {
                shardIndexes.add(i);
            }
        }

        int successShards = 0;
        int failedShards = 0;
        Throwable lastFailureCause = null;
        for (Integer shardIndex : shardIndexes) {
            String executionId = def.getId()
                    + "-"
                    + batchDate
                    + (rerunId.isEmpty() ? "" : "-" + rerunId)
                    + "-s"
                    + shardIndex
                    + "-of"
                    + shardTotal
                    + "-"
                    + System.nanoTime();

            boolean permitAcquired = false;
            boolean jobLockAcquired = false;
            Long jobInstanceId = null;
            try {
                permitAcquired = launchPermits.tryAcquire(1, TimeUnit.SECONDS);
                if (!permitAcquired) {
                    return LaunchResult.reschedule();
                }
                jobLockAcquired = jobLaunchLock.tryAcquire(1, TimeUnit.SECONDS);
                if (!jobLockAcquired) {
                    return LaunchResult.reschedule();
                }
                var businessInstance = jobInstanceService.createTriggeredInstance(new JobInstanceService.CreateRequest(
                        def.getId(),
                        def.getJobName(),
                        "SCHEDULER",
                        null,
                        batchDate,
                        taskParameters.get("batchNo"),
                        executionId,
                        !rerunId.isBlank(),
                        false,
                        false,
                        jobInstanceService.resolveRelatedFileId(taskParameters),
                        buildRequestPayload(taskParameters, executionId, shardIndex, shardTotal)));
                jobInstanceId = businessInstance.getId();
                JobParameters parameters = buildJobParameters(
                        def,
                        taskParameters,
                        batchDate,
                        executionId,
                        shardIndex,
                        shardTotal,
                        businessInstance.getId(),
                        businessInstance.getJobInstanceNo());
                JobExecution execution = runAndWarnIfSlow(job, parameters, launchWarnThresholdMs);
                if (execution != null && execution.getStatus() == BatchStatus.FAILED) {
                    failedShards++;
                } else {
                    successShards++;
                }
            } catch (Exception e) {
                lastFailureCause = e;
                log.warn(
                        "Launch failed: taskId={} jobName={} shardIndex={} reason={}",
                        def.getId(),
                        def.getJobName(),
                        shardIndex,
                        e.toString(),
                        e);
                if (jobInstanceId != null) {
                    jobInstanceService.markLaunchFailed(jobInstanceId, e.getMessage());
                }
                failedShards++;
            } finally {
                if (jobLockAcquired) {
                    jobLaunchLock.release();
                }
                if (permitAcquired) {
                    launchPermits.release();
                }
            }
        }

        if (failedShards == 0) {
            return LaunchResult.success();
        }
        if (successShards > 0) {
            return LaunchResult.partial("Partial success, failed shards=" + failedShards, lastFailureCause);
        }
        return LaunchResult.failed("All shards failed", lastFailureCause);
    }

    private int resolveShardTotal(OrchestrationTaskDefinition def, int queueSize) {
        if (def.getShardTotal() != null && def.getShardTotal() > 0) {
            return def.getShardTotal();
        }
        if (!def.isAllowParallel()) {
            return 1;
        }
        int dynamicMax =
                def.getDynamicShardMax() == null ? defaultDynamicShardMax : Math.max(1, def.getDynamicShardMax());
        if (dynamicMax <= 1) {
            return 1;
        }
        int loadBased = Math.max(1, queueSize / 20 + 1);
        return Math.min(dynamicMax, loadBased);
    }

    private long resolveLaunchWarnThresholdMs(OrchestrationTaskDefinition def) {
        if (def.getTimeoutMs() != null && def.getTimeoutMs() > 0) {
            return def.getTimeoutMs();
        }
        return defaultLaunchWarnThresholdMs;
    }

    private JobExecution runAndWarnIfSlow(Job job, JobParameters parameters, long launchWarnThresholdMs)
            throws Exception {
        long startedAtMs = System.currentTimeMillis();
        JobExecution execution = jobOperator.start(job, parameters);
        if (launchWarnThresholdMs > 0) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            if (elapsedMs > launchWarnThresholdMs) {
                log.warn(
                        "Job launcher call exceeded slow-launch warning threshold: jobName={} elapsedMs={}"
                                + " thresholdMs={}",
                        job.getName(),
                        elapsedMs,
                        launchWarnThresholdMs);
            }
        }
        return execution;
    }

    private Map<String, String> snapshotTaskParameters(OrchestrationTaskDefinition def) {
        if (def.getParameters() == null || def.getParameters().isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(def.getParameters());
    }

    private JobParameters buildJobParameters(
            OrchestrationTaskDefinition def,
            Map<String, String> taskParameters,
            String batchDate,
            String executionId,
            int shardIndex,
            int shardTotal,
            Long businessJobInstanceId,
            String businessJobInstanceNo) {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addString("execution.id", executionId)
                .addLong("time", System.currentTimeMillis())
                .addString("task.id", def.getId())
                .addLong("priority", (long) def.getPriority().weight())
                .addLong("shard.index", (long) shardIndex)
                .addLong("shard.total", (long) shardTotal)
                .addLong(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID, businessJobInstanceId)
                .addString(JobInstanceParameters.BUSINESS_JOB_INSTANCE_NO, businessJobInstanceNo)
                .addString(JobInstanceParameters.TRIGGER_SOURCE, "SCHEDULER");
        taskParameters.forEach((k, v) -> {
            if (k == null || RESERVED_PARAMETERS.contains(k) || v == null) {
                return;
            }
            builder.addString(k, v);
        });
        Long relatedFileId = jobInstanceService.resolveRelatedFileId(taskParameters);
        if (relatedFileId != null) {
            builder.addLong(JobInstanceParameters.RELATED_FILE_ID, relatedFileId);
        }
        String configuredBatchDate = taskParameters.get("batchDate");
        if (configuredBatchDate == null || configuredBatchDate.isBlank()) {
            builder.addString("batchDate", batchDate);
        }
        return builder.toJobParameters();
    }

    private Map<String, Object> buildRequestPayload(
            Map<String, String> taskParameters, String executionId, int shardIndex, int shardTotal) {
        Map<String, Object> payload = new HashMap<>(taskParameters);
        payload.put("execution.id", executionId);
        payload.put("shard.index", shardIndex);
        payload.put("shard.total", shardTotal);
        return payload;
    }

    @Getter
    @Builder
    public static class LaunchResult {
        private final boolean success;
        private final boolean partial;
        private final boolean shouldReschedule;
        private final String reason;

        /** catch 点捕获的原始异常,透传到调度决策点用于分类;无异常时为 null。 */
        private final Throwable failureCause;

        static LaunchResult success() {
            return LaunchResult.builder()
                    .success(true)
                    .partial(false)
                    .shouldReschedule(false)
                    .reason(null)
                    .build();
        }

        static LaunchResult partial(String reason) {
            return partial(reason, null);
        }

        static LaunchResult partial(String reason, Throwable failureCause) {
            return LaunchResult.builder()
                    .success(true)
                    .partial(true)
                    .shouldReschedule(false)
                    .reason(reason)
                    .failureCause(failureCause)
                    .build();
        }

        static LaunchResult reschedule() {
            return LaunchResult.builder()
                    .success(false)
                    .partial(false)
                    .shouldReschedule(true)
                    .reason("Launch permit unavailable")
                    .build();
        }

        static LaunchResult failed(String reason) {
            return failed(reason, null);
        }

        static LaunchResult failed(String reason, Throwable failureCause) {
            return LaunchResult.builder()
                    .success(false)
                    .partial(false)
                    .shouldReschedule(false)
                    .reason(reason)
                    .failureCause(failureCause)
                    .build();
        }
    }
}
