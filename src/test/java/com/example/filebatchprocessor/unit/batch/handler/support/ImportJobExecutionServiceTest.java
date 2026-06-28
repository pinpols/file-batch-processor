package com.example.filebatchprocessor.unit.batch.handler.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.handler.support.ImportJobExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ImportJobExecutionServiceTest {

    private JobOperator jobOperator;
    private Job job;
    private JobParameters params;
    private ImportJobExecutionService service;

    @BeforeEach
    void setUp() {
        jobOperator = mock(JobOperator.class);
        job = mock(Job.class);
        params = new JobParameters();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        service = new ImportJobExecutionService(jobOperator, executor);
    }

    @Test
    void shouldCompleteWithoutRetry() throws Exception {
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobOperator.start(eq(job), any(JobParameters.class))).thenReturn(execution);

        BatchStatus status = service.executeWithRetry(job, params, 0, 1, 0, 0);
        assertEquals(BatchStatus.COMPLETED, status);
        verify(jobOperator, times(1)).start(eq(job), any(JobParameters.class));
    }

    @Test
    void shouldRetryAndThenSucceed() throws Exception {
        JobExecution success = mock(JobExecution.class);
        when(success.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobOperator.start(eq(job), any(JobParameters.class)))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(success);

        BatchStatus status = service.executeWithRetry(job, params, 1, 1, 10_000, 0);
        assertEquals(BatchStatus.COMPLETED, status);
        verify(jobOperator, times(2)).start(eq(job), any(JobParameters.class));
    }
}
