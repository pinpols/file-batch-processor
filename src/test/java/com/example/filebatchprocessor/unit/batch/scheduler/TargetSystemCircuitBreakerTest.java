package com.example.filebatchprocessor.unit.batch.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.scheduler.TargetSystemCircuitBreaker;
import com.example.filebatchprocessor.config.CircuitBreakerProperties;
import com.example.filebatchprocessor.model.TargetSystemCircuitState;
import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.repository.TargetSystemCircuitStateRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
        // 新设计:OPEN+冷却到期时用原子条件 UPDATE 抢唯一探测资格,受影响行数=1 表示本调用方放行
        when(repository.tryTransitionToHalfOpen(eq("bad"), any())).thenReturn(1);

        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        assertTrue(breaker.tryAcquire("bad"));
        verify(batchMetrics, times(1)).counter("circuit_half_open_total", "target", "bad");
    }

    @Test
    void shouldOpenWhenFailureRateExceedsThreshold() {
        properties.setWindowSize(4L);
        properties.setFailureRateThreshold(0.5);
        // 新设计:失败计数持久化在 windowFailureCount;用共享 state 实例模拟跨调用累积(mock save 为 no-op)
        TargetSystemCircuitState shared = new TargetSystemCircuitState();
        shared.setTargetSystem("flaky");
        shared.setStatus("CLOSED");
        shared.setWindowSize(4L);
        shared.setFailureRateThreshold(0.5);
        when(repository.findByTargetSystem("flaky")).thenReturn(Optional.of(shared));
        when(repository.incrementFailureAndOpenIfThreshold(
                        eq("flaky"), any(LocalDateTime.class), eq(4L), eq(0.5), anyLong(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    shared.setWindowFailureCount(shared.getWindowFailureCount() + 1);
                    if (shared.getWindowFailureCount() >= 2) {
                        shared.setStatus("OPEN");
                    }
                    return 1;
                });

        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        // Record 3 failures out of 4 -> 0.75 > 0.5
        breaker.recordResult("flaky", false);
        breaker.recordResult("flaky", false);
        breaker.recordResult("flaky", false);
        breaker.recordResult("flaky", true);

        verify(batchMetrics, atLeastOnce()).counter("circuit_open_total", "target", "flaky");
    }

    @Test
    void shouldUseAtomicFailureIncrementToAvoidLostUpdates() {
        properties.setWindowSize(4L);
        properties.setFailureRateThreshold(0.5);
        TargetSystemCircuitState state = new TargetSystemCircuitState();
        state.setTargetSystem("flaky");
        state.setStatus("CLOSED");
        state.setWindowSize(4L);
        state.setFailureRateThreshold(0.5);
        when(repository.findByTargetSystem("flaky")).thenReturn(Optional.of(state));
        when(repository.incrementFailureAndOpenIfThreshold(
                        eq("flaky"), any(LocalDateTime.class), eq(4L), eq(0.5), anyLong(), any(LocalDateTime.class)))
                .thenReturn(1);

        TargetSystemCircuitBreaker breaker = new TargetSystemCircuitBreaker(repository, properties, batchMetrics);
        breaker.recordResult("flaky", false);

        verify(repository)
                .incrementFailureAndOpenIfThreshold(
                        eq("flaky"), any(LocalDateTime.class), eq(4L), eq(0.5), anyLong(), any(LocalDateTime.class));
        verify(repository, never()).save(any(TargetSystemCircuitState.class));
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
