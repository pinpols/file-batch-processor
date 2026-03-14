package com.example.filebatchprocessor.service;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.step.StepExecution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchCheckpointRestartE2ETest {

    @Test
    void shouldRestartFailedExecutionFromExistingExecutionId() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        JobExplorer jobExplorer = mock(JobExplorer.class);
        BatchRecoveryService service = new BatchRecoveryService(jobOperator, jobExplorer);

        JobInstance instance = new JobInstance(1L, "processFileJob");
        JobExecution failed = new JobExecution(100L, instance, new JobParameters());
        failed.setStatus(BatchStatus.FAILED);
        StepExecution failedStep = new StepExecution("importStep", failed);
        failedStep.setStatus(BatchStatus.FAILED);
        failed.addStepExecution(failedStep);

        when(jobExplorer.getJobExecution(100L)).thenReturn(failed);
        when(jobOperator.restart(100L)).thenReturn(101L);

        Long restartedId = service.restartByExecutionId(100L);

        assertEquals(101L, restartedId);
        verify(jobOperator).restart(100L);
    }

    @Test
    void shouldRejectRestartWhenExecutionIsCompleted() {
        JobOperator jobOperator = mock(JobOperator.class);
        JobExplorer jobExplorer = mock(JobExplorer.class);
        BatchRecoveryService service = new BatchRecoveryService(jobOperator, jobExplorer);

        JobInstance instance = new JobInstance(1L, "processFileJob");
        JobExecution completed = new JobExecution(200L, instance, new JobParameters());
        completed.setStatus(BatchStatus.COMPLETED);
        when(jobExplorer.getJobExecution(200L)).thenReturn(completed);

        assertThrows(IllegalStateException.class, () -> service.restartByExecutionId(200L));
    }
}
