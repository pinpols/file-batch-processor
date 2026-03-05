package com.example.filebatchprocessor.batch.handler.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ImportJobExecutionService {

    private final JobLauncher jobLauncher;
    private final ThreadPoolTaskExecutor batchTaskExecutor;

    public ImportJobExecutionService(@Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
                                     ThreadPoolTaskExecutor batchTaskExecutor) {
        this.jobLauncher = jobLauncher;
        this.batchTaskExecutor = batchTaskExecutor;
    }

    public BatchStatus executeWithRetry(Job job,
                                        JobParameters params,
                                        int maxRetries,
                                        long backoffMs,
                                        long maxDurationMs,
                                        long timeoutMs) {
        int attempt = 0;
        Instant start = Instant.now();
        while (true) {
            attempt++;
            try {
                BatchStatus status = runWithTimeout(job, params, timeoutMs);
                if (status == BatchStatus.COMPLETED) {
                    return status;
                }
            } catch (Exception ex) {
                if (attempt <= maxRetries) {
                    // Expected retry path: keep logs concise to avoid noisy stack traces.
                    log.warn("Attempt {} failed: {}", attempt, ex.getMessage());
                } else {
                    log.error("Attempt {} failed: {}", attempt, ex.getMessage(), ex);
                }
            }
            if (attempt > maxRetries) {
                return BatchStatus.FAILED;
            }
            if (maxDurationMs > 0 && Duration.between(start, Instant.now()).toMillis() > maxDurationMs) {
                log.warn("Max duration exceeded, aborting retries");
                return BatchStatus.FAILED;
            }
            try {
                Thread.sleep(backoffMs * attempt);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return BatchStatus.FAILED;
            }
        }
    }

    private BatchStatus runWithTimeout(Job job, JobParameters params, long timeoutMs) throws Exception {
        if (timeoutMs <= 0) {
            JobExecution execution = jobLauncher.run(job, params);
            return execution.getStatus();
        }
        CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
            try {
                return jobLauncher.run(job, params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, batchTaskExecutor);
        try {
            JobExecution execution = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return execution.getStatus();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw (e.getCause() instanceof Exception) ? (Exception) e.getCause() : e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
