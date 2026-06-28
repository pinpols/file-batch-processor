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
    // #2 修复:改为可写事务(原 readOnly 下 save 不落库),OPEN→HALF_OPEN 用原子条件 UPDATE 保证单探测者。
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
            // #12 修复:失败计数持久化在 windowFailureCount(跨重启/多实例),成功即清零
            state.setWindowFailureCount(0L);
            state.setUpdatedAt(LocalDateTime.now());
            if ("HALF_OPEN".equals(state.getStatus())) {
                state.setStatus("CLOSED");
                batchMetrics.counter("circuit_closed_total", "target", targetSystem);
            }
            repository.save(state);
        } else {
            // 失败计数从持久化值自增(不再依赖内存 map)
            long failures = state.getWindowFailureCount() + 1L;
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
