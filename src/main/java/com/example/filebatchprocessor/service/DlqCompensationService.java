package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationActionType;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 死信补偿服务：支持按记录重放和按任务重触发。
 */
@Slf4j
@Service
public class DlqCompensationService {

    private static final String SYSTEM_OPERATOR = "SYSTEM";

    private final DlqRecordRepository dlqRecordRepository;
    private final PartitionedImportService partitionedImportService;
    private final JobLauncher jobLauncher;
    private final Job processFileJob;
    private final BatchJobResolver batchJobResolver;
    private final TaskConfigService taskConfigService;
    private final JobInstanceService jobInstanceService;
    private final RetryCompensationService retryCompensationService;
    private final int maxReplayCount;
    private final long retryDelayMs;

    public DlqCompensationService(
            DlqRecordRepository dlqRecordRepository,
            PartitionedImportService partitionedImportService,
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            @Qualifier("processFileJob") Job processFileJob,
            BatchJobResolver batchJobResolver,
            TaskConfigService taskConfigService,
            JobInstanceService jobInstanceService,
            RetryCompensationService retryCompensationService,
            @Value("${batch.dlq.max-replay-count:5}") int maxReplayCount,
            @Value("${batch.dlq.retry-delay-ms:60000}") long retryDelayMs) {
        this.dlqRecordRepository = dlqRecordRepository;
        this.partitionedImportService = partitionedImportService;
        this.jobLauncher = jobLauncher;
        this.processFileJob = processFileJob;
        this.batchJobResolver = batchJobResolver;
        this.taskConfigService = taskConfigService;
        this.jobInstanceService = jobInstanceService;
        this.retryCompensationService = retryCompensationService;
        this.maxReplayCount = Math.max(1, maxReplayCount);
        this.retryDelayMs = Math.max(1000L, retryDelayMs);
    }

    // Suspend the tasklet transaction before launching a nested Spring Batch job.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int replayPending(int limit) {
        List<DlqRecord> records =
                dlqRecordRepository
                        .findTop100ByHandledFalseAndManualRequiredFalseAndRetryableTrueAndNextRetryAtBeforeOrderByCreatedAtAsc(
                                LocalDateTime.now());
        // #26:limit 用"本次实际处理过的记录数(含转人工/失败)"来卡,避免一批里多数失败时超额处理;
        // 返回值 processed 仍只数"真正重放成功"的条数(转人工/失败不算重放)。
        int processed = 0;
        int attempts = 0;
        for (DlqRecord record : records) {
            if (attempts >= Math.max(limit, 1)) {
                break;
            }
            attempts++;
            long currentReplay = record.getReplayCount() == null ? 0L : record.getReplayCount();
            if (currentReplay >= maxReplayCount) {
                record.setManualRequired(true);
                record.setCompensationStatus("MANUAL_REQUIRED");
                record.setLastReplayError("Exceeded max replay count: " + maxReplayCount);
                dlqRecordRepository.save(record);
                continue;
            }
            Map<String, String> params = parse(record.getParams());
            String taskId = resolveTaskId(params);
            Long relatedFileId = jobInstanceService.resolveRelatedFileId(params);
            CompensationRecord compensationRecord =
                    retryCompensationService.startCompensation(new RetryCompensationService.StartRequest(
                            CompensationActionType.DLQ_REPLAY,
                            null,
                            null,
                            relatedFileId,
                            record.getId(),
                            null,
                            null,
                            SYSTEM_OPERATOR,
                            "Scheduled DLQ replay",
                            buildCompensationRequest(record, taskId, params)));
            try {
                ReplayOutcome outcome = replay(record, params, taskId, compensationRecord.getId());
                retryCompensationService.completeCompensation(
                        compensationRecord.getId(),
                        outcome.targetJobInstanceId(),
                        outcome.springExecutionId(),
                        outcome.resultPayload());
                record.setHandled(true);
                record.setHandledAt(LocalDateTime.now());
                record.setLastReplayError(null);
                record.setReplayCount(currentReplay + 1);
                record.setCompensationStatus("REPLAYED");
                dlqRecordRepository.save(record);
                processed++;
            } catch (Exception e) {
                Long targetJobInstanceId = e instanceof ReplayFailureException replayFailureException
                        ? replayFailureException.targetJobInstanceId()
                        : null;
                retryCompensationService.failCompensation(
                        compensationRecord.getId(),
                        targetJobInstanceId,
                        e,
                        e.getMessage(),
                        Map.of("dlqRecordId", record.getId()));
                record.setReplayCount(currentReplay + 1);
                String msg = e.getMessage();
                record.setLastReplayError(msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg);
                record.setCompensationStatus("RETRY_PENDING");
                record.setNextRetryAt(LocalDateTime.now().plusNanos(retryDelayMs * 1_000_000));
                dlqRecordRepository.save(record);
                log.error("DLQ replay failed for id={}", record.getId(), e);
            }
        }
        return processed;
    }

    private ReplayOutcome replay(DlqRecord record, Map<String, String> params, String taskId, Long compensationRecordId)
            throws Exception {
        String source = params.getOrDefault("source", "");
        if ("record-writer".equals(source)) {
            String businessKey = params.get("businessKey");
            String name = params.get("name");
            String description = params.get("description");
            String batchDate = params.get("batchDate");
            partitionedImportService.importRecord(businessKey, name, description, batchDate, null, null);
            return new ReplayOutcome(
                    null,
                    null,
                    Map.of(
                            "mode",
                            "RECORD_WRITER",
                            "dlqRecordId",
                            record.getId(),
                            "businessKey",
                            businessKey == null ? "" : businessKey));
        }

        String raw = record.getParams();
        Job replayJob = resolveReplayJob(record, taskId);
        String effectiveTaskId = (taskId == null || taskId.isBlank()) ? replayJob.getName() : taskId;
        Long relatedFileId = jobInstanceService.resolveRelatedFileId(params);
        String runKey = "dlq-" + record.getId() + "-" + System.nanoTime();
        BusinessJobInstance businessInstance =
                jobInstanceService.createTriggeredInstance(new JobInstanceService.CreateRequest(
                        effectiveTaskId,
                        replayJob.getName(),
                        "DLQ_REPLAY",
                        SYSTEM_OPERATOR,
                        blankToNull(params.get("batchDate")),
                        blankToNull(params.get("batchNo")),
                        runKey,
                        false,
                        true,
                        false,
                        relatedFileId,
                        buildReplayRequestPayload(record, effectiveTaskId, compensationRecordId, params)));
        try {
            JobParametersBuilder builder = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("job.param", raw == null ? "" : raw)
                    .addLong("dlq.replay.id", record.getId())
                    .addString("dlq.replay.time", String.valueOf(System.currentTimeMillis()))
                    .addLong(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID, businessInstance.getId())
                    .addString(JobInstanceParameters.BUSINESS_JOB_INSTANCE_NO, businessInstance.getJobInstanceNo())
                    .addString(JobInstanceParameters.TRIGGER_SOURCE, "DLQ_REPLAY")
                    .addString(JobInstanceParameters.TRIGGERED_BY, SYSTEM_OPERATOR);

            if (relatedFileId != null) {
                builder.addLong(JobInstanceParameters.RELATED_FILE_ID, relatedFileId);
            }
            if (effectiveTaskId != null && !effectiveTaskId.isBlank()) {
                builder.addString("task.id", effectiveTaskId);
            }

            mergeTaskParameters(builder, effectiveTaskId);
            params.forEach((key, value) -> {
                if (key == null
                        || key.isBlank()
                        || value == null
                        || "time".equals(key)
                        || JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID.equals(key)
                        || JobInstanceParameters.BUSINESS_JOB_INSTANCE_NO.equals(key)
                        || JobInstanceParameters.TRIGGER_SOURCE.equals(key)
                        || JobInstanceParameters.TRIGGERED_BY.equals(key)
                        || JobInstanceParameters.RELATED_FILE_ID.equals(key)) {
                    return;
                }
                builder.addString(key, value);
            });

            JobExecution execution = jobLauncher.run(replayJob, builder.toJobParameters());
            return new ReplayOutcome(
                    businessInstance.getId(),
                    execution.getId(),
                    Map.of(
                            "mode",
                            "BATCH_JOB",
                            "taskId",
                            effectiveTaskId,
                            "jobName",
                            replayJob.getName(),
                            "springExecutionId",
                            execution.getId()));
        } catch (Exception ex) {
            jobInstanceService.markLaunchFailed(businessInstance.getId(), ex.getMessage());
            throw new ReplayFailureException(ex, businessInstance.getId());
        }
    }

    private Map<String, String> parse(String param) {
        Map<String, String> map = new HashMap<>();
        if (param == null || param.isBlank()) {
            return map;
        }
        String[] pairs = param.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private String resolveTaskId(Map<String, String> params) {
        String taskId = params.get("task.id");
        if (taskId == null || taskId.isBlank()) {
            taskId = params.get("taskId");
        }
        return taskId;
    }

    private Job resolveReplayJob(DlqRecord record, String taskId) {
        if (record.getJobName() != null && !record.getJobName().isBlank()) {
            Job resolved = batchJobResolver
                    .resolve(record.getJobName())
                    .map(BatchJobResolver.ResolvedJob::job)
                    .orElse(null);
            if (resolved != null) {
                return resolved;
            }
        }

        if (taskId != null && !taskId.isBlank()) {
            try {
                String configuredJobName =
                        taskConfigService.getTaskDefinition(taskId).getJobName();
                Job resolved = batchJobResolver
                        .resolve(configuredJobName)
                        .map(BatchJobResolver.ResolvedJob::job)
                        .orElse(null);
                if (resolved != null) {
                    return resolved;
                }
            } catch (Exception ex) {
                log.debug("Failed to resolve replay job from task config: taskId={}", taskId, ex);
            }
        }

        return processFileJob;
    }

    private void mergeTaskParameters(JobParametersBuilder builder, String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        try {
            taskConfigService.getTaskParametersAsMap(taskId).forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || "time".equals(key)) {
                    return;
                }
                builder.addString(key, value);
            });
        } catch (Exception ex) {
            log.debug("No persisted task parameters found for replay taskId={}", taskId, ex);
        }
    }

    private Map<String, Object> buildCompensationRequest(DlqRecord record, String taskId, Map<String, String> params) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("dlqRecordId", record.getId());
        payload.put("jobName", record.getJobName());
        if (taskId != null) {
            payload.put("taskId", taskId);
        }
        payload.put("paramCount", params.size());
        return payload;
    }

    private Map<String, Object> buildReplayRequestPayload(
            DlqRecord record, String taskId, Long compensationRecordId, Map<String, String> params) {
        Map<String, Object> payload = new HashMap<>(params);
        payload.put("dlqRecordId", record.getId());
        payload.put("taskId", taskId);
        payload.put("compensationRecordId", compensationRecordId);
        return payload;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record ReplayOutcome(Long targetJobInstanceId, Long springExecutionId, Map<String, Object> resultPayload) {}

    private static final class ReplayFailureException extends RuntimeException {
        private final Long targetJobInstanceId;

        private ReplayFailureException(Throwable cause, Long targetJobInstanceId) {
            super(cause);
            this.targetJobInstanceId = targetJobInstanceId;
        }

        private Long targetJobInstanceId() {
            return targetJobInstanceId;
        }
    }
}
