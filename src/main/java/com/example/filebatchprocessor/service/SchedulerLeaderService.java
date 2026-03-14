package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.repository.SchedulerLeaderLockRepository;
import com.example.filebatchprocessor.observability.BatchMetrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SchedulerLeaderService {

    private static final String DEFAULT_LOCK_NAME = "orchestration-scheduler";

    private final SchedulerLeaderLockRepository repository;
    private final BatchMetrics batchMetrics;
    private final String ownerId = UUID.randomUUID().toString();
    private final AtomicBoolean leader = new AtomicBoolean(false);

    @Value("${orchestration.scheduler.leader-lock-name:" + DEFAULT_LOCK_NAME + "}")
    private String lockName;

    @Value("${orchestration.scheduler.leader-ttl-seconds:30}")
    private long ttlSeconds;

    @Value("${orchestration.scheduler.force-leader:false}")
    private boolean forceLeader;

    public SchedulerLeaderService(SchedulerLeaderLockRepository repository, BatchMetrics batchMetrics) {
        this.repository = repository;
        this.batchMetrics = batchMetrics;
    }

    public boolean isLeader() {
        if (forceLeader) {
            return true;
        }
        return leader.get();
    }

    @PostConstruct
    public void init() {
        refreshLeadership();
    }

    @Scheduled(fixedDelayString = "${orchestration.scheduler.leader.refresh-ms:5000}")
    @Transactional
    public void refreshLeadership() {
        try {
            boolean acquired = tryAcquireOrRenew();
            boolean prev = leader.getAndSet(acquired);
            if (acquired && !prev) {
                log.info("Scheduler leadership acquired: lockName={}, ownerId={}", lockName, ownerId);
                batchMetrics.gauge("scheduler_leader", () -> 1, "status", "leader");
            }
            if (!acquired && prev) {
                log.warn("Scheduler leadership lost: lockName={}, ownerId={}", lockName, ownerId);
                batchMetrics.gauge("scheduler_leader", () -> 0, "status", "leader");
            }
            // Update gauge continuously to reflect current state
            batchMetrics.gauge("scheduler_leader", () -> acquired ? 1 : 0, "status", "leader");
        } catch (Exception e) {
            log.error("Failed to refresh scheduler leadership", e);
            leader.set(false);
            batchMetrics.gauge("scheduler_leader", () -> 0, "status", "leader");
        }
    }

    protected boolean tryAcquireOrRenew() {
        long safeTtl = Math.max(5, ttlSeconds);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(safeTtl);
        int updated = repository.tryAcquireOrRenew(lockName, ownerId, expiresAt);
        return updated > 0;
    }
}
