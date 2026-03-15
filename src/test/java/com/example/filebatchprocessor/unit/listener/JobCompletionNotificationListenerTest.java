package com.example.filebatchprocessor.unit.listener;

import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.QualityGateResultRepository;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.FileProcessLogService;
import com.example.filebatchprocessor.service.JobInstanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobCompletionNotificationListenerTest {

    @Test
    void shouldRegisterGeneratedFileForCompletedDataExportJob(@TempDir Path tempDir) throws Exception {
        BatchRunRecordRepository batchRunRecordRepository = mock(BatchRunRecordRepository.class);
        ImportedRecordRepository importedRecordRepository = mock(ImportedRecordRepository.class);
        QualityGateResultRepository qualityGateResultRepository = mock(QualityGateResultRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        JobInstanceService jobInstanceService = mock(JobInstanceService.class);

        when(batchRunRecordRepository.findByJobExecutionId(99L)).thenReturn(Optional.empty());
        when(batchRunRecordRepository.save(any(BatchRunRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(500L);
        when(fileAssetService.registerOutboundFile(eq("export.csv"), any(), eq("DATA_EXPORT"), eq("2026-03-15"),
                isNull(), isNull(), eq("PROCESSED"), anyMap())).thenReturn(fileRecord);

        JobCompletionNotificationListener listener = new JobCompletionNotificationListener(
                batchRunRecordRepository,
                importedRecordRepository,
                qualityGateResultRepository,
                fileAssetService,
                fileProcessLogService,
                jobInstanceService,
                0.0,
                100
        );

        Path output = tempDir.resolve("export.csv");
        Files.writeString(output, "id,business_key\n1,key1\n");

        JobExecution jobExecution = mock(JobExecution.class);
        JobInstance jobInstance = mock(JobInstance.class);
        StepExecution stepExecution = mock(StepExecution.class);

        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("dataExportJob");
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());
        when(jobExecution.getId()).thenReturn(99L);
        when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        when(jobExecution.getAllFailureExceptions()).thenReturn(java.util.List.of());
        when(jobExecution.getJobParameters()).thenReturn(new org.springframework.batch.core.job.parameters.JobParametersBuilder()
                .addString("output.file.name", output.toString())
                .addString("batchDate", "2026-03-15")
                .toJobParameters());
        when(jobExecution.getStepExecutions()).thenReturn(Set.of(stepExecution));

        when(stepExecution.getStepName()).thenReturn("exportStep");
        when(stepExecution.getExecutionContext()).thenReturn(new ExecutionContext());
        when(stepExecution.getReadCount()).thenReturn(1L);
        when(stepExecution.getWriteCount()).thenReturn(1L);

        listener.beforeJob(jobExecution);
        listener.afterJob(jobExecution);

        verify(jobInstanceService).markRunning(jobExecution);
        verify(jobInstanceService).completeFromBatch(jobExecution);
        verify(fileAssetService).registerOutboundFile(eq("export.csv"), eq(output.toString()), eq("DATA_EXPORT"),
                eq("2026-03-15"), isNull(), isNull(), eq("PROCESSED"), anyMap());
        verify(fileProcessLogService).log(eq(500L), eq("afterJob"), eq("EXPORT"), isNull(), eq("PROCESSED"),
                eq("SUCCESS"), isNull(), eq("dataExportJob"), eq(0), isNull(), isNull(), anyMap());
    }
}
