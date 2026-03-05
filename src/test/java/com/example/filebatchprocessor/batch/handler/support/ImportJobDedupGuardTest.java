package com.example.filebatchprocessor.batch.handler.support;

import com.example.filebatchprocessor.service.ExecutionDedupService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportJobDedupGuardTest {

    @Test
    void shouldReturnDuplicateWhenAcquireFailed() {
        ExecutionDedupService dedupService = mock(ExecutionDedupService.class);
        when(dedupService.tryAcquire("k1", "2026-03-01", "r1", 60)).thenReturn(false);

        ImportJobDedupGuard guard = new ImportJobDedupGuard(dedupService);
        assertTrue(guard.isDuplicate("k1", "2026-03-01", "r1", 60));
    }

    @Test
    void shouldReturnDuplicateWithinWindow() {
        ExecutionDedupService dedupService = mock(ExecutionDedupService.class);
        when(dedupService.tryAcquire("k1", "2026-03-01", "r1", 60)).thenReturn(true);

        ImportJobDedupGuard guard = new ImportJobDedupGuard(dedupService);
        assertFalse(guard.isDuplicate("k1", "2026-03-01", "r1", 60));
        assertTrue(guard.isDuplicate("k1", "2026-03-01", "r1", 60));
    }
}
