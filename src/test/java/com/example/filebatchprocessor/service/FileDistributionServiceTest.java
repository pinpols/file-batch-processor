package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
        verify(fileDispatchRecordService, times(1)).createPendingDispatch(fileRecord, 1L, "HTTP", "http://x", 3,
                false, null, null);
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
        verify(fileDispatchRecordService, times(1)).markRetryPending(1L, "err1", null);
        verify(fileAssetService, times(1)).resetToProcessed(eq(88L), any());

        assertFalse(service.markAsFailed(1L, "err2"));
        assertEquals("FAILED", task.getStatus());
        verify(fileDispatchRecordService, times(1)).markFailed(1L, "err2", null);
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
        verify(fileDispatchRecordService).markPendingForRetry(21L, null, false);
        verify(fileAssetService).resetToProcessed(eq(88L), any());
        verify(retryCompensationService).startCompensation(any());
        verify(retryCompensationService).completeCompensation(eq(901L), eq(700L), eq(null), any());
    }

    @Test
    void shouldAcknowledgeDispatchAndKeepSuccessStatus() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(31L);
        task.setStatus("SUCCESS");
        task.setRetryCount(0);
        task.setMaxRetries(3);
        task.setFileRecordId(88L);
        task.setTargetSystem("HTTP");
        task.setTargetAddress("http://ack");

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(88L);

        FileDispatchRecord dispatchRecord = new FileDispatchRecord();
        dispatchRecord.setId(501L);
        dispatchRecord.setDispatchNo("FD-ACK-1");
        dispatchRecord.setAckStatus("ACKED");

        when(repository.findById(31L)).thenReturn(Optional.of(task));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetService.findById(88L)).thenReturn(Optional.of(fileRecord));
        when(fileDispatchRecordService.markAckReceived(eq(31L), eq(700L), eq(true), eq("operator"), eq("ok"), any()))
                .thenReturn(Optional.of(dispatchRecord));

        service.acknowledgeDispatch(31L, true, "operator", "ok", Map.of("ackCode", "200"), 700L);

        assertEquals("SUCCESS", task.getStatus());
        verify(fileDispatchRecordService).markAckReceived(eq(31L), eq(700L), eq(true), eq("operator"), eq("ok"), any());
    }

    @Test
    void shouldMoveRejectedAckBackToRetry() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(32L);
        task.setStatus("SUCCESS");
        task.setRetryCount(1);
        task.setMaxRetries(3);
        task.setFileRecordId(89L);
        task.setTargetSystem("HTTP");
        task.setTargetAddress("http://reject");

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(89L);

        FileDispatchRecord dispatchRecord = new FileDispatchRecord();
        dispatchRecord.setId(502L);
        dispatchRecord.setDispatchNo("FD-ACK-2");
        dispatchRecord.setAckStatus("REJECTED");

        when(repository.findById(32L)).thenReturn(Optional.of(task));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetService.findById(89L)).thenReturn(Optional.of(fileRecord));
        when(fileDispatchRecordService.markAckReceived(eq(32L), eq(701L), eq(false), eq("operator"), eq("rejected"), any()))
                .thenReturn(Optional.of(dispatchRecord));

        service.acknowledgeDispatch(32L, false, "operator", "rejected", Map.of("ackCode", "REJECT"), 701L);

        assertEquals("RETRY", task.getStatus());
        verify(fileAssetService).resetToProcessed(eq(89L), any());
    }

    @Test
    void shouldScheduleResendAndTrackCompensation() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(33L);
        task.setStatus("SUCCESS");
        task.setRetryCount(1);
        task.setMaxRetries(3);
        task.setFileRecordId(90L);
        task.setTargetSystem("HTTP");
        task.setTargetAddress("http://resend");

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(90L);

        BusinessJobInstance jobInstance = new BusinessJobInstance();
        jobInstance.setId(702L);

        CompensationRecord compensationRecord = new CompensationRecord();
        compensationRecord.setId(902L);

        when(repository.findById(33L)).thenReturn(Optional.of(task));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetService.findById(90L)).thenReturn(Optional.of(fileRecord));
        when(retryCompensationService.findLatestJobInstanceByRelatedFileId(90L)).thenReturn(Optional.of(jobInstance));
        when(retryCompensationService.startCompensation(any())).thenReturn(compensationRecord);

        service.scheduleResend(33L, "operator", "manual resend", 702L);

        assertEquals("RETRY", task.getStatus());
        verify(fileDispatchRecordService).markPendingForRetry(33L, 702L, true);
        verify(retryCompensationService).completeCompensation(eq(902L), eq(702L), eq(null), any());
    }

    @Test
    void shouldMarkAckTimeoutAndMoveTaskToRetry() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        FileDistributionService service = new FileDistributionService(
                repository, traceRepository, fileAssetService, fileDispatchRecordService, fileProcessLogService, retryCompensationService);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(34L);
        task.setStatus("SUCCESS");
        task.setRetryCount(1);
        task.setMaxRetries(3);
        task.setFileRecordId(91L);
        task.setTargetSystem("HTTP");
        task.setTargetAddress("http://timeout");

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(91L);

        BusinessJobInstance jobInstance = new BusinessJobInstance();
        jobInstance.setId(703L);

        CompensationRecord compensationRecord = new CompensationRecord();
        compensationRecord.setId(903L);

        FileDispatchRecord dispatchRecord = new FileDispatchRecord();
        dispatchRecord.setId(503L);
        dispatchRecord.setAckStatus("TIMEOUT");

        when(repository.findById(34L)).thenReturn(Optional.of(task));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetService.findById(91L)).thenReturn(Optional.of(fileRecord));
        when(retryCompensationService.findLatestJobInstanceByRelatedFileId(91L)).thenReturn(Optional.of(jobInstance));
        when(retryCompensationService.startCompensation(any())).thenReturn(compensationRecord);
        when(fileDispatchRecordService.markAckTimeout(eq(34L), eq(703L), eq("ack timeout"), any()))
                .thenReturn(Optional.of(dispatchRecord));

        service.markAckTimedOut(34L, "ack timeout", 703L);

        assertEquals("RETRY", task.getStatus());
        verify(fileAssetService).resetToProcessed(eq(91L), any());
        verify(retryCompensationService).completeCompensation(eq(903L), eq(703L), eq(null), any());
    }
}
