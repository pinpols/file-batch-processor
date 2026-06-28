package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.ErrorCodeClassifier;
import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationActionType;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.model.CompensationStatus;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.example.filebatchprocessor.repository.CompensationRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RetryCompensationService {

    private static final DateTimeFormatter COMPENSATION_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final CompensationRecordRepository compensationRecordRepository;
    private final BusinessJobInstanceRepository businessJobInstanceRepository;
    private final JobExecutionLogService jobExecutionLogService;
    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public RetryCompensationService(
            CompensationRecordRepository compensationRecordRepository,
            BusinessJobInstanceRepository businessJobInstanceRepository,
            JobExecutionLogService jobExecutionLogService,
            JobOperator jobOperator,
            JobRepository jobRepository,
            ObjectMapper objectMapper) {
        this.compensationRecordRepository = compensationRecordRepository;
        this.businessJobInstanceRepository = businessJobInstanceRepository;
        this.jobExecutionLogService = jobExecutionLogService;
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    public CompensationRecord startCompensation(StartRequest request) {
        CompensationRecord record = new CompensationRecord();
        record.setCompensationNo(generateCompensationNo());
        record.setActionType(request.actionType().name());
        record.setStatus(CompensationStatus.RUNNING.name());
        record.setTargetJobInstanceId(request.targetJobInstanceId());
        record.setTargetStepInstanceId(request.targetStepInstanceId());
        record.setRelatedFileId(request.relatedFileId());
        record.setRelatedDlqRecordId(request.relatedDlqRecordId());
        record.setLegacyDistributionTaskId(request.legacyDistributionTaskId());
        record.setSourceSpringExecutionId(request.sourceSpringExecutionId());
        record.setOperatorName(blankToNull(request.operatorName()));
        record.setReason(truncate(blankToNull(request.reason()), 500));
        record.setRequestPayload(toJson(request.requestPayload()));
        CompensationRecord saved = compensationRecordRepository.save(record);
        logEvent(
                saved.getTargetJobInstanceId(),
                "COMPENSATION_REQUESTED",
                "WARN",
                "Compensation requested: " + saved.getActionType(),
                saved.getOperatorName(),
                buildAuditPayload(saved));
        return saved;
    }

    public void completeCompensation(
            Long compensationRecordId,
            Long targetJobInstanceId,
            Long restartedSpringExecutionId,
            Map<String, ?> resultPayload) {
        if (compensationRecordId == null) {
            return;
        }
        compensationRecordRepository.findById(compensationRecordId).ifPresent(record -> {
            if (targetJobInstanceId != null && record.getTargetJobInstanceId() == null) {
                record.setTargetJobInstanceId(targetJobInstanceId);
            }
            record.setRestartedSpringExecutionId(restartedSpringExecutionId);
            record.setStatus(CompensationStatus.COMPLETED.name());
            record.setCompletedAt(LocalDateTime.now());
            record.setResultPayload(toJson(resultPayload));
            compensationRecordRepository.save(record);
            logEvent(
                    record.getTargetJobInstanceId(),
                    "COMPENSATION_COMPLETED",
                    "INFO",
                    "Compensation completed: " + record.getActionType(),
                    record.getOperatorName(),
                    buildAuditPayload(record));
        });
    }

    public void failCompensation(
            Long compensationRecordId,
            Long targetJobInstanceId,
            Throwable throwable,
            String errorMessage,
            Map<String, ?> resultPayload) {
        if (compensationRecordId == null) {
            return;
        }
        compensationRecordRepository.findById(compensationRecordId).ifPresent(record -> {
            if (targetJobInstanceId != null && record.getTargetJobInstanceId() == null) {
                record.setTargetJobInstanceId(targetJobInstanceId);
            }
            record.setStatus(CompensationStatus.FAILED.name());
            record.setCompletedAt(LocalDateTime.now());
            record.setErrorCode(
                    throwable == null
                            ? null
                            : ErrorCodeClassifier.classify(throwable).name());
            record.setErrorMessage(
                    truncate(errorMessage == null && throwable != null ? throwable.getMessage() : errorMessage, 2000));
            record.setResultPayload(toJson(resultPayload));
            compensationRecordRepository.save(record);
            logEvent(
                    record.getTargetJobInstanceId(),
                    "COMPENSATION_FAILED",
                    "ERROR",
                    "Compensation failed: " + record.getActionType(),
                    record.getOperatorName(),
                    buildAuditPayload(record));
        });
    }

    public Long restartExecution(long executionId, String operatorName, String reason) throws Exception {
        JobExecution execution = loadRestartableExecution(executionId);
        Long targetJobInstanceId = businessJobInstanceRepository
                .findBySpringBatchExecutionId(executionId)
                .map(BusinessJobInstance::getId)
                .orElse(null);
        Long relatedFileId = businessJobInstanceRepository
                .findBySpringBatchExecutionId(executionId)
                .map(BusinessJobInstance::getRelatedFileId)
                .orElse(null);
        CompensationRecord record = startCompensation(new StartRequest(
                CompensationActionType.JOB_RESTART,
                targetJobInstanceId,
                null,
                relatedFileId,
                null,
                null,
                executionId,
                operatorName,
                reason,
                Map.of(
                        "executionId", executionId,
                        "jobName", execution.getJobInstance().getJobName(),
                        "batchStatus", execution.getStatus().name())));
        try {
            JobExecution restarted = jobOperator.restart(execution);
            long restartedId = restarted.getId();
            completeCompensation(
                    record.getId(),
                    targetJobInstanceId,
                    restartedId,
                    Map.of(
                            "executionId", executionId,
                            "restartedExecutionId", restartedId));
            return restartedId;
        } catch (Exception ex) {
            failCompensation(
                    record.getId(), targetJobInstanceId, ex, ex.getMessage(), Map.of("executionId", executionId));
            throw ex;
        }
    }

    public Long restartLatestFailed(String jobName, String operatorName, String reason) throws Exception {
        List<JobExecution> running =
                jobRepository.findRunningJobExecutions(jobName).stream().toList();
        if (!running.isEmpty()) {
            throw new IllegalStateException("Job is currently running: " + jobName);
        }
        List<JobExecution> failedExecutions = jobRepository.getJobInstances(jobName, 0, 100).stream()
                .flatMap(instance -> jobRepository.getJobExecutions(instance).stream())
                .filter(exec -> exec.getStatus() == BatchStatus.FAILED || exec.getStatus() == BatchStatus.STOPPED)
                .sorted((left, right) -> right.getCreateTime().compareTo(left.getCreateTime()))
                .toList();
        if (failedExecutions.isEmpty()) {
            throw new IllegalArgumentException("No failed/stopped execution found for job: " + jobName);
        }
        return restartExecution(failedExecutions.get(0).getId(), operatorName, reason);
    }

    public Optional<BusinessJobInstance> findLatestJobInstanceByRelatedFileId(Long relatedFileId) {
        if (relatedFileId == null) {
            return Optional.empty();
        }
        return businessJobInstanceRepository.findFirstByRelatedFileIdOrderByCreatedAtDesc(relatedFileId);
    }

    private JobExecution loadRestartableExecution(long executionId) {
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        if (!(execution.getStatus() == BatchStatus.FAILED || execution.getStatus() == BatchStatus.STOPPED)) {
            throw new IllegalStateException("Execution is not restartable: " + execution.getStatus());
        }
        return execution;
    }

    private Map<String, Object> buildAuditPayload(CompensationRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("compensationId", record.getId());
        payload.put("compensationNo", record.getCompensationNo());
        payload.put("actionType", record.getActionType());
        payload.put("status", record.getStatus());
        if (record.getSourceSpringExecutionId() != null) {
            payload.put("sourceSpringExecutionId", record.getSourceSpringExecutionId());
        }
        if (record.getRestartedSpringExecutionId() != null) {
            payload.put("restartedSpringExecutionId", record.getRestartedSpringExecutionId());
        }
        if (record.getRelatedFileId() != null) {
            payload.put("relatedFileId", record.getRelatedFileId());
        }
        if (record.getRelatedDlqRecordId() != null) {
            payload.put("relatedDlqRecordId", record.getRelatedDlqRecordId());
        }
        if (record.getLegacyDistributionTaskId() != null) {
            payload.put("legacyDistributionTaskId", record.getLegacyDistributionTaskId());
        }
        if (record.getReason() != null) {
            payload.put("reason", record.getReason());
        }
        return payload;
    }

    private void logEvent(
            Long targetJobInstanceId,
            String eventType,
            String level,
            String message,
            String operatorName,
            Map<String, ?> payload) {
        if (targetJobInstanceId == null) {
            return;
        }
        jobExecutionLogService.log(targetJobInstanceId, null, eventType, level, message, operatorName, payload);
    }

    private String generateCompensationNo() {
        return "CP-"
                + LocalDate.now().format(COMPENSATION_NO_DATE)
                + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String toJson(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return payload.toString();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record StartRequest(
            CompensationActionType actionType,
            Long targetJobInstanceId,
            Long targetStepInstanceId,
            Long relatedFileId,
            Long relatedDlqRecordId,
            Long legacyDistributionTaskId,
            Long sourceSpringExecutionId,
            String operatorName,
            String reason,
            Map<String, ?> requestPayload) {}
}
