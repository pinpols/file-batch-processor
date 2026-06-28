package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.FileDispatchRecordService;
import com.example.filebatchprocessor.service.FileDistributionService;
import com.example.filebatchprocessor.service.FileProcessLogService;
import com.example.filebatchprocessor.service.RetryCompensationService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FileDistributionServiceTest {

    @Test
    void shouldFindPendingTasks() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        BatchMetrics batchMetrics = mock(BatchMetrics.class);

        FileDistributionService service = new FileDistributionService(
                repository,
                traceRepository,
                fileAssetService,
                fileDispatchRecordService,
                fileProcessLogService,
                retryCompensationService,
                batchMetrics);

        when(repository.findByStatus("PENDING")).thenReturn(java.util.List.of());

        var result = service.findPendingTasks();

        assertNotNull(result);
        verify(repository).findByStatus("PENDING");
    }

    @Test
    void shouldFindTaskById() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        BatchMetrics batchMetrics = mock(BatchMetrics.class);

        FileDistributionService service = new FileDistributionService(
                repository,
                traceRepository,
                fileAssetService,
                fileDispatchRecordService,
                fileProcessLogService,
                retryCompensationService,
                batchMetrics);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        var result = service.findTaskById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void shouldFindAckTimeoutTasks() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileDispatchRecordService fileDispatchRecordService = mock(FileDispatchRecordService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        BatchMetrics batchMetrics = mock(BatchMetrics.class);

        FileDistributionService service = new FileDistributionService(
                repository,
                traceRepository,
                fileAssetService,
                fileDispatchRecordService,
                fileProcessLogService,
                retryCompensationService,
                batchMetrics);

        when(fileDispatchRecordService.findAckTimeoutCandidates(any(), anyInt()))
                .thenReturn(java.util.List.of());

        var result = service.findAckTimeoutTasks(30);

        assertNotNull(result);
    }
}
