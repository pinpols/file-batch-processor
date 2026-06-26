package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.ErrorCodeClassifier;
import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.BusinessJobInstanceStatus;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobInstanceService {

    private static final DateTimeFormatter INSTANCE_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final BusinessJobInstanceRepository repository;
    private final JobStepInstanceService jobStepInstanceService;
    private final JobExecutionLogService jobExecutionLogService;
    private final ObjectMapper objectMapper;

    public JobInstanceService(
            BusinessJobInstanceRepository repository,
            JobStepInstanceService jobStepInstanceService,
            JobExecutionLogService jobExecutionLogService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.jobStepInstanceService = jobStepInstanceService;
        this.jobExecutionLogService = jobExecutionLogService;
        this.objectMapper = objectMapper;
    }

    public BusinessJobInstance createTriggeredInstance(CreateRequest request) {
        BusinessJobInstance instance = new BusinessJobInstance();
        instance.setJobInstanceNo(generateInstanceNo());
        instance.setTaskId(request.taskId());
        instance.setJobName(request.jobName());
        instance.setTriggerSource(request.triggerSource());
        instance.setOperatorName(request.operatorName());
        instance.setBizDate(blankToNull(request.bizDate()));
        instance.setBatchNo(blankToNull(request.batchNo()));
        instance.setRunKey(blankToNull(request.runKey()));
        instance.setStatus(BusinessJobInstanceStatus.TRIGGERED.name());
        instance.setRerunFlag(request.rerunFlag());
        instance.setRetryFlag(request.retryFlag());
        instance.setManualFlag(request.manualFlag());
        instance.setRelatedFileId(request.relatedFileId());
        instance.setRequestPayload(toJson(request.requestPayload()));
        BusinessJobInstance saved = repository.save(instance);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", request.taskId());
        payload.put("jobName", request.jobName());
        payload.put("triggerSource", request.triggerSource());
        payload.put("manualFlag", request.manualFlag());
        payload.put("rerunFlag", request.rerunFlag());
        payload.put("retryFlag", request.retryFlag());
        if (request.runKey() != null && !request.runKey().isBlank()) {
            payload.put("runKey", request.runKey());
        }
        jobExecutionLogService.log(
                saved.getId(),
                null,
                "INSTANCE_CREATED",
                "INFO",
                "Business job instance created",
                request.operatorName(),
                payload);
        return saved;
    }

    public void markLaunchFailed(Long jobInstanceId, String reason) {
        if (jobInstanceId == null) {
            return;
        }
        repository.findById(jobInstanceId).ifPresent(instance -> {
            instance.setStatus(BusinessJobInstanceStatus.FAILED.name());
            instance.setErrorCode(
                    ErrorCodeClassifier.classify(new RuntimeException(reason == null ? "Launch failed" : reason))
                            .name());
            instance.setErrorMessage(truncate(reason, 2000));
            instance.setEndTime(LocalDateTime.now());
            if (instance.getStartTime() != null && instance.getEndTime() != null) {
                instance.setDurationMs(Math.max(
                        0L,
                        Duration.between(instance.getStartTime(), instance.getEndTime())
                                .toMillis()));
            }
            repository.save(instance);
            Map<String, Object> payload = new LinkedHashMap<>();
            if (reason != null && !reason.isBlank()) {
                payload.put("reason", reason);
            }
            jobExecutionLogService.log(
                    instance.getId(),
                    null,
                    "LAUNCH_FAILED",
                    "ERROR",
                    "Launch failed before Spring Batch completion",
                    instance.getOperatorName(),
                    payload);
        });
    }

    public void markRunning(JobExecution jobExecution) {
        resolveInstance(jobExecution).ifPresent(instance -> {
            instance.setStatus(BusinessJobInstanceStatus.RUNNING.name());
            instance.setSpringBatchExecutionId(jobExecution.getId());
            instance.setSpringBatchInstanceId(
                    jobExecution.getJobInstance() == null
                            ? null
                            : jobExecution.getJobInstance().getInstanceId());
            instance.setStartTime(
                    jobExecution.getStartTime() != null ? jobExecution.getStartTime() : LocalDateTime.now());
            repository.save(instance);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("springBatchExecutionId", jobExecution.getId());
            if (jobExecution.getJobInstance() != null) {
                payload.put("jobName", jobExecution.getJobInstance().getJobName());
            }
            jobExecutionLogService.log(
                    instance.getId(),
                    null,
                    "JOB_STARTED",
                    "INFO",
                    "Spring Batch job started",
                    instance.getOperatorName(),
                    payload);
        });
    }

    public void completeFromBatch(JobExecution jobExecution) {
        resolveInstance(jobExecution).ifPresent(instance -> {
            instance.setSpringBatchExecutionId(jobExecution.getId());
            instance.setSpringBatchInstanceId(
                    jobExecution.getJobInstance() == null
                            ? null
                            : jobExecution.getJobInstance().getInstanceId());
            instance.setStatus(
                    BusinessJobInstanceStatus.fromSpringBatch(jobExecution).name());
            if (instance.getStartTime() == null) {
                instance.setStartTime(jobExecution.getStartTime());
            }
            instance.setEndTime(jobExecution.getEndTime() != null ? jobExecution.getEndTime() : LocalDateTime.now());
            if (instance.getStartTime() != null && instance.getEndTime() != null) {
                instance.setDurationMs(Math.max(
                        0L,
                        Duration.between(instance.getStartTime(), instance.getEndTime())
                                .toMillis()));
            }
            instance.setErrorCode(resolveErrorCode(jobExecution));
            instance.setErrorMessage(resolveErrorMessage(jobExecution));
            instance.setResultSummary(toJson(buildSummary(jobExecution)));
            repository.save(instance);

            jobStepInstanceService.replaceFromSpringBatch(instance.getId(), jobExecution, instance.getOperatorName());
            jobExecutionLogService.log(
                    instance.getId(),
                    null,
                    "JOB_FINISHED",
                    instance.getStatus().equals(BusinessJobInstanceStatus.FAILED.name()) ? "ERROR" : "INFO",
                    "Spring Batch job finished: " + instance.getStatus(),
                    instance.getOperatorName(),
                    buildSummary(jobExecution));
        });
    }

    public Long resolveRelatedFileId(Map<String, ?> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        return asLong(firstNonNull(
                parameters.get(JobInstanceParameters.RELATED_FILE_ID),
                parameters.get("fileRecordId"),
                parameters.get("file_record_id"),
                parameters.get("relatedFileId")));
    }

    private Optional<BusinessJobInstance> resolveInstance(JobExecution jobExecution) {
        if (jobExecution == null) {
            return Optional.empty();
        }
        JobParameters parameters = jobExecution.getJobParameters();
        if (parameters != null) {
            Long instanceId = parameters.getLong(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID);
            if (instanceId != null) {
                return repository.findById(instanceId);
            }
        }
        return repository.findBySpringBatchExecutionId(jobExecution.getId());
    }

    private Map<String, Object> buildSummary(JobExecution jobExecution) {
        long totalRead = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();
        long totalWrite = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        long totalSkip = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount)
                .sum();
        long totalFilter = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getFilterCount)
                .sum();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("springBatchExecutionId", jobExecution.getId());
        summary.put(
                "springBatchInstanceId",
                jobExecution.getJobInstance() == null
                        ? null
                        : jobExecution.getJobInstance().getInstanceId());
        summary.put(
                "batchStatus",
                jobExecution.getStatus() == null
                        ? null
                        : jobExecution.getStatus().name());
        summary.put(
                "exitCode",
                jobExecution.getExitStatus() == null
                        ? null
                        : jobExecution.getExitStatus().getExitCode());
        summary.put("stepCount", jobExecution.getStepExecutions().size());
        summary.put("readCount", totalRead);
        summary.put("writeCount", totalWrite);
        summary.put("skipCount", totalSkip);
        summary.put("filterCount", totalFilter);
        summary.put(
                "failureCount",
                jobExecution.getAllFailureExceptions() == null
                        ? 0
                        : jobExecution.getAllFailureExceptions().size());
        return summary;
    }

    private String resolveErrorCode(JobExecution jobExecution) {
        if (jobExecution.getAllFailureExceptions() == null
                || jobExecution.getAllFailureExceptions().isEmpty()) {
            return null;
        }
        return ErrorCodeClassifier.classify(
                        jobExecution.getAllFailureExceptions().get(0))
                .name();
    }

    private String resolveErrorMessage(JobExecution jobExecution) {
        if (jobExecution.getAllFailureExceptions() != null
                && !jobExecution.getAllFailureExceptions().isEmpty()) {
            return truncate(jobExecution.getAllFailureExceptions().get(0).getMessage(), 2000);
        }
        if (jobExecution.getExitStatus() != null
                && jobExecution.getExitStatus().getExitDescription() != null
                && !jobExecution.getExitStatus().getExitDescription().isBlank()) {
            return truncate(jobExecution.getExitStatus().getExitDescription(), 2000);
        }
        return null;
    }

    private String generateInstanceNo() {
        return "JI-" + LocalDate.now().format(INSTANCE_NO_DATE) + "-"
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

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record CreateRequest(
            String taskId,
            String jobName,
            String triggerSource,
            String operatorName,
            String bizDate,
            String batchNo,
            String runKey,
            boolean rerunFlag,
            boolean retryFlag,
            boolean manualFlag,
            Long relatedFileId,
            Map<String, ?> requestPayload) {}
}
