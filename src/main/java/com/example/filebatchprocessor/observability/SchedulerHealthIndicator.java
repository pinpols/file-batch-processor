package com.example.filebatchprocessor.observability;

import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.service.SchedulerLeaderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * #33 领域级健康/就绪信号:把"调度 leader 状态、DLQ 积压、人工介入积压"暴露到 /actuator/health。
 *
 * <p>设计:非 leader 实例本身是健康的(不能因非 leader 就判 readiness DOWN,否则会把正常副本摘出 LB);
 * 仅当 DLQ 人工介入积压超过阈值时判 OUT_OF_SERVICE,提示需要运维介入。leader/积压计数始终作为 detail 暴露。
 */
@Component("scheduler")
public class SchedulerHealthIndicator implements HealthIndicator {

    private final SchedulerLeaderService schedulerLeaderService;
    private final DlqRecordRepository dlqRecordRepository;
    private final long manualBacklogThreshold;

    public SchedulerHealthIndicator(
            SchedulerLeaderService schedulerLeaderService,
            DlqRecordRepository dlqRecordRepository,
            @Value("${batch.health.dlq-manual-backlog-threshold:50}") long manualBacklogThreshold) {
        this.schedulerLeaderService = schedulerLeaderService;
        this.dlqRecordRepository = dlqRecordRepository;
        this.manualBacklogThreshold = manualBacklogThreshold;
    }

    @Override
    public Health health() {
        boolean leader = schedulerLeaderService.isLeader();
        long dlqBacklog = dlqRecordRepository.countByHandledFalse();
        long manualBacklog = dlqRecordRepository.countByHandledFalseAndManualRequiredTrue();

        Health.Builder builder =
                manualBacklog > manualBacklogThreshold ? Health.outOfService() : Health.up();
        return builder.withDetail("leader", leader)
                .withDetail("dlqBacklog", dlqBacklog)
                .withDetail("dlqManualBacklog", manualBacklog)
                .withDetail("dlqManualBacklogThreshold", manualBacklogThreshold)
                .build();
    }
}
