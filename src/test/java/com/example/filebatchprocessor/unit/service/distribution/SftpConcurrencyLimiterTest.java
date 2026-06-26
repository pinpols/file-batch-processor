package com.example.filebatchprocessor.unit.service.distribution;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.service.distribution.SftpConcurrencyLimiter;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;

class SftpConcurrencyLimiterTest {

    @Test
    void shouldReturnSameSemaphoreForSameHost() {
        SftpConcurrencyLimiter limiter = new SftpConcurrencyLimiter(2);
        Semaphore s1 = limiter.semaphoreForHost("HostA");
        Semaphore s2 = limiter.semaphoreForHost("hosta");
        assertSame(s1, s2);
    }

    @Test
    void shouldEnforceMaxConcurrentPerHost() {
        SftpConcurrencyLimiter limiter = new SftpConcurrencyLimiter(1);
        Semaphore s = limiter.semaphoreForHost("h");
        assertTrue(s.tryAcquire());
        assertFalse(s.tryAcquire());
        s.release();
        assertTrue(s.tryAcquire());
    }
}
