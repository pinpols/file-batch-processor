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
        return nextRetryAt(def, 0);
    }

    // #25 修复:指数退避——base * 2^attempt(封顶 30 分钟),再叠加 jitter;attempt=已发生的重试次数。
    Instant nextRetryAt(OrchestrationTaskDefinition def, int attempt) {
        long base = def.getRetryBackoffMs() == null ? defaultRetryBackoffMs : Math.max(1000, def.getRetryBackoffMs());

        long maxBackoff = 30L * 60_000L; // 30 分钟封顶
        int safeAttempt = Math.max(0, Math.min(attempt, 16)); // 防移位溢出
        long backoff = Math.min(maxBackoff, base * (1L << safeAttempt));

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
