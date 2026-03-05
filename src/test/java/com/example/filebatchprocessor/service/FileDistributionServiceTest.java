package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileDistributionServiceTest {

    @Test
    void shouldCreateTaskSuccessfully() throws Exception {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileDistributionService service = new FileDistributionService(repository, traceRepository);

        Path tempFile = Files.createTempFile("dist", ".dat");
        Files.writeString(tempFile, "payload");

        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> {
            FileDistributionTask t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        FileDistributionTask created = service.createDistributionTask("a.dat", tempFile.toString(), "HTTP", "http://x");
        assertNotNull(created.getId());
        assertEquals("PENDING", created.getStatus());
    }

    @Test
    void shouldMoveToRetryThenFailed() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileDistributionService service = new FileDistributionService(repository, traceRepository);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(1L);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setMaxRetries(2);

        when(repository.findById(1L)).thenReturn(Optional.of(task));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.markAsFailed(1L, "err1"));
        assertEquals("RETRY", task.getStatus());

        assertFalse(service.markAsFailed(1L, "err2"));
        assertEquals("FAILED", task.getStatus());
    }

    @Test
    void shouldMarkFailedForFtpNotImplemented() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        RecordTraceRepository traceRepository = mock(RecordTraceRepository.class);
        FileDistributionService service = new FileDistributionService(repository, traceRepository);

        FileDistributionTask task = new FileDistributionTask();
        task.setId(10L);
        task.setRetryCount(0);
        task.setMaxRetries(1);

        when(repository.findById(10L)).thenReturn(Optional.of(task));
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.distributeByFTP(10L, "127.0.0.1", 21, "u", "p", "/tmp");
        assertEquals("FAILED", task.getStatus());
    }
}
