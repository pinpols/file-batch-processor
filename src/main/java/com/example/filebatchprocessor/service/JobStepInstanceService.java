package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.ErrorCodeClassifier;
import com.example.filebatchprocessor.model.BusinessJobStepInstance;
import com.example.filebatchprocessor.model.BusinessJobStepStatus;
import com.example.filebatchprocessor.repository.BusinessJobStepInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobStepInstanceService {

    private final BusinessJobStepInstanceRepository repository;
    private final JobExecutionLogService jobExecutionLogService;
    private final ObjectMapper objectMapper;

    public JobStepInstanceService(
            BusinessJobStepInstanceRepository repository,
            JobExecutionLogService jobExecutionLogService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.jobExecutionLogService = jobExecutionLogService;
        this.objectMapper = objectMapper;
    }

    public List<BusinessJobStepInstance> replaceFromSpringBatch(
            Long jobInstanceId, JobExecution jobExecution, String operatorName) {
        if (jobInstanceId == null || jobExecution == null) {
            return List.of();
        }
        repository.deleteByJobInstanceId(jobInstanceId);
        List<StepExecution> orderedExecutions = new ArrayList<>(jobExecution.getStepExecutions());
        orderedExecutions.sort(
                Comparator.comparing(StepExecution::getStartTime, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(StepExecution::getStepName, Comparator.nullsLast(String::compareTo)));

        List<BusinessJobStepInstance> saved = new ArrayList<>();
        int stepOrder = 1;
        for (StepExecution stepExecution : orderedExecutions) {
            BusinessJobStepInstance stepInstance = new BusinessJobStepInstance();
            stepInstance.setJobInstanceId(jobInstanceId);
            stepInstance.setStepCode(stepExecution.getStepName());
            stepInstance.setStepName(stepExecution.getStepName());
            stepInstance.setStepOrderNo(stepOrder++);
            stepInstance.setAttemptNo(1);
            stepInstance.setSpringStepExecutionId(stepExecution.getId());
            stepInstance.setStatus(
                    BusinessJobStepStatus.fromSpringBatch(stepExecution).name());
            stepInstance.setReadCount(stepExecution.getReadCount());
            stepInstance.setWriteCount(stepExecution.getWriteCount());
            stepInstance.setFilterCount(stepExecution.getFilterCount());
            stepInstance.setSkipCount(stepExecution.getSkipCount());
            stepInstance.setCommitCount(stepExecution.getCommitCount());
            stepInstance.setRollbackCount(stepExecution.getRollbackCount());
            stepInstance.setStartTime(stepExecution.getStartTime());
            stepInstance.setEndTime(stepExecution.getEndTime());
            stepInstance.setErrorCode(resolveErrorCode(stepExecution));
            stepInstance.setErrorMessage(resolveErrorMessage(stepExecution));
            Map<String, Object> summary = new LinkedHashMap<>();
            if (stepExecution.getExitStatus() != null
                    && stepExecution.getExitStatus().getExitCode() != null) {
                summary.put("exitCode", stepExecution.getExitStatus().getExitCode());
            }
            summary.put("summary", stepExecution.getSummary());
            summary.put("readSkipCount", stepExecution.getReadSkipCount());
            summary.put("processSkipCount", stepExecution.getProcessSkipCount());
            summary.put("writeSkipCount", stepExecution.getWriteSkipCount());
            stepInstance.setSummaryJson(toJson(summary));
            BusinessJobStepInstance persisted = repository.save(stepInstance);
            saved.add(persisted);
            jobExecutionLogService.log(
                    jobInstanceId,
                    persisted.getId(),
                    "STEP_SYNC",
                    "INFO",
                    "Step synchronized: " + persisted.getStepName() + " -> " + persisted.getStatus(),
                    operatorName,
                    stepPayload(stepExecution));
        }
        return saved;
    }

    private Map<String, Object> stepPayload(StepExecution stepExecution) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepName", stepExecution.getStepName());
        payload.put("springStepExecutionId", stepExecution.getId());
        payload.put(
                "status",
                stepExecution.getStatus() == null
                        ? null
                        : stepExecution.getStatus().name());
        payload.put("readCount", stepExecution.getReadCount());
        payload.put("writeCount", stepExecution.getWriteCount());
        payload.put("skipCount", stepExecution.getSkipCount());
        payload.put("commitCount", stepExecution.getCommitCount());
        payload.put("rollbackCount", stepExecution.getRollbackCount());
        payload.put(
                "exitCode",
                stepExecution.getExitStatus() == null
                        ? null
                        : stepExecution.getExitStatus().getExitCode());
        return payload;
    }

    private String resolveErrorCode(StepExecution stepExecution) {
        if (stepExecution.getFailureExceptions() == null
                || stepExecution.getFailureExceptions().isEmpty()) {
            return null;
        }
        return ErrorCodeClassifier.classify(stepExecution.getFailureExceptions().get(0))
                .name();
    }

    private String resolveErrorMessage(StepExecution stepExecution) {
        if (stepExecution.getFailureExceptions() == null
                || stepExecution.getFailureExceptions().isEmpty()) {
            return null;
        }
        Throwable throwable = stepExecution.getFailureExceptions().get(0);
        return truncate(throwable.getMessage(), 2000);
    }

    private String toJson(Map<String, ?> payload) {
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
}
