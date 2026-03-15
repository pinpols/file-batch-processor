package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.service.BatchRecoveryService;
import com.example.filebatchprocessor.service.RetryCompensationService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchRecoveryServiceTest {

    @Test
    void shouldRestartLatestFailedExecution() throws Exception {
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        BatchRecoveryService service = new BatchRecoveryService(retryCompensationService);
        when(retryCompensationService.restartLatestFailed("importJob", "SYSTEM", "Legacy recovery request")).thenReturn(101L);

        Long restarted = service.restartLatestFailed("importJob");
        assertEquals(101L, restarted);
    }
}
