package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileDistributionServiceTest {

    @Test
    void shouldCreateTaskSuccessfully() throws Exception {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        Path tempFile = Files.createTempFile("dist", ".dat");
        Files.writeString(tempFile, "payload");

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(55L);
        fileRecord.setFileNo("FR-TEST");
        fileRecord.setVersionNo(1);
        when(fileAssetService.findLatestByStoredPath(tempFile.toString())).thenReturn(Optional.empty());
        when(fileAssetService.findById(55L)).thenReturn(Optional.of(fileRecord));
        when(fileAssetService.registerOutboundFile(eq("a.dat"), eq(tempFile.toString()), eq("FILE_DISTRIBUTION"),
                eq(null), eq(null), eq(null), eq("PROCESSED"), any())).thenReturn(fileRecord);
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> {
            FileDistributionTask t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        FileDistributionTask created = service.createDistributionTask("a.dat", tempFile.toString(), "HTTP", "http://x");
        assertNotNull(created.getId());
        assertEquals("PENDING", created.getStatus());
        assertEquals(55L, created.getFileRecordId());
        verify(fileDispatchRecordService, times(1)).createPendingDispatch(fileRecord, 1L, "HTTP", "http://x", 3);
    }

    @Test
    void shouldMoveToRetryThenFailed() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(1L);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setMaxRetries(2);
        task.setFileRecordId(88L);
        task.setTargetSystem("HTTP");
        task.setTargetAddress("http://x");

        when(repository.findById(1L)).thenReturn(Optional.of(task));
        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(88L);
        when(fileAssetService.findById(88L)).thenReturn(Optional.of(fileRecord));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.markAsFailed(1L, "err1"));
        assertEquals("RETRY", task.getStatus());
        verify(fileDispatchRecordService, times(1)).markRetryPending(1L, "err1");
        verify(fileAssetService, times(1)).resetToProcessed(eq(88L), any());

        assertFalse(service.markAsFailed(1L, "err2"));
        assertEquals("FAILED", task.getStatus());
        verify(fileDispatchRecordService, times(1)).markFailed(1L, "err2");
        verify(fileAssetService, times(1)).markFailed(88L, "err2");
    }

    @Test
    void shouldMarkFailedForFtpNotImplemented() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(10L);
        task.setRetryCount(0);
        task.setMaxRetries(1);
        task.setFileRecordId(999L);
        task.setFileName("ftp.dat");
        task.setFilePath("/tmp/ftp.dat");
        task.setTargetSystem("FTP");
        task.setTargetAddress("ftp://127.0.0.1");

        when(repository.findById(10L)).thenReturn(Optional.of(task));
        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(999L);
        when(fileAssetService.findById(999L)).thenReturn(Optional.of(fileRecord));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.distributeByFTP(10L, "127.0.0.1", 21, "u", "p", "/tmp");
        assertEquals("FAILED", task.getStatus());
        verify(fileAssetService, times(1)).markFailed(eq(999L), any());
    }

    @Test
    void shouldCreateCompensationRecordWhenRetryingFailedTask() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(21L);
        task.setStatus("RETRY");
        task.setRetryCount(1);
        task.setMaxRetries(3);
        task.setFileRecordId(88L);
        task.setTargetSystem("HTTP");
        task.setTargetAddress("http://retry");

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(88L);

        BusinessJobInstance jobInstance = new BusinessJobInstance();
        jobInstance.setId(700L);

        CompensationRecord compensationRecord = new CompensationRecord();
        compensationRecord.setId(901L);

        when(repository.findById(21L)).thenReturn(Optional.of(task));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetService.findById(88L)).thenReturn(Optional.of(fileRecord));
        when(retryCompensationService.findLatestJobInstanceByRelatedFileId(88L)).thenReturn(Optional.of(jobInstance));
        when(retryCompensationService.startCompensation(any())).thenReturn(compensationRecord);

        service.retryFailedTask(21L, "operator", "manual distribution retry");

        assertEquals("PENDING", task.getStatus());
        verify(fileDispatchRecordService).markPendingForRetry(21L);
        verify(fileAssetService).resetToProcessed(eq(88L), any());
        verify(retryCompensationService).startCompensation(any());
        verify(retryCompensationService).completeCompensation(eq(901L), eq(700L), eq(null), any());
    }
}
