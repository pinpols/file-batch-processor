package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.exception.InvalidFileStateTransitionException;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.service.FileAssetStateMachineService;
import com.example.filebatchprocessor.service.FileProcessLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FileAssetStateMachineServiceTest {

    @Test
    void shouldAdvanceReceptionLifecycle() {
        FileAssetRecordRepository repository = mock(FileAssetRecordRepository.class);
        FileProcessLogService processLogService = mock(FileProcessLogService.class);
        FileAssetStateMachineService service =
                new FileAssetStateMachineService(repository, processLogService, new ObjectMapper());

        FileAssetRecord record = new FileAssetRecord();
        record.setId(1L);
        record.setStatus("ARRIVED");

        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(repository.save(any(FileAssetRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var ready = service.transition(
                1L,
                com.example.filebatchprocessor.model.FileAssetStatus.READY,
                "integrity-ok",
                Map.of("verified", true));
        assertEquals("ARRIVED", ready.from().name());
        assertEquals("READY", ready.to().name());
        assertEquals("READY", record.getStatus());
        assertNotNull(record.getReadyTime());
        assertEquals(Boolean.TRUE, record.getIntegrityVerified());

        var processing = service.transition(
                1L, com.example.filebatchprocessor.model.FileAssetStatus.PROCESSING, "start-processing", null);
        assertEquals("READY", processing.from().name());
        assertEquals("PROCESSING", processing.to().name());

        var processed = service.transition(
                1L, com.example.filebatchprocessor.model.FileAssetStatus.PROCESSED, "finish-processing", null);
        assertEquals("PROCESSING", processed.from().name());
        assertEquals("PROCESSED", processed.to().name());
        assertNotNull(record.getProcessedTime());
    }

    @Test
    void shouldRejectIllegalTransition() {
        FileAssetRecordRepository repository = mock(FileAssetRecordRepository.class);
        FileProcessLogService processLogService = mock(FileProcessLogService.class);
        FileAssetStateMachineService service =
                new FileAssetStateMachineService(repository, processLogService, new ObjectMapper());

        FileAssetRecord record = new FileAssetRecord();
        record.setId(2L);
        record.setStatus("ARRIVED");

        when(repository.findById(2L)).thenReturn(Optional.of(record));

        assertThrows(
                InvalidFileStateTransitionException.class,
                () -> service.transition(
                        2L, com.example.filebatchprocessor.model.FileAssetStatus.DISPATCHING, "skip-ahead", null));
    }

    @Test
    void shouldAllowDispatchBackToProcessedForResend() {
        FileAssetRecordRepository repository = mock(FileAssetRecordRepository.class);
        FileProcessLogService processLogService = mock(FileProcessLogService.class);
        FileAssetStateMachineService service =
                new FileAssetStateMachineService(repository, processLogService, new ObjectMapper());

        FileAssetRecord record = new FileAssetRecord();
        record.setId(3L);
        record.setStatus("DISPATCHED");

        when(repository.findById(3L)).thenReturn(Optional.of(record));
        when(repository.save(any(FileAssetRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var reset = service.transition(
                3L,
                com.example.filebatchprocessor.model.FileAssetStatus.PROCESSED,
                "ack-timeout",
                Map.of("ackTimeout", true));
        assertEquals("DISPATCHED", reset.from().name());
        assertEquals("PROCESSED", reset.to().name());
        assertEquals("PROCESSED", record.getStatus());
    }
}
