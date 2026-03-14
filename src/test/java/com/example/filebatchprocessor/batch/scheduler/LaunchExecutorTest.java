package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LaunchExecutorTest {

    private JobLauncher jobLauncher;
    private ObjectProvider<Map<String, Job>> jobsProvider;

    @BeforeEach
    void setUp() {
        jobLauncher = mock(JobLauncher.class);
        jobsProvider = mock(ObjectProvider.class);
    }

    @Test
    void shouldFailWhenJobMissing() {
        when(jobsProvider.getIfAvailable()).thenReturn(Map.of());
        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobsProvider, new Semaphore(1), 1, 1000);

        OrchestrationTaskDefinition def = new OrchestrationTaskDefinition();
        def.setId("t1");
        def.setJobName("missingJob");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertFalse(result.isSuccess());
        assertFalse(result.isShouldReschedule());
    }

    @Test
    void shouldRescheduleWhenNoPermit() {
        Job job = mock(Job.class);
        when(jobsProvider.getIfAvailable()).thenReturn(Map.of("jobA", job));
        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobsProvider, new Semaphore(0), 1, 1000);

        OrchestrationTaskDefinition def = new OrchestrationTaskDefinition();
        def.setId("t1");
        def.setJobName("jobA");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertFalse(result.isSuccess());
        assertTrue(result.isShouldReschedule());
    }

    @Test
    void shouldLaunchSuccessfully() throws Exception {
        Job job = mock(Job.class);
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobsProvider.getIfAvailable()).thenReturn(Map.of("jobA", job));
        when(jobLauncher.run(eq(job), any())).thenReturn(execution);

        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobsProvider, new Semaphore(1), 1, 1000);

        OrchestrationTaskDefinition def = new OrchestrationTaskDefinition();
        def.setId("t1");
        def.setJobName("jobA");
        def.setAllowParallel(false);

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertTrue(result.isSuccess());
        verify(jobLauncher, atLeastOnce()).run(eq(job), any());
    }

    @Test
    void shouldNotIncreasePermitWhenAcquireFails() {
        Job job = mock(Job.class);
        Semaphore permits = new Semaphore(0);
        when(jobsProvider.getIfAvailable()).thenReturn(Map.of("jobA", job));
        LaunchExecutor launchExecutor = new LaunchExecutor(jobLauncher, jobsProvider, permits, 1, 1000);

        OrchestrationTaskDefinition def = new OrchestrationTaskDefinition();
        def.setId("t1");
        def.setJobName("jobA");

        LaunchExecutor.LaunchResult result = launchExecutor.launch(def, "2026-03-01", 0);
        assertTrue(result.isShouldReschedule());
        assertEquals(0, permits.availablePermits());
    }
}
