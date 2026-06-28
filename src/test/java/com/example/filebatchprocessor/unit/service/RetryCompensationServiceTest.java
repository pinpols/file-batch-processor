package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.example.filebatchprocessor.repository.CompensationRecordRepository;
import com.example.filebatchprocessor.service.JobExecutionLogService;
import com.example.filebatchprocessor.service.RetryCompensationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

class RetryCompensationServiceTest {

    @Test
    void shouldRestartExecutionAndCompleteCompensation() throws Exception {
        CompensationRecordRepository compensationRecordRepository = mock(CompensationRecordRepository.class);
        BusinessJobInstanceRepository businessJobInstanceRepository = mock(BusinessJobInstanceRepository.class);
        JobExecutionLogService jobExecutionLogService = mock(JobExecutionLogService.class);
        JobOperator jobOperator = mock(JobOperator.class);
        JobRepository jobRepository = mock(JobRepository.class);
        RetryCompensationService service = new RetryCompensationService(
                compensationRecordRepository,
                businessJobInstanceRepository,
                jobExecutionLogService,
                jobOperator,
                jobRepository,
                new ObjectMapper());

        JobInstance springJobInstance = new JobInstance(1L, "fileImportJob");
        JobExecution failedExecution = new JobExecution(100L, springJobInstance, new JobParameters());
        failedExecution.setStatus(BatchStatus.FAILED);
        failedExecution.setCreateTime(LocalDateTime.now());
        when(jobRepository.getJobExecution(100L)).thenReturn(failedExecution);

        BusinessJobInstance businessJobInstance = new BusinessJobInstance();
        businessJobInstance.setId(88L);
        businessJobInstance.setRelatedFileId(66L);
        when(businessJobInstanceRepository.findBySpringBatchExecutionId(100L))
                .thenReturn(Optional.of(businessJobInstance));

        CompensationRecord persistedRecord = new CompensationRecord();
        when(compensationRecordRepository.save(any(CompensationRecord.class))).thenAnswer(invocation -> {
            CompensationRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(501L);
            }
            persistedRecord.setId(record.getId());
            persistedRecord.setCompensationNo(record.getCompensationNo());
            persistedRecord.setActionType(record.getActionType());
            persistedRecord.setStatus(record.getStatus());
            persistedRecord.setTargetJobInstanceId(record.getTargetJobInstanceId());
            persistedRecord.setRelatedFileId(record.getRelatedFileId());
            persistedRecord.setSourceSpringExecutionId(record.getSourceSpringExecutionId());
            persistedRecord.setRestartedSpringExecutionId(record.getRestartedSpringExecutionId());
            persistedRecord.setOperatorName(record.getOperatorName());
            persistedRecord.setReason(record.getReason());
            persistedRecord.setRequestPayload(record.getRequestPayload());
            persistedRecord.setResultPayload(record.getResultPayload());
            return record;
        });
        when(compensationRecordRepository.findById(501L)).thenReturn(Optional.of(persistedRecord));
        JobExecution restartedExecution = new JobExecution(101L, springJobInstance, new JobParameters());
        when(jobOperator.restart(failedExecution)).thenReturn(restartedExecution);

        Long restartedExecutionId = service.restartExecution(100L, "operator", "manual retry");

        assertEquals(101L, restartedExecutionId);
        verify(compensationRecordRepository, atLeastOnce()).save(any(CompensationRecord.class));
        verify(jobExecutionLogService)
                .log(
                        eq(88L),
                        eq(null),
                        eq("COMPENSATION_REQUESTED"),
                        eq("WARN"),
                        eq("Compensation requested: JOB_RESTART"),
                        eq("operator"),
                        any());
        verify(jobExecutionLogService)
                .log(
                        eq(88L),
                        eq(null),
                        eq("COMPENSATION_COMPLETED"),
                        eq("INFO"),
                        eq("Compensation completed: JOB_RESTART"),
                        eq("operator"),
                        any());
    }
}
