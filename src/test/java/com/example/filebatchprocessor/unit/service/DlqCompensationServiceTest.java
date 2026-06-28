package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.service.BatchJobResolver;
import com.example.filebatchprocessor.service.DlqCompensationService;
import com.example.filebatchprocessor.service.JobInstanceService;
import com.example.filebatchprocessor.service.PartitionedImportService;
import com.example.filebatchprocessor.service.RetryCompensationService;
import com.example.filebatchprocessor.service.TaskConfigService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

class DlqCompensationServiceTest {

    @Test
    void batchReplayUsesStableRunKeyForSameDlqAttempt() throws Exception {
        DlqRecordRepository dlqRepository = mock(DlqRecordRepository.class);
        PartitionedImportService partitionedImportService = mock(PartitionedImportService.class);
        JobOperator jobOperator = mock(JobOperator.class);
        Job replayJob = mock(Job.class);
        BatchJobResolver batchJobResolver = mock(BatchJobResolver.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);

        when(replayJob.getName()).thenReturn(BatchJobNames.FILE_IMPORT_JOB);
        when(batchJobResolver.resolve(BatchJobNames.FILE_IMPORT_JOB))
                .thenReturn(Optional.of(new BatchJobResolver.ResolvedJob(
                        BatchJobNames.FILE_IMPORT_JOB, BatchJobNames.FILE_IMPORT_JOB, replayJob)));
        DlqRecord record = new DlqRecord();
        record.setId(42L);
        record.setJobName(BatchJobNames.FILE_IMPORT_JOB);
        record.setParams("taskId=import-main&batchDate=2026-03-15&input.file.name=/data/in.csv");
        record.setReplayCount(2L);
        when(dlqRepository
                        .findTop100ByHandledFalseAndManualRequiredFalseAndRetryableTrueAndNextRetryAtBeforeOrderByCreatedAtAsc(
                                any(LocalDateTime.class)))
                .thenReturn(List.of(record));

        CompensationRecord compensation = new CompensationRecord();
        compensation.setId(700L);
        when(retryCompensationService.startCompensation(any())).thenReturn(compensation);

        BusinessJobInstance businessJobInstance = new BusinessJobInstance();
        businessJobInstance.setId(900L);
        businessJobInstance.setJobInstanceNo("JOB-900");
        when(jobInstanceService.createTriggeredInstance(any())).thenReturn(businessJobInstance);
        when(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenReturn(new JobExecution(
                        123L, new JobInstance(124L, BatchJobNames.FILE_IMPORT_JOB), new JobParameters()));

        DlqCompensationService service = new DlqCompensationService(
                dlqRepository,
                partitionedImportService,
                jobOperator,
                replayJob,
                batchJobResolver,
                taskConfigService,
                jobInstanceService,
                retryCompensationService,
                5,
                60_000L);

        service.replayPending(1);

        ArgumentCaptor<JobInstanceService.CreateRequest> requestCaptor =
                ArgumentCaptor.forClass(JobInstanceService.CreateRequest.class);
        verify(jobInstanceService).createTriggeredInstance(requestCaptor.capture());
        assertEquals("dlq-42-replay-2-import-main", requestCaptor.getValue().runKey());
    }
}
