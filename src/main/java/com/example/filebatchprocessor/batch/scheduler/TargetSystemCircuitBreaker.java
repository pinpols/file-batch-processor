package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.config.CircuitBreakerProperties;
import com.example.filebatchprocessor.model.TargetSystemCircuitState;
import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.repository.TargetSystemCircuitStateRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class TargetSystemCircuitBreaker {

    private final TargetSystemCircuitStateRepository repository;
    private final CircuitBreakerProperties properties;
    private final BatchMetrics batchMetrics;

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
    // OPEN→HALF_OPEN 用原子条件 UPDATE 保证同一时间只有一个探测请求。
    @Transactional
    public boolean tryAcquire(String targetSystem) {
        Optional<TargetSystemCircuitState> opt = repository.findByTargetSystem(targetSystem);
        if (opt.isEmpty()) {
            // No state yet, allow
            return true;
        }
        TargetSystemCircuitState state = opt.get();
        if (!"OPEN".equals(state.getStatus())) {
            // CLOSED 或 HALF_OPEN:放行
            return true;
        }
        // status == OPEN
        LocalDateTime now = LocalDateTime.now();
        if (state.getCooldownUntil() != null && now.isBefore(state.getCooldownUntil())) {
            batchMetrics.counter("circuit_rejected_total", "target", targetSystem);
            return false;
        }
        // 冷却到期:原子地把唯一一个调用方转为 HALF_OPEN 并放行,其余并发调用方落空被拒绝(防探测涌入)。
        int won = repository.tryTransitionToHalfOpen(targetSystem, now);
        if (won == 1) {
            batchMetrics.counter("circuit_half_open_total", "target", targetSystem);
            return true;
        }
        batchMetrics.counter("circuit_rejected_total", "target", targetSystem);
        return false;
    }

    /** 记录目标系统调用结果，并根据滑动窗口状态推进断路器。 */
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

        boolean wasOpen = "OPEN".equals(state.getStatus());

        if (success) {
            // 失败计数持久化在 windowFailureCount,跨重启和多实例保持同一窗口口径。
            state.setWindowFailureCount(0L);
            state.setUpdatedAt(LocalDateTime.now());
            if ("HALF_OPEN".equals(state.getStatus())) {
                state.setStatus("CLOSED");
                batchMetrics.counter("circuit_closed_total", "target", targetSystem);
            }
            repository.save(state);
        } else {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cooldownUntil = now.plusNanos(properties.getCooldownDurationMs() * 1_000_000L);
            long openFailureCount = openFailureCount();
            int updated = repository.incrementFailureAndOpenIfThreshold(
                    targetSystem,
                    now,
                    properties.getWindowSize(),
                    properties.getFailureRateThreshold(),
                    properties.getCooldownDurationMs(),
                    openFailureCount,
                    cooldownUntil);
            if (updated == 0) {
                TargetSystemCircuitState created = new TargetSystemCircuitState();
                created.setTargetSystem(targetSystem);
                created.setStatus("CLOSED");
                created.setWindowSize(properties.getWindowSize());
                created.setFailureRateThreshold(properties.getFailureRateThreshold());
                created.setCooldownDurationMs(properties.getCooldownDurationMs());
                repository.save(created);
                repository.incrementFailureAndOpenIfThreshold(
                        targetSystem,
                        now,
                        properties.getWindowSize(),
                        properties.getFailureRateThreshold(),
                        properties.getCooldownDurationMs(),
                        openFailureCount,
                        cooldownUntil);
            }

            Optional<TargetSystemCircuitState> current = repository.findByTargetSystem(targetSystem);
            if (current.isPresent() && "OPEN".equals(current.get().getStatus()) && !wasOpen) {
                TargetSystemCircuitState updatedState = current.get();
                batchMetrics.counter("circuit_open_total", "target", targetSystem);
                log.warn(
                        "Circuit opened for targetSystem={} due to failureRate={}/{}",
                        targetSystem,
                        failureRate(updatedState),
                        updatedState.getFailureRateThreshold());
            }
        }
    }

    private double failureRate(TargetSystemCircuitState state) {
        long windowSize = Math.max(1L, state.getWindowSize());
        return ((double) state.getWindowFailureCount()) / ((double) windowSize);
    }

    private long openFailureCount() {
        long windowSize = Math.max(1L, properties.getWindowSize());
        return Math.max(1L, (long) Math.ceil(properties.getFailureRateThreshold() * windowSize));
    }
}
