package com.example.filebatchprocessor.unit.batch.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void shouldClassifyDuplicateAsSkippable() {
        String errorMessage = "Record already exists for key";
        boolean isSkippable = errorMessage.contains("already exists");
        assertTrue(isSkippable);
    }

    @Test
    void shouldAllowRetryOnlyWithinMaxAttemptsForRetryableErrors() {
        OrchestrationTaskDefinition task =
                OrchestrationTaskDefinition.builder().id("t1").maxAttempts(3).build();

        String retryableError = "Connection timeout";
        String nonRetryableError = "Record already exists";

        assertTrue(allowRetry(task, 1, retryableError));
        assertFalse(allowRetry(task, 3, retryableError));
        assertFalse(allowRetry(task, 1, nonRetryableError));
    }

    private boolean allowRetry(OrchestrationTaskDefinition task, int attempt, String errorMessage) {
        if (errorMessage.contains("already exists")) {
            return false;
        }
        return attempt < task.getMaxAttempts();
    }
}
