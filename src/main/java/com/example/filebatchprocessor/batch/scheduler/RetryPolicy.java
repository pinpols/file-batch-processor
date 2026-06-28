package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

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
        return allowRetry(def, currentAttempt, classify(reason));
    }

    boolean allowRetry(OrchestrationTaskDefinition def, int currentAttempt, FailureClass failureClass) {
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
                || lower.contains("lock")
                || lower.contains("unavailable")
                || lower.contains("reset")) {
            return FailureClass.RETRYABLE;
        }
        return FailureClass.FATAL;
    }

    /**
     * 按异常类型 + PG SQLState 前缀分类。沿 cause 链逐层判断(最多 8 层防环),命中即返回;遍历完未命中 → FATAL。
     */
    FailureClass classify(Throwable t) {
        if (t == null) {
            return FailureClass.FATAL;
        }
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < 8) {
            FailureClass hit = classifySingle(current);
            if (hit != null) {
                return hit;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
            depth++;
        }
        return FailureClass.FATAL;
    }

    /** 对单个异常做类型/SQLState 判定;无法判定返回 null(交由调用方沿 cause 链继续)。 */
    private FailureClass classifySingle(Throwable t) {
        // 乐观锁要先于 DuplicateKey/DataIntegrity 判定(都属 DataAccess,但应 RETRYABLE)。
        if (t instanceof OptimisticLockingFailureException) {
            return FailureClass.RETRYABLE;
        }
        if (t instanceof TransientDataAccessException
                || t instanceof QueryTimeoutException
                || t instanceof ConcurrencyFailureException
                || t instanceof CannotCreateTransactionException
                || t instanceof SocketTimeoutException
                || t instanceof ConnectException
                || t instanceof TimeoutException) {
            return FailureClass.RETRYABLE;
        }
        // 子类优先(虽与 DataIntegrity 结果同,保持语义清晰)。
        if (t instanceof DuplicateKeyException || t instanceof DataIntegrityViolationException) {
            return FailureClass.SKIPPABLE;
        }
        if (t instanceof IllegalArgumentException || t instanceof IllegalStateException) {
            return FailureClass.FATAL;
        }
        if (t instanceof SQLException sqlException) {
            return classifySqlState(sqlException.getSQLState());
        }
        return null;
    }

    private FailureClass classifySqlState(String sqlState) {
        if (sqlState == null || sqlState.length() < 2) {
            return null;
        }
        String prefix = sqlState.substring(0, 2);
        switch (prefix) {
            case "08": // 连接异常
            case "40": // 事务回滚 / 死锁
            case "57": // operator intervention
            case "53": // 资源不足
                return FailureClass.RETRYABLE;
            case "23": // 完整性约束冲突(如唯一键重复,幂等可跳)
                return FailureClass.SKIPPABLE;
            case "22": // 数据异常(如数值越界)
            case "42": // 语法 / 权限
                return FailureClass.FATAL;
            default:
                return null;
        }
    }
}
