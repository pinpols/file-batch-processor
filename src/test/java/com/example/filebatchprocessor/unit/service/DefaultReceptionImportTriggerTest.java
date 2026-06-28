package com.example.filebatchprocessor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ReceptionGroup;
import com.example.filebatchprocessor.params.ImportJobParams;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import com.example.filebatchprocessor.service.DefaultReceptionImportTrigger;
import com.example.filebatchprocessor.service.FileReceptionService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

@ExtendWith(MockitoExtension.class)
class DefaultReceptionImportTriggerTest {

    @Mock
    private FileReceptionQueueRepository queueRepository;

    @Mock
    private FileReceptionService fileReceptionService;

    @Mock
    private ReceptionGroupRepository groupRepository;

    @Mock
    private JobOperator jobOperator;

    @Mock
    private Job fileImportJob;

    @Test
    void triggersExistingImportJobAndMarksQueueCompleted() throws Exception {
        FileReceptionQueue queue = queue(42L, "daily.xlsx", "C:/inbox/daily.xlsx", "RECEIVED");
        queue.setReceptionGroupId(7L);
        ReceptionGroup group = new ReceptionGroup();
        group.setId(7L);
        group.setBizDate("2026-03-14");
        JobExecution execution =
                new JobExecution(99L, new JobInstance(100L, BatchJobNames.FILE_IMPORT_JOB), new JobParameters());
        execution.setStatus(BatchStatus.COMPLETED);

        when(queueRepository.findById(42L)).thenReturn(Optional.of(queue));
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(jobOperator.run(any(Job.class), any(JobParameters.class))).thenReturn(execution);

        DefaultReceptionImportTrigger trigger = trigger();
        trigger.triggerImport(42L);

        ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).run(any(Job.class), paramsCaptor.capture());
        JobParameters params = paramsCaptor.getValue();
        assertThat(params.getString(ImportJobParams.KEY_INPUT_FILE_NAME)).isEqualTo("C:/inbox/daily.xlsx");
        assertThat(params.getString(ImportJobParams.KEY_BATCH_DATE)).isEqualTo("2026-03-14");
        assertThat(params.getString(ImportJobParams.KEY_FILE_FORMAT)).isEqualTo("EXCEL");
        assertThat(params.getString("reception.queue.id")).isEqualTo("42");
        verify(fileReceptionService).markAsReady(42L);
        verify(fileReceptionService).markAsProcessing(42L);
        verify(fileReceptionService).markAsCompleted(42L);
        verify(fileReceptionService, never()).markAsFailed(any(), any());
    }

    @Test
    void skipsAlreadyCompletedQueue() throws Exception {
        when(queueRepository.findById(42L))
                .thenReturn(Optional.of(queue(42L, "daily.csv", "C:/inbox/daily.csv", "COMPLETED")));

        trigger().triggerImport(42L);

        verify(jobOperator, never()).run(any(Job.class), any(JobParameters.class));
        verify(fileReceptionService, never()).markAsProcessing(any());
    }

    private DefaultReceptionImportTrigger trigger() {
        return new DefaultReceptionImportTrigger(
                queueRepository, fileReceptionService, groupRepository, jobOperator, fileImportJob);
    }

    private FileReceptionQueue queue(Long id, String fileName, String filePath, String status) {
        FileReceptionQueue queue = new FileReceptionQueue();
        queue.setId(id);
        queue.setFileName(fileName);
        queue.setFilePath(filePath);
        queue.setStatus(status);
        return queue;
    }
}
