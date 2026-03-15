package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class FileReceptionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReceiveFileAndPersistMetadata() throws Exception {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        FileReceptionService service = new FileReceptionService(repository, fileAssetService, fileProcessLogService);

        Path source = tempDir.resolve("incoming.csv");
        Files.writeString(source, "id,name\n1,Alice\n");

        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(101L);
        when(repository.findByFileName("incoming.csv")).thenReturn(Optional.empty());
        when(fileAssetService.registerInboundFile(eq("incoming.csv"), eq(source.toString()), eq("ERP"), any(), any(), eq("ARRIVED"), any()))
                .thenReturn(fileRecord);
        when(repository.save(any(FileReceptionQueue.class))).thenAnswer(invocation -> {
            FileReceptionQueue queue = invocation.getArgument(0);
            queue.setId(1L);
            return queue;
        });

        FileReceptionQueue received = service.receiveFile("incoming.csv", source.toString(), "ERP");

        assertEquals(1L, received.getId());
        assertEquals("RECEIVED", received.getStatus());
        assertEquals("ERP", received.getSourceSystem());
        assertEquals(Files.size(source), received.getFileSize());
        assertNotNull(received.getFileHash());
        assertEquals(101L, received.getFileRecordId());
        verify(repository, times(1)).save(any(FileReceptionQueue.class));
        verify(fileProcessLogService, times(1)).log(eq(101L), eq("receiveFile"), eq("RECEIVE"),
                eq(null), eq("ARRIVED"), eq("SUCCESS"), eq(null), eq("fileReceptionJob"), eq(0),
                eq(null), eq(null), any());
    }

    @Test
    void shouldRejectDuplicateFileName() {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileReceptionService service = new FileReceptionService(repository);

        FileReceptionQueue existing = new FileReceptionQueue();
        existing.setId(7L);
        existing.setFileName("dup.csv");
        when(repository.findByFileName("dup.csv")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.receiveFile("dup.csv", "/tmp/dup.csv", "ERP")
        );

        assertEquals("File already exists: dup.csv", ex.getMessage());
    }

    @Test
    void shouldRejectSameFileNameWithDifferentContent(@TempDir Path tempDir) throws Exception {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileReceptionService service = new FileReceptionService(repository);

        Path source = tempDir.resolve("dup.csv");
        Files.writeString(source, "new-content");

        FileReceptionQueue existing = new FileReceptionQueue();
        existing.setId(7L);
        existing.setFileName("dup.csv");
        existing.setFileHash("OLD_HASH");
        existing.setFileSize(3L);
        when(repository.findByFileName("dup.csv")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.receiveFile("dup.csv", source.toString(), "ERP")
        );

        assertEquals("File name conflict with different content: dup.csv", ex.getMessage());
    }

    @Test
    void shouldRejectDuplicateFileContentAcrossDifferentFileNames(@TempDir Path tempDir) throws Exception {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        FileReceptionService service = new FileReceptionService(repository, fileAssetService, fileProcessLogService);

        Path source = tempDir.resolve("incoming-copy.csv");
        Files.writeString(source, "id,name\n1,Alice\n");
        when(repository.findByFileName("incoming-copy.csv")).thenReturn(Optional.empty());

        FileAssetRecord existingRecord = new FileAssetRecord();
        existingRecord.setId(88L);
        existingRecord.setFileNo("FR-EXISTING");
        when(fileAssetService.findDuplicateInbound(eq("ERP"), any())).thenReturn(Optional.of(existingRecord));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.receiveFile("incoming-copy.csv", source.toString(), "ERP")
        );

        assertEquals("Duplicate file content already received: FR-EXISTING", ex.getMessage());
    }

    @Test
    void shouldVerifyIntegrityForStoredFile() throws Exception {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileReceptionService service = new FileReceptionService(repository);

        Path source = tempDir.resolve("integrity.csv");
        Files.writeString(source, "payload");

        when(repository.findByFileName("integrity.csv")).thenReturn(Optional.empty());
        when(repository.save(any(FileReceptionQueue.class))).thenAnswer(invocation -> {
            FileReceptionQueue queue = invocation.getArgument(0);
            queue.setId(11L);
            return queue;
        });

        FileReceptionQueue queue = service.receiveFile("integrity.csv", source.toString(), "ERP");
        when(repository.findById(11L)).thenReturn(Optional.of(queue));

        assertTrue(service.verifyFileIntegrity(11L));

        Files.writeString(source, "payload-changed");
        assertFalse(service.verifyFileIntegrity(11L));
    }

    @Test
    void shouldIncrementRetryCountWhenMarkAsFailed() {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        FileReceptionService service = new FileReceptionService(repository, fileAssetService, fileProcessLogService);

        FileReceptionQueue queue = new FileReceptionQueue();
        queue.setId(21L);
        queue.setStatus("RECEIVED");
        queue.setRetryCount(1);
        queue.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        queue.setFileRecordId(301L);

        when(repository.findById(21L)).thenReturn(Optional.of(queue));
        when(repository.save(any(FileReceptionQueue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markAsFailed(21L, "checksum mismatch");

        assertEquals("FAILED", queue.getStatus());
        assertEquals(2, queue.getRetryCount());
        assertEquals("checksum mismatch", queue.getErrorMessage());
        assertNotNull(queue.getUpdatedAt());
        verify(fileAssetService, times(1)).markFailed(301L, "checksum mismatch");
    }

    @Test
    void shouldAggregateStatisticsByStatus() {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileReceptionService service = new FileReceptionService(repository);

        when(repository.countByStatus("RECEIVED")).thenReturn(3L);
        when(repository.countByStatus("WAITING")).thenReturn(2L);
        when(repository.countByStatus("PROCESSING")).thenReturn(1L);
        when(repository.countByStatus("COMPLETED")).thenReturn(5L);
        when(repository.countByStatus("FAILED")).thenReturn(4L);

        FileReceptionService.FileReceptionStats stats = service.getStatistics();

        assertEquals(3L, stats.receivedCount);
        assertEquals(2L, stats.waitingCount);
        assertEquals(1L, stats.processingCount);
        assertEquals(5L, stats.completedCount);
        assertEquals(4L, stats.failedCount);
    }
}
