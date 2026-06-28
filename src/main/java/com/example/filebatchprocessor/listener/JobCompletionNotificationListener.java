package com.example.filebatchprocessor.listener;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.model.QualityGateResult;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.QualityGateResultRepository;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.FileProcessLogService;
import com.example.filebatchprocessor.service.JobInstanceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);
    private final BatchRunRecordRepository batchRunRecordRepository;
    private final ImportedRecordRepository importedRecordRepository;
    private final QualityGateResultRepository qualityGateResultRepository;
    private final FileAssetService fileAssetService;
    private final FileProcessLogService fileProcessLogService;
    private final JobInstanceService jobInstanceService;
    private final double defaultDuplicateMaxRate;
    private final long defaultDuplicateMinLines;
    private final boolean qualityEnforceDefault;

    public JobCompletionNotificationListener(
            BatchRunRecordRepository batchRunRecordRepository,
            ImportedRecordRepository importedRecordRepository,
            QualityGateResultRepository qualityGateResultRepository,
            FileAssetService fileAssetService,
            FileProcessLogService fileProcessLogService,
            JobInstanceService jobInstanceService,
            @org.springframework.beans.factory.annotation.Value("${batch.import.duplicate.max-rate:0.0}")
                    double defaultDuplicateMaxRate,
            @org.springframework.beans.factory.annotation.Value("${batch.import.duplicate.min-lines:100}")
                    long defaultDuplicateMinLines,
            @org.springframework.beans.factory.annotation.Value("${quality.enforce-default:false}")
                    boolean qualityEnforceDefault) {
        this.batchRunRecordRepository = batchRunRecordRepository;
        this.importedRecordRepository = importedRecordRepository;
        this.qualityGateResultRepository = qualityGateResultRepository;
        this.fileAssetService = fileAssetService;
        this.fileProcessLogService = fileProcessLogService;
        this.jobInstanceService = jobInstanceService;
        this.defaultDuplicateMaxRate = Math.max(0.0, defaultDuplicateMaxRate);
        this.defaultDuplicateMinLines = Math.max(1L, defaultDuplicateMinLines);
        this.qualityEnforceDefault = qualityEnforceDefault;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Starting job: {}", jobExecution.getJobInstance().getJobName());
        log.info("Job parameters: {}", jobExecution.getJobParameters());
        jobInstanceService.markRunning(jobExecution);
        upsertBatchRun(jobExecution, "RUNNING");
    }

    // #28:把 afterJob 内的两处持久化(completeFromBatch + upsertBatchRun)纳入同一事务,
    // 避免中途 crash 留下 BusinessJobInstance 与 BatchRunRecord 状态不一致。
    @Override
    @Transactional
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        Duration duration = Duration.between(
                jobExecution.getStartTime().toInstant(ZoneOffset.UTC),
                jobExecution.getEndTime() != null
                        ? jobExecution.getEndTime().toInstant(ZoneOffset.UTC)
                        : java.time.Instant.now());

        log.info("Job [{}] completed with status: {}", jobName, status);
        log.info("Job [{}] execution time: {} seconds", jobName, duration.getSeconds());

        if (status == BatchStatus.COMPLETED) {
            validateDataQuality(jobExecution);
            logJobStatistics(jobExecution);
            boolean gateFailed = false;
            gateFailed |= evaluatePostImportQuality(jobExecution);
            gateFailed |= evaluateDuplicateRate(jobExecution);
            gateFailed |= evaluatePostExportQuality(jobExecution);
            registerGeneratedExportFile(jobExecution);
            // 可选硬闸门：opt-in (quality.enforce=true) 时，任一质量门 FAIL 即把作业判为 FAILED，
            // 复用既有失败/补偿/告警路径；默认 false 保持原「软降级 PARTIAL」行为不变。
            if (gateFailed && isQualityEnforced(jobExecution)) {
                log.error("Job [{}] failed quality gate enforcement (quality.enforce=true) -> marking FAILED", jobName);
                jobExecution.setStatus(BatchStatus.FAILED);
                jobExecution.setExitStatus(
                        ExitStatus.FAILED.and(new ExitStatus("QUALITY_GATE_FAILED", "quality gate failed")));
            }
        } else if (status == BatchStatus.FAILED) {
            handleJobFailure(jobExecution);
        }

        logJobSummary(jobExecution);
        jobInstanceService.completeFromBatch(jobExecution);
        upsertBatchRun(jobExecution, jobExecution.getStatus().name());
    }

    /**
     * 质量门是否强制阻断。opt-in：job 参数 {@code quality.enforce=true} 时开启，
     * 缺省（含全局默认 {@code quality.enforce-default})关闭，行为与改造前一致。
     */
    private boolean isQualityEnforced(JobExecution jobExecution) {
        if (jobExecution.getJobParameters() != null) {
            String enforce = jobExecution.getJobParameters().getString("quality.enforce");
            if (enforce != null && !enforce.isBlank()) {
                return Boolean.parseBoolean(enforce.trim());
            }
        }
        return qualityEnforceDefault;
    }

    private void validateDataQuality(JobExecution jobExecution) {
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        if (stepExecutions.isEmpty()) {
            log.warn(
                    "No step executions found for job: {}",
                    jobExecution.getJobInstance().getJobName());
            return;
        }

        stepExecutions.forEach(step -> {
            log.info(
                    "Step [{}] - Read: {}, Write: {}, Skip: {}, Commit: {}",
                    step.getStepName(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getSkipCount(),
                    step.getCommitCount());
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
        log.error(
                "Job [{}] failed with status: {}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus());

        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            log.error(
                    "Job [{}] failure exceptions:",
                    jobExecution.getJobInstance().getJobName());
            jobExecution.getAllFailureExceptions().forEach(ex -> log.error("  - Exception: {}", ex.getMessage(), ex));
        }

        jobExecution.getStepExecutions().stream()
                .filter(step -> step.getFailureExceptions() != null
                        && !step.getFailureExceptions().isEmpty())
                .forEach(step -> {
                    log.error("Step [{}] failure exceptions:", step.getStepName());
                    step.getFailureExceptions().forEach(ex -> log.error("  - Exception: {}", ex.getMessage(), ex));
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

    /** @return true 当且仅当该质量门被评估且结果为 FAIL（用于可选硬闸门判定）。 */
    private boolean evaluatePostImportQuality(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        if (!BatchJobNames.FILE_IMPORT_JOB.equals(jobName)) {
            return false;
        }
        String batchDate = null;
        if (jobExecution.getJobParameters() != null) {
            batchDate = jobExecution.getJobParameters().getString("batchDate");
            if (batchDate == null || batchDate.isBlank()) {
                batchDate = jobExecution.getJobParameters().getString("batch.date");
            }
        }
        if (batchDate == null || batchDate.isBlank()) {
            return false;
        }
        try {
            long total = importedRecordRepository.countByBatchDate(batchDate);
            long missingName = importedRecordRepository.countMissingNameByBatchDate(batchDate);
            double missingRate = total == 0 ? 0.0 : (double) missingName / (double) total;
            boolean failed = missingRate > 0.01;

            persistQualityGate(
                    jobExecution,
                    "IMPORT_REQUIRED_FIELD_COMPLETENESS",
                    batchDate,
                    total,
                    missingName,
                    missingRate,
                    0.01,
                    total,
                    failed ? "FAIL" : "PASS",
                    "missing-name-rate=" + missingRate);
            return failed;
        } catch (Exception ex) {
            log.warn("Failed to evaluate post-import quality gate for executionId={}", jobExecution.getId(), ex);
            return false;
        }
    }

    /** @return true 当且仅当重复率门被评估且结果为 FAIL。 */
    private boolean evaluateDuplicateRate(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        if (!BatchJobNames.FILE_IMPORT_JOB.equals(jobName)) {
            return false;
        }
        String batchDate = null;
        if (jobExecution.getJobParameters() != null) {
            batchDate = jobExecution.getJobParameters().getString("batchDate");
        }
        if (batchDate == null || batchDate.isBlank()) {
            return false;
        }
        double maxRate = defaultDuplicateMaxRate;
        long minLines = defaultDuplicateMinLines;
        if (jobExecution.getJobParameters() != null) {
            String maxRateStr = jobExecution.getJobParameters().getString("quality.maxDuplicateRate");
            String minLinesStr = jobExecution.getJobParameters().getString("quality.minDuplicateLines");
            Double parsedMax = parseDouble(maxRateStr);
            Long parsedMin = parseLong(minLinesStr);
            if (parsedMax != null) {
                maxRate = Math.max(0.0, parsedMax);
            }
            if (parsedMin != null) {
                minLines = Math.max(1L, parsedMin);
            }
        }
        try {
            long total = importedRecordRepository.countByBatchDate(batchDate);
            if (total < minLines) {
                return false;
            }
            long duplicates = importedRecordRepository.countDuplicateBusinessKeysByBatchDate(batchDate);
            double rate = total == 0 ? 0.0 : (double) duplicates / (double) total;
            boolean failed = rate > maxRate;
            persistQualityGate(
                    jobExecution,
                    "IMPORT_DUPLICATE_RATE",
                    batchDate,
                    total,
                    duplicates,
                    rate,
                    maxRate,
                    minLines,
                    failed ? "FAIL" : "PASS",
                    "duplicate-rate=" + rate);
            return failed;
        } catch (Exception ex) {
            log.warn("Failed to evaluate duplicate rate gate for executionId={}", jobExecution.getId(), ex);
            return false;
        }
    }

    /** @return true 当且仅当导出行数门被评估且结果为 FAIL。 */
    private boolean evaluatePostExportQuality(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        if (!BatchJobNames.DATA_EXPORT_JOB.equals(jobName) && !BatchJobNames.FILE_EXPORT_JOB.equals(jobName)) {
            return false;
        }
        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        String batchDate = null;
        String expectedStr = null;
        String minStr = null;
        if (jobExecution.getJobParameters() != null) {
            batchDate = jobExecution.getJobParameters().getString("batchDate");
            expectedStr = jobExecution.getJobParameters().getString("quality.expectedRows");
            minStr = jobExecution.getJobParameters().getString("quality.minRows");
        }
        Long expected = parseLong(expectedStr);
        Long min = parseLong(minStr);
        if (expected == null && min == null) {
            return false;
        }
        long total = writeCount;
        String status;
        String message;
        double errorRate = 0.0;
        if (expected != null) {
            long diff = Math.abs(expected - writeCount);
            errorRate = expected == 0 ? (writeCount == 0 ? 0.0 : 1.0) : (double) diff / expected;
            status = diff == 0 ? "PASS" : "FAIL";
            message = "expectedRows=" + expected + ", actualRows=" + writeCount;
        } else {
            status = writeCount >= min ? "PASS" : "FAIL";
            message = "minRows=" + min + ", actualRows=" + writeCount;
        }
        persistQualityGate(
                jobExecution,
                "EXPORT_ROW_COUNT",
                batchDate,
                total,
                Math.max(0L, (expected == null ? 0L : Math.abs(expected - writeCount))),
                errorRate,
                0.0,
                min == null ? 0L : min,
                status,
                message);
        return "FAIL".equals(status);
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void persistQualityGate(
            JobExecution jobExecution,
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

            long readCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getReadCount)
                    .sum();
            long writeCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getWriteCount)
                    .sum();
            long skipCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getSkipCount)
                    .sum();
            long filterCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getFilterCount)
                    .sum();
            long rollbackCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getRollbackCount)
                    .sum();
            long commitCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getCommitCount)
                    .sum();
            long parseErrorCount = jobExecution.getStepExecutions().stream()
                    .mapToLong(step -> step.getExecutionContext().getLong("parse.error.count", 0L))
                    .sum();
            boolean qualityPassed = readCount == (writeCount + skipCount + filterCount) && parseErrorCount == 0;
            String finalStatus = ("COMPLETED".equals(status) && !qualityPassed) ? "PARTIAL" : status;
            String qualityMessage = qualityPassed
                    ? "OK"
                    : String.format(
                            "quality gate failed: read=%d write=%d skip=%d filter=%d parseErrors=%d",
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

            if (jobExecution.getStatus() == BatchStatus.FAILED
                    && !jobExecution.getAllFailureExceptions().isEmpty()) {
                String message = jobExecution.getAllFailureExceptions().get(0).getMessage();
                record.setErrorMessage(
                        message != null && message.length() > 1000 ? message.substring(0, 1000) : message);
            } else {
                record.setErrorMessage(null);
            }

            if (!qualityPassed) {
                log.warn(
                        "Data-quality gate not passed for executionId={}: read={}, write={}, skip={}, filter={}",
                        jobExecution.getId(),
                        readCount,
                        writeCount,
                        skipCount,
                        filterCount);
            }

            batchRunRecordRepository.save(record);
        } catch (Exception ex) {
            log.error("Failed to persist batch run audit record for executionId={}", jobExecution.getId(), ex);
        }
    }

    private void registerGeneratedExportFile(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        if (!BatchJobNames.DATA_EXPORT_JOB.equals(jobName)) {
            return;
        }

        String outputFileName = jobExecution.getJobParameters().getString("output.file.name");
        if (outputFileName == null || outputFileName.isBlank()) {
            outputFileName = "export/output.csv";
        }

        Path outputPath = Path.of(outputFileName);
        if (!Files.exists(outputPath)) {
            log.warn("Skip registering export file asset because file does not exist: {}", outputPath);
            return;
        }

        Path outputFileNamePart = outputPath.getFileName();
        if (outputFileNamePart == null) {
            log.warn("Skip registering export file asset because path has no file name: {}", outputPath);
            return;
        }

        String batchDate = jobExecution.getJobParameters().getString("batchDate");
        var fileRecord = fileAssetService.registerOutboundFile(
                outputFileNamePart.toString(),
                outputPath.toString(),
                "DATA_EXPORT",
                batchDate,
                null,
                null,
                "PROCESSED",
                Map.of(
                        "jobName",
                        jobName,
                        "jobExecutionId",
                        jobExecution.getId(),
                        "source",
                        "JobCompletionNotificationListener"));
        fileProcessLogService.log(
                fileRecord.getId(),
                "afterJob",
                "EXPORT",
                null,
                "PROCESSED",
                "SUCCESS",
                null,
                jobName,
                0,
                null,
                null,
                Map.of("jobExecutionId", jobExecution.getId(), "outputFileName", outputFileName));
    }
}
