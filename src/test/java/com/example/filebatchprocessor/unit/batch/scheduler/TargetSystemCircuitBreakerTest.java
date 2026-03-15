package com.example.filebatchprocessor.unit.batch.scheduler;

import com.example.filebatchprocessor.batch.scheduler.TargetSystemCircuitBreaker;
import com.example.filebatchprocessor.config.CircuitBreakerProperties;
import com.example.filebatchprocessor.model.TargetSystemCircuitState;
import com.example.filebatchprocessor.repository.TargetSystemCircuitStateRepository;
import com.example.filebatchprocessor.observability.BatchMetrics;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

class TargetSystemCircuitBreakerTest {

    private final TargetSystemCircuitStateRepository repository = mock(TargetSystemCircuitStateRepository.class);
    private final CircuitBreakerProperties properties = new CircuitBreakerProperties();
    private final BatchMetrics batchMetrics = mock(BatchMetrics.class);

    @Test
    void shouldAllowWhenNoState() {
        when(repository.findByTargetSystem("unknown")).thenReturn(Optional.empty());
        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        assertTrue(breaker.tryAcquire("unknown"));
    }

    @Test
    void shouldRejectWhenOpenAndCooldownActive() {
        TargetSystemCircuitState state = new TargetSystemCircuitState();
        state.setTargetSystem("bad");
        state.setStatus("OPEN");
        state.setCooldownUntil(LocalDateTime.now().plusMinutes(1));
        when(repository.findByTargetSystem("bad")).thenReturn(Optional.of(state));

        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        assertFalse(breaker.tryAcquire("bad"));
        verify(batchMetrics, times(1)).counter("circuit_rejected_total", "target", "bad");
    }

    @Test
    void shouldAllowWhenOpenButCooldownExpired() {
        TargetSystemCircuitState state = new TargetSystemCircuitState();
        state.setTargetSystem("bad");
        state.setStatus("OPEN");
        state.setCooldownUntil(LocalDateTime.now().minusMinutes(1));
        when(repository.findByTargetSystem("bad")).thenReturn(Optional.of(state));

        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        assertTrue(breaker.tryAcquire("bad"));
        verify(batchMetrics, times(1)).counter("circuit_half_open_total", "target", "bad");
    }

    @Test
    void shouldOpenWhenFailureRateExceedsThreshold() {
        properties.setWindowSize(4L);
        properties.setFailureRateThreshold(0.5);
        when(repository.findByTargetSystem("flaky")).thenReturn(Optional.empty());

        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        // Record 3 failures out of 4 -> 0.75 > 0.5
        breaker.recordResult("flaky", false);
        breaker.recordResult("flaky", false);
        breaker.recordResult("flaky", false);
        breaker.recordResult("flaky", true);

        verify(batchMetrics, atLeastOnce()).counter("circuit_open_total", "target", "flaky");
    }

    @Test
    void shouldCloseAfterSuccessInHalfOpen() {
        TargetSystemCircuitState state = new TargetSystemCircuitState();
        state.setTargetSystem("recover");
        state.setStatus("HALF_OPEN");
        when(repository.findByTargetSystem("recover")).thenReturn(Optional.of(state));

        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        breaker.recordResult("recover", true);
        verify(batchMetrics, times(1)).counter("circuit_closed_total", "target", "recover");
    }
}
