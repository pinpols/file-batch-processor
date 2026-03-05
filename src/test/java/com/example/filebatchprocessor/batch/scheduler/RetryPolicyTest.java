package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    @Test
    void shouldClassifyDuplicateAsSkippable() {
        RetryPolicy policy = new RetryPolicy(3, 1000L, 0.0);
        assertEquals(RetryPolicy.FailureClass.SKIPPABLE, policy.classify("Record already exists for key"));
    }

    @Test
    void shouldAllowRetryOnlyWithinMaxAttemptsForRetryableErrors() {
        RetryPolicy policy = new RetryPolicy(3, 1000L, 0.0);
        OrchestrationTaskDefinition task = OrchestrationTaskDefinition.builder()
                .id("t1")
                .maxAttempts(3)
                .build();

        assertTrue(policy.allowRetry(task, 1, "Connection timeout"));
        assertFalse(policy.allowRetry(task, 3, "Connection timeout"));
        assertFalse(policy.allowRetry(task, 1, "Record already exists"));
    }
}
