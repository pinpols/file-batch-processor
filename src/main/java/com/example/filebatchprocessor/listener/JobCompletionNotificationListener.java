package com.example.filebatchprocessor.listener;


import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.model.QualityGateResult;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.QualityGateResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);
    private final BatchRunRecordRepository batchRunRecordRepository;
    private final ImportedRecordRepository importedRecordRepository;
    private final QualityGateResultRepository qualityGateResultRepository;

    public JobCompletionNotificationListener(BatchRunRecordRepository batchRunRecordRepository,
                                             ImportedRecordRepository importedRecordRepository,
                                             QualityGateResultRepository qualityGateResultRepository) {
        this.batchRunRecordRepository = batchRunRecordRepository;
        this.importedRecordRepository = importedRecordRepository;
        this.qualityGateResultRepository = qualityGateResultRepository;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Starting job: {}", jobExecution.getJobInstance().getJobName());
        log.info("Job parameters: {}", jobExecution.getJobParameters());
        upsertBatchRun(jobExecution, "RUNNING");
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
            evaluatePostImportQuality(jobExecution);
        } else if (status == BatchStatus.FAILED) {
            handleJobFailure(jobExecution);
        }

        logJobSummary(jobExecution);
        upsertBatchRun(jobExecution, status.name());
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

    private void evaluatePostImportQuality(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        if (!"importJob".equals(jobName)) {
            return;
        }
        String batchDate = null;
        if (jobExecution.getJobParameters() != null) {
            batchDate = jobExecution.getJobParameters().getString("batchDate");
            if (batchDate == null || batchDate.isBlank()) {
                batchDate = jobExecution.getJobParameters().getString("batch.date");
            }
        }
        if (batchDate == null || batchDate.isBlank()) {
            return;
        }
        try {
            long total = importedRecordRepository.countByBatchDate(batchDate);
            long missingName = importedRecordRepository.countMissingNameByBatchDate(batchDate);
            double missingRate = total == 0 ? 0.0 : (double) missingName / (double) total;

            persistQualityGate(jobExecution, "IMPORT_REQUIRED_FIELD_COMPLETENESS", batchDate, total, missingName,
                    missingRate, 0.01, total, missingRate <= 0.01 ? "PASS" : "FAIL",
                    "missing-name-rate=" + missingRate);
        } catch (Exception ex) {
            log.warn("Failed to evaluate post-import quality gate for executionId={}", jobExecution.getId(), ex);
        }
    }

    private void persistQualityGate(JobExecution jobExecution,
                                    String gateType,
                                    String batchDate,
                                    long totalCount,
                                    long issueCount,
                                    double errorRate,
                                    double maxRate,
                                    long minLines,
                                    String status,
                                    String message) {
        QualityGateResult result = new QualityGateResult();
        result.setGateType(gateType);
        result.setJobName(jobExecution.getJobInstance().getJobName());
        result.setStepName("post-job");
        result.setBatchDate(batchDate);
        result.setJobExecutionId(jobExecution.getId());
        result.setStepExecutionId(null);
        result.setReadCount(totalCount);
        result.setParseErrorCount(issueCount);
        result.setTotalCount(totalCount);
        result.setErrorRate(errorRate);
        result.setMaxRate(maxRate);
        result.setMinLines(minLines);
        result.setStatus(status);
        result.setMessage(message);
        qualityGateResultRepository.save(result);
    }

    private void upsertBatchRun(JobExecution jobExecution, String status) {
        try {
            BatchRunRecord record = batchRunRecordRepository
                    .findByJobExecutionId(jobExecution.getId())
                    .orElseGet(BatchRunRecord::new);

            long readCount = jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getReadCount).sum();
            long writeCount = jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
            long skipCount = jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getSkipCount).sum();
            long filterCount = jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getFilterCount).sum();
            long rollbackCount = jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getRollbackCount).sum();
            long commitCount = jobExecution.getStepExecutions().stream().mapToLong(StepExecution::getCommitCount).sum();
            long parseErrorCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(step -> step.getExecutionContext().getLong("parse.error.count", 0L))
                    .sum();
            boolean qualityPassed = readCount == (writeCount + skipCount + filterCount) && parseErrorCount == 0;
            String finalStatus = ("COMPLETED".equals(status) && !qualityPassed) ? "PARTIAL" : status;
            String qualityMessage = qualityPassed
                    ? "OK"
                    : String.format("quality gate failed: read=%d write=%d skip=%d filter=%d parseErrors=%d",
                    readCount, writeCount, skipCount, filterCount, parseErrorCount);

            Instant start = jobExecution.getStartTime() != null
                    ? jobExecution.getStartTime().toInstant(ZoneOffset.UTC)
                    : Instant.now();
            Instant end = jobExecution.getEndTime() != null
                    ? jobExecution.getEndTime().toInstant(ZoneOffset.UTC)
                    : Instant.now();
            long durationMs = Math.max(Duration.between(start, end).toMillis(), 1L);
            double throughput = (writeCount * 1000.0) / durationMs;

            record.setJobExecutionId(jobExecution.getId());
            record.setJobName(jobExecution.getJobInstance().getJobName());
            record.setJobParams(jobExecution.getJobParameters().toString());
            record.setStatus(finalStatus);
            record.setReadCount(readCount);
            record.setWriteCount(writeCount);
            record.setSkipCount(skipCount);
            record.setParseErrorCount(parseErrorCount);
            record.setRollbackCount(rollbackCount);
            record.setCommitCount(commitCount);
            record.setRetryCount(rollbackCount);
            record.setDurationMs(durationMs);
            record.setThroughputRps(throughput);
            record.setQualityPassed(qualityPassed);
            record.setQualityMessage(qualityMessage);
            record.setStartTime(jobExecution.getStartTime());
            record.setEndTime(jobExecution.getEndTime());
            record.setUpdatedAt(LocalDateTime.now());

            if (jobExecution.getStatus() == BatchStatus.FAILED && !jobExecution.getAllFailureExceptions().isEmpty()) {
                String message = jobExecution.getAllFailureExceptions().get(0).getMessage();
                record.setErrorMessage(message != null && message.length() > 1000 ? message.substring(0, 1000) : message);
            } else {
                record.setErrorMessage(null);
            }

            if (!qualityPassed) {
                log.warn("Data-quality gate not passed for executionId={}: read={}, write={}, skip={}, filter={}",
                        jobExecution.getId(), readCount, writeCount, skipCount, filterCount);
            }

            batchRunRecordRepository.save(record);
        } catch (Exception ex) {
            log.error("Failed to persist batch run audit record for executionId={}", jobExecution.getId(), ex);
        }
    }
}
