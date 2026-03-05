package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import lombok.Builder;
import lombok.Getter;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class LaunchExecutor {

    private final JobLauncher jobLauncher;
    private final ObjectProvider<Map<String, Job>> jobsProvider;
    private final ThreadPoolTaskExecutor batchTaskExecutor;
    private final Semaphore launchPermits;
    private final int defaultDynamicShardMax;
    private final long defaultTimeoutMs;

    LaunchExecutor(JobLauncher jobLauncher,
                   ObjectProvider<Map<String, Job>> jobsProvider,
                   ThreadPoolTaskExecutor batchTaskExecutor,
                   Semaphore launchPermits,
                   int defaultDynamicShardMax,
                   long defaultTimeoutMs) {
        this.jobLauncher = jobLauncher;
        this.jobsProvider = jobsProvider;
        this.batchTaskExecutor = batchTaskExecutor;
        this.launchPermits = launchPermits;
        this.defaultDynamicShardMax = Math.max(1, defaultDynamicShardMax);
        this.defaultTimeoutMs = Math.max(1000, defaultTimeoutMs);
    }

    LaunchResult launch(OrchestrationTaskDefinition def, String batchDate, int queueSize) {
        Map<String, Job> jobs = jobsProvider.getIfAvailable();
        if (jobs == null || !jobs.containsKey(def.getJobName())) {
            return LaunchResult.failed("No job found for name " + def.getJobName());
        }
        Job job = jobs.get(def.getJobName());

        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        long timeoutMs = resolveTimeoutMs(def);
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
        for (Integer shardIndex : shardIndexes) {
            String executionId = def.getId() + "-" + batchDate
                    + (rerunId.isEmpty() ? "" : "-" + rerunId)
                    + "-s" + shardIndex
                    + "-of" + shardTotal
                    + "-" + System.nanoTime();
            JobParametersBuilder builder = new JobParametersBuilder()
                    .addString("execution.id", executionId)
                    .addLong("time", System.currentTimeMillis())
                    .addString("task.id", def.getId())
                    .addLong("priority", (long) def.getPriority().weight())
                    .addLong("shard.index", shardIndex.longValue())
                    .addLong("shard.total", (long) shardTotal);
            def.getParameters().forEach(builder::addString);
            if (!def.getParameters().containsKey("batchDate") || def.getParameters().get("batchDate").isBlank()) {
                builder.addString("batchDate", batchDate);
            }

            try {
                if (!launchPermits.tryAcquire(1, TimeUnit.SECONDS)) {
                    return LaunchResult.reschedule();
                }
                JobExecution execution = runWithTimeout(job, builder, timeoutMs);
                if (execution != null && execution.getStatus() == BatchStatus.FAILED) {
                    failedShards++;
                } else {
                    successShards++;
                }
            } catch (Exception e) {
                failedShards++;
            } finally {
                launchPermits.release();
            }
        }

        if (failedShards == 0) {
            return LaunchResult.success();
        }
        if (successShards > 0) {
            return LaunchResult.partial("Partial success, failed shards=" + failedShards);
        }
        return LaunchResult.failed("All shards failed");
    }

    private int resolveShardTotal(OrchestrationTaskDefinition def, int queueSize) {
        if (def.getShardTotal() != null && def.getShardTotal() > 0) {
            return def.getShardTotal();
        }
        if (!def.isAllowParallel()) {
            return 1;
        }
        int dynamicMax = def.getDynamicShardMax() == null ? defaultDynamicShardMax : Math.max(1, def.getDynamicShardMax());
        if (dynamicMax <= 1) {
            return 1;
        }
        int loadBased = Math.max(1, queueSize / 20 + 1);
        return Math.min(dynamicMax, loadBased);
    }

    private long resolveTimeoutMs(OrchestrationTaskDefinition def) {
        if (def.getTimeoutMs() != null && def.getTimeoutMs() > 0) {
            return def.getTimeoutMs();
        }
        return defaultTimeoutMs;
    }

    private JobExecution runWithTimeout(Job job, JobParametersBuilder builder, long timeoutMs) throws Exception {
        if (timeoutMs <= 0) {
            return jobLauncher.run(job, builder.toJobParameters());
        }
        CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
            try {
                return jobLauncher.run(job, builder.toJobParameters());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, batchTaskExecutor);
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Getter
    @Builder
    static class LaunchResult {
        private final boolean success;
        private final boolean partial;
        private final boolean shouldReschedule;
        private final String reason;

        static LaunchResult success() {
            return LaunchResult.builder().success(true).partial(false).shouldReschedule(false).reason(null).build();
        }

        static LaunchResult partial(String reason) {
            return LaunchResult.builder().success(true).partial(true).shouldReschedule(false).reason(reason).build();
        }

        static LaunchResult reschedule() {
            return LaunchResult.builder().success(false).partial(false).shouldReschedule(true).reason("Launch permit unavailable").build();
        }

        static LaunchResult failed(String reason) {
            return LaunchResult.builder().success(false).partial(false).shouldReschedule(false).reason(reason).build();
        }
    }
}
