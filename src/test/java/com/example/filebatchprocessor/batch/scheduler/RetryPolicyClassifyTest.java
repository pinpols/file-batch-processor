package com.example.filebatchprocessor.batch.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.scheduler.RetryPolicy.FailureClass;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

class RetryPolicyClassifyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy(3, 60000, 0.0);

    @Test
    void optimisticLockingIsRetryable() {
        assertEquals(
                FailureClass.RETRYABLE,
                retryPolicy.classify(new OptimisticLockingFailureException("x")));
    }

    @Test
    void queryTimeoutIsRetryable() {
        assertEquals(FailureClass.RETRYABLE, retryPolicy.classify(new QueryTimeoutException("x")));
    }

    @Test
    void socketTimeoutIsRetryable() {
        assertEquals(FailureClass.RETRYABLE, retryPolicy.classify(new SocketTimeoutException("x")));
    }

    @Test
    void duplicateKeyIsSkippable() {
        assertEquals(FailureClass.SKIPPABLE, retryPolicy.classify(new DuplicateKeyException("x")));
    }

    @Test
    void sqlState08IsRetryable() {
        assertEquals(FailureClass.RETRYABLE, retryPolicy.classify(new SQLException("conn", "08006")));
    }

    @Test
    void sqlState40IsRetryable() {
        assertEquals(
                FailureClass.RETRYABLE, retryPolicy.classify(new SQLException("deadlock", "40P01")));
    }

    @Test
    void sqlState23IsSkippable() {
        assertEquals(FailureClass.SKIPPABLE, retryPolicy.classify(new SQLException("dup", "23505")));
    }

    @Test
    void sqlState22IsFatal() {
        assertEquals(FailureClass.FATAL, retryPolicy.classify(new SQLException("range", "22003")));
    }

    @Test
    void causeChainIsTraversed() {
        assertEquals(
                FailureClass.RETRYABLE,
                retryPolicy.classify(new RuntimeException("wrap", new SQLException("c", "08001"))));
    }

    @Test
    void illegalArgumentIsFatal() {
        assertEquals(FailureClass.FATAL, retryPolicy.classify(new IllegalArgumentException("bad")));
    }

    @Test
    void nullThrowableIsFatal() {
        assertEquals(FailureClass.FATAL, retryPolicy.classify((Throwable) null));
    }

    @Test
    void unknownRuntimeIsFatal() {
        assertEquals(FailureClass.FATAL, retryPolicy.classify(new RuntimeException("???")));
    }
}
