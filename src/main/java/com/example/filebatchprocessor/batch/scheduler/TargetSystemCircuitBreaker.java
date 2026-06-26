package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.config.CircuitBreakerProperties;
import com.example.filebatchprocessor.model.TargetSystemCircuitState;
import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.repository.TargetSystemCircuitStateRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class TargetSystemCircuitBreaker {

    private final TargetSystemCircuitStateRepository repository;
    private final CircuitBreakerProperties properties;
    private final BatchMetrics batchMetrics;

    // In-memory sliding window counters (targetSystem -> failureCount)
    private final ConcurrentMap<String, Long> failureCounters = new ConcurrentHashMap<>();

    public TargetSystemCircuitBreaker(
            TargetSystemCircuitStateRepository repository,
            CircuitBreakerProperties properties,
            BatchMetrics batchMetrics) {
        this.repository = repository;
        this.properties = properties;
        this.batchMetrics = batchMetrics;
    }

    /**
     * Returns true if request is allowed (circuit closed or half-open), false if rejected (circuit open).
     */
    @Transactional(readOnly = true)
    public boolean tryAcquire(String targetSystem) {
        Optional<TargetSystemCircuitState> opt = repository.findByTargetSystem(targetSystem);
        if (opt.isEmpty()) {
            // No state yet, allow
            return true;
        }
        TargetSystemCircuitState state = opt.get();
        if ("OPEN".equals(state.getStatus())
                && state.getCooldownUntil() != null
                && LocalDateTime.now().isBefore(state.getCooldownUntil())) {
            batchMetrics.counter("circuit_rejected_total", "target", targetSystem);
            return false;
        }
        if ("OPEN".equals(state.getStatus())
                && (state.getCooldownUntil() == null || LocalDateTime.now().isAfter(state.getCooldownUntil()))) {
            // Transition to HALF_OPEN
            state.setStatus("HALF_OPEN");
            state.setUpdatedAt(LocalDateTime.now());
            repository.save(state);
            batchMetrics.counter("circuit_half_open_total", "target", targetSystem);
        }
        return true;
    }

    /**
     * Record a task result for the target system.
     */
    @Transactional
    public void recordResult(String targetSystem, boolean success) {
        TargetSystemCircuitState state = repository
                .findByTargetSystem(targetSystem)
                .orElseGet(() -> {
                    TargetSystemCircuitState s = new TargetSystemCircuitState();
                    s.setTargetSystem(targetSystem);
                    s.setStatus("CLOSED");
                    s.setWindowSize(properties.getWindowSize());
                    s.setFailureRateThreshold(properties.getFailureRateThreshold());
                    s.setCooldownDurationMs(properties.getCooldownDurationMs());
                    return s;
                });

        if (success) {
            // Reset failure counter on success
            failureCounters.put(targetSystem, 0L);
            if ("HALF_OPEN".equals(state.getStatus())) {
                state.setStatus("CLOSED");
                state.setUpdatedAt(LocalDateTime.now());
                repository.save(state);
                batchMetrics.counter("circuit_closed_total", "target", targetSystem);
            }
        } else {
            // Increment failure counter
            long failures = failureCounters.merge(targetSystem, 1L, (old, one) -> old == null ? one : old + one);
            state.setWindowFailureCount(failures);
            state.setLastFailureAt(LocalDateTime.now());
            state.setUpdatedAt(LocalDateTime.now());

            // Check failure rate against threshold
            double failureRate = ((double) failures) / ((double) state.getWindowSize());
            if (failureRate >= state.getFailureRateThreshold()) {
                // Open the circuit
                state.setStatus("OPEN");
                state.setCooldownUntil(LocalDateTime.now().plusNanos(state.getCooldownDurationMs() * 1_000_000L));
                repository.save(state);
                batchMetrics.counter("circuit_open_total", "target", targetSystem);
                log.warn(
                        "Circuit opened for targetSystem={} due to failureRate={}/{}",
                        targetSystem,
                        failureRate,
                        state.getFailureRateThreshold());
            } else {
                repository.save(state);
            }
        }
    }
}
