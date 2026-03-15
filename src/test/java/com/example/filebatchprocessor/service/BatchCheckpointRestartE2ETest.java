package com.example.filebatchprocessor.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchCheckpointRestartE2ETest {

    @Test
    void shouldRestartFailedExecutionFromExistingExecutionId() throws Exception {
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        BatchRecoveryService service = new BatchRecoveryService(retryCompensationService);
        when(retryCompensationService.restartExecution(100L, "SYSTEM", "Legacy recovery request")).thenReturn(101L);

        Long restartedId = service.restartByExecutionId(100L);

        assertEquals(101L, restartedId);
    }

    @Test
    void shouldRejectRestartWhenExecutionIsCompleted() throws Exception {
        RetryCompensationService retryCompensationService = mock(RetryCompensationService.class);
        BatchRecoveryService service = new BatchRecoveryService(retryCompensationService);
        when(retryCompensationService.restartExecution(200L, "SYSTEM", "Legacy recovery request"))
                .thenThrow(new IllegalStateException("Execution is not restartable: COMPLETED"));

        assertThrows(IllegalStateException.class, () -> service.restartByExecutionId(200L));
    }
}
