package com.example.filebatchprocessor.service;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.explore.JobExplorer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchRecoveryServiceTest {

    @Test
    void shouldRestartLatestFailedExecution() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        JobExplorer jobExplorer = mock(JobExplorer.class);
        BatchRecoveryService service = new BatchRecoveryService(jobOperator, jobExplorer);

        JobInstance instance = new JobInstance(1L, "importJob");
        JobExecution failed = new JobExecution(100L, instance, new JobParameters());
        failed.setStatus(BatchStatus.FAILED);
        failed.setCreateTime(LocalDateTime.now());

        when(jobExplorer.findRunningJobExecutions("importJob")).thenReturn(Set.of());
        when(jobExplorer.getJobInstances("importJob", 0, 100)).thenReturn(List.of(instance));
        when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(failed));
        when(jobExplorer.getJobExecution(100L)).thenReturn(failed);
        when(jobOperator.restart(100L)).thenReturn(101L);

        Long restarted = service.restartLatestFailed("importJob");
        assertEquals(101L, restarted);
    }
}
