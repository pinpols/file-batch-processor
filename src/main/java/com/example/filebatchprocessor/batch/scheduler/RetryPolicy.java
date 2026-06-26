package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

class RetryPolicy {

    enum FailureClass {
        RETRYABLE,
        SKIPPABLE,
        FATAL
    }

    private final int defaultMaxAttempts;
    private final long defaultRetryBackoffMs;
    private final double defaultRetryJitterRatio;

    RetryPolicy(int defaultMaxAttempts, long defaultRetryBackoffMs, double defaultRetryJitterRatio) {
        this.defaultMaxAttempts = Math.max(1, defaultMaxAttempts);
        this.defaultRetryBackoffMs = Math.max(1000, defaultRetryBackoffMs);
        this.defaultRetryJitterRatio = Math.min(1.0, Math.max(0.0, defaultRetryJitterRatio));
    }

    boolean allowRetry(OrchestrationTaskDefinition def, int currentAttempt, String reason) {
        FailureClass failureClass = classify(reason);
        if (failureClass != FailureClass.RETRYABLE) {
            return false;
        }
        Integer maxAttempts = def.getMaxAttempts() == null ? defaultMaxAttempts : Math.max(1, def.getMaxAttempts());
        return currentAttempt < maxAttempts;
    }

    Instant nextRetryAt(OrchestrationTaskDefinition def) {
        long backoff =
                def.getRetryBackoffMs() == null ? defaultRetryBackoffMs : Math.max(1000, def.getRetryBackoffMs());

        double jitterRatio = defaultRetryJitterRatio;
        long jitterRange = (long) (backoff * jitterRatio);
        long jitter = 0L;
        if (jitterRange > 0) {
            jitter = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        }
        long delay = Math.max(0L, backoff + jitter);
        return Instant.now().plusMillis(delay);
    }

    FailureClass classify(String reason) {
        if (reason == null || reason.isBlank()) {
            return FailureClass.FATAL;
        }
        String lower = reason.toLowerCase();
        if (lower.contains("already exists") || lower.contains("duplicate") || lower.contains("idempotent")) {
            return FailureClass.SKIPPABLE;
        }
        if (lower.contains("timeout")
                || lower.contains("connection")
                || lower.contains("temporar")
                || lower.contains("deadlock")
                || lower.contains("lock")) {
            return FailureClass.RETRYABLE;
        }
        return FailureClass.FATAL;
    }
}
