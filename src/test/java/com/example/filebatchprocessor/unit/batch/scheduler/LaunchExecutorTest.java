package com.example.filebatchprocessor.unit.batch.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.scheduler.LaunchExecutor;
import com.example.filebatchprocessor.batch.scheduler.TaskPriority;
import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.BatchJobResolver;
import com.example.filebatchprocessor.service.JobInstanceService;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;

class LaunchExecutorTest {

    private JobOperator jobOperator;
    private BatchJobResolver jobResolver;
    private JobInstanceService jobInstanceService;

    @BeforeEach
    void setUp() {
        jobOperator = mock(JobOperator.class);
        jobResolver = mock(BatchJobResolver.class);
        jobInstanceService = mock(JobInstanceService.class);
    }

    @Test
    void shouldFailWhenJobMissing() {
        when(jobResolver.resolve("missingJob")).thenReturn(Optional.empty());
        when(jobResolver.describeAvailableJobs()).thenReturn("[" + "jobA,".repeat(80) + "]");
        LaunchExecutor launchExecutor =
                new LaunchExecutor(jobOperator, jobResolver, jobInstanceService, new Semaphore(1), 1, 1000);

        OrchestrationTaskDefinition def = definition("t1", "missingJob");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertFalse(result.isSuccess());
        assertFalse(result.isShouldReschedule());
        assertEquals("No job found for name missingJob", result.getReason());
        assertTrue(result.getReason().length() <= 255);
    }

    @Test
    void shouldRescheduleWhenNoPermit() {
        Job job = mock(Job.class);
        when(jobResolver.resolve("jobA"))
                .thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("jobA", "jobA", job)));
        LaunchExecutor launchExecutor =
                new LaunchExecutor(jobOperator, jobResolver, jobInstanceService, new Semaphore(0), 1, 1000);

        OrchestrationTaskDefinition def = definition("t1", "jobA");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertFalse(result.isSuccess());
        assertTrue(result.isShouldReschedule());
    }

    @Test
    void shouldLaunchSuccessfully() throws Exception {
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);
        BusinessJobInstance businessJobInstance = new BusinessJobInstance();
        businessJobInstance.setId(101L);
        businessJobInstance.setJobInstanceNo("JI-20260315-AAAA1111");
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobResolver.resolve("jobA"))
                .thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("jobA", "jobA", job)));
        when(jobInstanceService.createTriggeredInstance(any())).thenReturn(businessJobInstance);
        when(jobOperator.start(eq(job), any())).thenReturn(execution);

        LaunchExecutor launchExecutor =
                new LaunchExecutor(jobOperator, jobResolver, jobInstanceService, new Semaphore(1), 1, 1000);

        OrchestrationTaskDefinition def = definition("t1", "jobA");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertTrue(result.isSuccess());
        verify(jobInstanceService).createTriggeredInstance(any());
        verify(jobOperator, atLeastOnce()).start(eq(job), any());
    }

    @Test
    void shouldNotIncreasePermitWhenAcquireFails() {
        Job job = mock(Job.class);
        Semaphore permits = new Semaphore(0);
        when(jobResolver.resolve("jobA"))
                .thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("jobA", "jobA", job)));
        LaunchExecutor launchExecutor =
                new LaunchExecutor(jobOperator, jobResolver, jobInstanceService, permits, 1, 1000);

        OrchestrationTaskDefinition def = definition("t1", "jobA");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertTrue(result.isShouldReschedule());
        assertEquals(0, permits.availablePermits());
    }

    private OrchestrationTaskDefinition definition(String taskId, String jobName) {
        return OrchestrationTaskDefinition.builder()
                .id(taskId)
                .jobName(jobName)
                .priority(TaskPriority.NORMAL)
                .allowParallel(false)
                .build();
    }
}
