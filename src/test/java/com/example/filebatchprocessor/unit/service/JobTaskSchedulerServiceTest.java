package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.service.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;

class JobTaskSchedulerServiceTest {

    @Test
    void shouldTriggerJobWithTaskDefinitionAndMergedParameters() throws Exception {
        JobLauncher jobLauncher = mock(JobLauncher.class);
        BatchJobResolver batchJobResolver = mock(BatchJobResolver.class);
        JobOperator jobOperator = mock(JobOperator.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);

        JobTaskSchedulerService service = new JobTaskSchedulerService(
                jobLauncher,
                batchJobResolver,
                jobOperator,
                taskConfigService,
                jobInstanceService,
                retryCompensationService);

        TaskDefinition def = new TaskDefinition();
        def.setTaskId("task-import");
        def.setJobName("processFileJob");
        when(taskConfigService.getTaskDefinition("task-import")).thenReturn(def);
        when(taskConfigService.getTaskParametersAsMap("task-import"))
                .thenReturn(new HashMap<>(Map.of("batchDate", "2026-03-14", "source", "db-default")));
        BusinessJobInstance businessJobInstance = new BusinessJobInstance();
        businessJobInstance.setId(501L);
        businessJobInstance.setJobInstanceNo("JI-20260315-ABCD1234");
        when(jobInstanceService.createTriggeredInstance(any())).thenReturn(businessJobInstance);

        Job job = mock(Job.class);
        when(batchJobResolver.resolve("processFileJob"))
                .thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("processFileJob", "importJob", job)));

        JobExecution execution = mock(JobExecution.class);
        when(execution.getId()).thenReturn(1001L);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(execution);

        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("source", "manual");
        requestParams.put("shardIndex", 2);

        String result = service.triggerJob("task-import", requestParams, "tester");

        assertTrue(result.contains("taskId=task-import"));
        assertTrue(result.contains("jobName=processFileJob"));
        assertTrue(result.contains("executionId=1001"));

        verify(jobLauncher).run(eq(job), any(JobParameters.class));
    }

    @Test
    void shouldFallbackToTaskIdWhenTaskDefinitionMissing() throws Exception {
        JobLauncher jobLauncher = mock(JobLauncher.class);
        BatchJobResolver batchJobResolver = mock(BatchJobResolver.class);
        JobOperator jobOperator = mock(JobOperator.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        JobTaskSchedulerService service = new JobTaskSchedulerService(
                jobLauncher,
                batchJobResolver,
                jobOperator,
                taskConfigService,
                jobInstanceService,
                mock(RetryCompensationService.class));

        when(taskConfigService.getTaskDefinition("dataExportJob")).thenThrow(new IllegalArgumentException("not found"));
        when(taskConfigService.getTaskParametersAsMap("dataExportJob")).thenReturn(Map.of());
        BusinessJobInstance businessJobInstance = new BusinessJobInstance();
        businessJobInstance.setId(502L);
        businessJobInstance.setJobInstanceNo("JI-20260315-EFGH5678");
        when(jobInstanceService.createTriggeredInstance(any())).thenReturn(businessJobInstance);

        Job job = mock(Job.class);
        when(batchJobResolver.resolve("dataExportJob"))
                .thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("dataExportJob", "dataExportJob", job)));
        JobExecution execution = mock(JobExecution.class);
        when(execution.getId()).thenReturn(2002L);
        when(execution.getStatus()).thenReturn(BatchStatus.STARTING);
        when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(execution);

        String result = service.triggerJob("dataExportJob", Map.of("batchDate", "2026-03-14"), "tester");

        assertTrue(result.contains("jobName=dataExportJob"));
    }

    @Test
    void shouldReturnValidationErrorForInvalidTaskId() {
        JobTaskSchedulerService service = new JobTaskSchedulerService(
                mock(JobLauncher.class),
                mock(BatchJobResolver.class),
                mock(JobOperator.class),
                mock(TaskConfigService.class),
                mock(JobInstanceService.class),
                mock(RetryCompensationService.class));

        String result = service.triggerJob(" ", Map.of(), "tester");
        assertTrue(result.contains("taskId is required"));
    }

    @Test
    void shouldRetryAndStopExecution() throws Exception {
        JobLauncher jobLauncher = mock(JobLauncher.class);
        BatchJobResolver batchJobResolver = mock(BatchJobResolver.class);
        JobOperator jobOperator = mock(JobOperator.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        JobTaskSchedulerService service = new JobTaskSchedulerService(
                jobLauncher,
                batchJobResolver,
                jobOperator,
                taskConfigService,
                mock(JobInstanceService.class),
                retryCompensationService);

        when(retryCompensationService.restartExecution(88L, "operator", "Manual retry from jobTaskSchedulerService"))
                .thenReturn(99L);
        String retryResult = service.retryJobExecution(88L, "operator");
        assertTrue(retryResult.contains("restartedExecutionId=99"));

        service.stopJobExecution(88L, "operator");
        verify(jobOperator).stop(88L);
    }

    @Test
    void shouldIgnoreInvalidStopExecutionRequest() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        JobTaskSchedulerService service = new JobTaskSchedulerService(
                mock(JobLauncher.class),
                mock(BatchJobResolver.class),
                jobOperator,
                mock(TaskConfigService.class),
                mock(JobInstanceService.class),
                mock(RetryCompensationService.class));

        service.stopJobExecution(null, "operator");
        service.stopJobExecution(0L, "operator");
        verify(jobOperator, never()).stop(any(Long.class));
    }
}
