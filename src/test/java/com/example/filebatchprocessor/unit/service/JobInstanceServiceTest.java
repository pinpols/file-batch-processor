package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.BusinessJobInstanceStatus;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.example.filebatchprocessor.service.JobExecutionLogService;
import com.example.filebatchprocessor.service.JobInstanceParameters;
import com.example.filebatchprocessor.service.JobInstanceService;
import com.example.filebatchprocessor.service.JobStepInstanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobInstanceServiceTest {

    @Test
    void shouldCreateTriggeredInstance() {
        BusinessJobInstanceRepository repository = mock(BusinessJobInstanceRepository.class);
        JobStepInstanceService jobStepInstanceService = mock(JobStepInstanceService.class);
        JobExecutionLogService jobExecutionLogService = mock(JobExecutionLogService.class);
        JobInstanceService service = new JobInstanceService(
                repository,
                jobStepInstanceService,
                jobExecutionLogService,
                new ObjectMapper()
        );

        when(repository.save(any(BusinessJobInstance.class))).thenAnswer(invocation -> {
            BusinessJobInstance instance = invocation.getArgument(0);
            instance.setId(700L);
            return instance;
        });

        BusinessJobInstance created = service.createTriggeredInstance(new JobInstanceService.CreateRequest(
                "task-import",
                "processFileJob",
                "MANUAL",
                "tester",
                "2026-03-15",
                "BATCH-1",
                "manual-task-import-1",
                false,
                false,
                true,
                900L,
                java.util.Map.of("batchDate", "2026-03-15")
        ));

        assertEquals(700L, created.getId());
        assertEquals("task-import", created.getTaskId());
        assertEquals("processFileJob", created.getJobName());
        assertEquals("MANUAL", created.getTriggerSource());
        assertEquals("tester", created.getOperatorName());
        assertEquals(BusinessJobInstanceStatus.TRIGGERED.name(), created.getStatus());
        assertEquals(900L, created.getRelatedFileId());
        assertNotNull(created.getRequestPayload());

        verify(jobExecutionLogService).log(eq(700L), eq(null), eq("INSTANCE_CREATED"), eq("INFO"),
                eq("Business job instance created"), eq("tester"), any());
    }

    @Test
    void shouldUpdateInstanceFromSpringBatchCompletion() {
        BusinessJobInstanceRepository repository = mock(BusinessJobInstanceRepository.class);
        JobStepInstanceService jobStepInstanceService = mock(JobStepInstanceService.class);
        JobExecutionLogService jobExecutionLogService = mock(JobExecutionLogService.class);
        JobInstanceService service = new JobInstanceService(
                repository,
                jobStepInstanceService,
                jobExecutionLogService,
                new ObjectMapper()
        );

        BusinessJobInstance stored = new BusinessJobInstance();
        stored.setId(701L);
        stored.setTaskId("task-export");
        stored.setJobName("dataExportJob");
        stored.setStatus(BusinessJobInstanceStatus.TRIGGERED.name());
        stored.setOperatorName("scheduler");

        when(repository.findById(701L)).thenReturn(Optional.of(stored));
        when(repository.save(any(BusinessJobInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobInstance springJobInstance = new JobInstance(31L, "dataExportJob");
        JobExecution jobExecution = mock(JobExecution.class);
        StepExecution stepExecution = mock(StepExecution.class);
        LocalDateTime start = LocalDateTime.now().minusSeconds(5);
        LocalDateTime end = LocalDateTime.now();

        when(jobExecution.getId()).thenReturn(401L);
        when(jobExecution.getJobInstance()).thenReturn(springJobInstance);
        when(jobExecution.getJobParameters()).thenReturn(new JobParametersBuilder()
                .addLong(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID, 701L)
                .addString(JobInstanceParameters.BUSINESS_JOB_INSTANCE_NO, "JI-20260315-XYZ12345")
                .toJobParameters());
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        when(jobExecution.getStepExecutions()).thenReturn(Set.of(stepExecution));
        when(jobExecution.getStartTime()).thenReturn(start);
        when(jobExecution.getEndTime()).thenReturn(end);
        when(jobExecution.getAllFailureExceptions()).thenReturn(java.util.List.of());

        when(stepExecution.getReadCount()).thenReturn(10L);
        when(stepExecution.getWriteCount()).thenReturn(8L);
        when(stepExecution.getSkipCount()).thenReturn(1L);
        when(stepExecution.getFilterCount()).thenReturn(0L);

        service.markRunning(jobExecution);
        service.completeFromBatch(jobExecution);

        assertEquals(BusinessJobInstanceStatus.PARTIAL_SUCCESS.name(), stored.getStatus());
        assertEquals(401L, stored.getSpringBatchExecutionId());
        assertEquals(31L, stored.getSpringBatchInstanceId());
        assertNotNull(stored.getStartTime());
        assertNotNull(stored.getEndTime());
        assertNotNull(stored.getDurationMs());
        assertNotNull(stored.getResultSummary());

        verify(jobStepInstanceService).replaceFromSpringBatch(701L, jobExecution, "scheduler");
        verify(jobExecutionLogService).log(eq(701L), eq(null), eq("JOB_STARTED"), eq("INFO"),
                eq("Spring Batch job started"), eq("scheduler"), any());
        verify(jobExecutionLogService).log(eq(701L), eq(null), eq("JOB_FINISHED"), eq("INFO"),
                eq("Spring Batch job finished: PARTIAL_SUCCESS"), eq("scheduler"), any());
    }
}
