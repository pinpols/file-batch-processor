package com.example.filebatchprocessor.listener;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Collection;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Starting job: {}", jobExecution.getJobInstance().getJobName());
        log.info("Job parameters: {}", jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        Duration duration = Duration.between(
            jobExecution.getStartTime().toInstant(ZoneOffset.UTC),
            jobExecution.getEndTime() != null ? jobExecution.getEndTime().toInstant(ZoneOffset.UTC) : java.time.Instant.now()
        );

        log.info("Job [{}] completed with status: {}", jobName, status);
        log.info("Job [{}] execution time: {} seconds", jobName, duration.getSeconds());

        if (status == BatchStatus.COMPLETED) {
            validateDataQuality(jobExecution);
            logJobStatistics(jobExecution);
        } else if (status == BatchStatus.FAILED) {
            handleJobFailure(jobExecution);
        }

        logJobSummary(jobExecution);
    }

    private void validateDataQuality(JobExecution jobExecution) {
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        if (stepExecutions.isEmpty()) {
            log.warn("No step executions found for job: {}", jobExecution.getJobInstance().getJobName());
            return;
        }

        stepExecutions.forEach(step -> {
            log.info("Step [{}] - Read: {}, Write: {}, Skip: {}, Commit: {}",
                step.getStepName(),
                step.getReadCount(),
                step.getWriteCount(),
                step.getSkipCount(),
                step.getCommitCount()
            );
        });
    }

    private void logJobStatistics(JobExecution jobExecution) {
        jobExecution.getStepExecutions().forEach(step -> {
            log.info("Step [{}] statistics:", step.getStepName());
            log.info("  - Read count: {}", step.getReadCount());
            log.info("  - Write count: {}", step.getWriteCount());
            log.info("  - Skip count: {}", step.getSkipCount());
            log.info("  - Rollback count: {}", step.getRollbackCount());
            log.info("  - Commit count: {}", step.getCommitCount());
            log.info("  - Filter count: {}", step.getFilterCount());
            log.info("  - Process skip count: {}", step.getProcessSkipCount());
            log.info("  - Read skip count: {}", step.getReadSkipCount());
            log.info("  - Write skip count: {}", step.getWriteSkipCount());
        });
    }

    private void handleJobFailure(JobExecution jobExecution) {
        log.error("Job [{}] failed with status: {}",
            jobExecution.getJobInstance().getJobName(),
            jobExecution.getStatus()
        );

        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            log.error("Job [{}] failure exceptions:", jobExecution.getJobInstance().getJobName());
            jobExecution.getAllFailureExceptions().forEach(ex ->
                log.error("  - Exception: {}", ex.getMessage(), ex)
            );
        }

        jobExecution.getStepExecutions().stream()
            .filter(step -> step.getFailureExceptions() != null && !step.getFailureExceptions().isEmpty())
            .forEach(step -> {
                log.error("Step [{}] failure exceptions:", step.getStepName());
                step.getFailureExceptions().forEach(ex ->
                    log.error("  - Exception: {}", ex.getMessage(), ex)
                );
            });
    }

    private void logJobSummary(JobExecution jobExecution) {
        log.info("=== Job [{}] Summary ===", jobExecution.getJobInstance().getJobName());
        log.info("Status: {}", jobExecution.getStatus());
        log.info("Start Time: {}", jobExecution.getStartTime());
        log.info("End Time: {}", jobExecution.getEndTime());
        log.info("Exit Status: {}", jobExecution.getExitStatus().getExitCode());
        log.info("=======================");
    }
}