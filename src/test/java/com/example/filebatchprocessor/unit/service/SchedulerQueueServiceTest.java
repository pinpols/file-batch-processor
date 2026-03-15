package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.SchedulerQueueRecord;
import com.example.filebatchprocessor.repository.SchedulerQueueRecordRepository;
import com.example.filebatchprocessor.service.SchedulerQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SchedulerQueueServiceTest {

    @Mock
    private SchedulerQueueRecordRepository repository;

    private SchedulerQueueService service;

    @BeforeEach
    void setUp() {
        service = new SchedulerQueueService(repository);
    }

    @Test
    void shouldEnqueueWhenNotDuplicate() {
        boolean enqueued = service.tryEnqueue("rk1", "taskA", "2026-03-14", null);

        assertTrue(enqueued);
        ArgumentCaptor<SchedulerQueueRecord> captor = ArgumentCaptor.forClass(SchedulerQueueRecord.class);
        verify(repository).save(captor.capture());
        assertEquals("rk1", captor.getValue().getRunKey());
        assertEquals("", captor.getValue().getRerunId());
    }

    @Test
    void shouldRejectDuplicateEnqueue() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(repository).save(any(SchedulerQueueRecord.class));

        boolean enqueued = service.tryEnqueue("rk1", "taskA", "2026-03-14", "rerun-1");

        assertFalse(enqueued);
    }

    @Test
    void shouldDequeueByRunKey() {
        service.dequeue("rk2");
        verify(repository).deleteById("rk2");
    }
}

