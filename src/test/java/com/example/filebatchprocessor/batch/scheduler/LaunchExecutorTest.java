package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.BatchJobResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;

import java.util.Optional;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LaunchExecutorTest {

    private JobLauncher jobLauncher;
    private BatchJobResolver jobResolver;

    @BeforeEach
    void setUp() {
        jobLauncher = mock(JobLauncher.class);
        jobResolver = mock(BatchJobResolver.class);
    }

    @Test
    void shouldFailWhenJobMissing() {
        when(jobResolver.resolve("missingJob")).thenReturn(Optional.empty());
        when(jobResolver.describeAvailableJobs()).thenReturn("[jobA]");
        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobResolver, new Semaphore(1), 1, 1000);

        OrchestrationTaskDefinition def = definition("t1", "missingJob");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertFalse(result.isSuccess());
        assertFalse(result.isShouldReschedule());
    }

    @Test
    void shouldRescheduleWhenNoPermit() {
        Job job = mock(Job.class);
        when(jobResolver.resolve("jobA")).thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("jobA", "jobA", job)));
        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobResolver, new Semaphore(0), 1, 1000);

        OrchestrationTaskDefinition def = definition("t1", "jobA");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertFalse(result.isSuccess());
        assertTrue(result.isShouldReschedule());
    }

    @Test
    void shouldLaunchSuccessfully() throws Exception {
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobResolver.resolve("jobA")).thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("jobA", "jobA", job)));
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);

        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobResolver, new Semaphore(1), 1, 1000);

        OrchestrationTaskDefinition def = definition("t1", "jobA");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertTrue(result.isSuccess());
        verify(jobLauncher, atLeastOnce()).run(eq(job), any());
    }

    @Test
    void shouldNotIncreasePermitWhenAcquireFails() {
        Job job = mock(Job.class);
        Semaphore permits = new Semaphore(0);
        when(jobResolver.resolve("jobA")).thenReturn(Optional.of(new BatchJobResolver.ResolvedJob("jobA", "jobA", job)));
        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobResolver, permits, 1, 1000);

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
