package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.model.TaskDefinition;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BatchMetricsPublisher {

    private final MeterRegistry meterRegistry;
    private final BatchRunRecordRepository batchRunRecordRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final TaskExecutionStateRepository taskExecutionStateRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;

    private final AtomicLong recentFailureCount = new AtomicLong(0);
    private final AtomicLong recentCompletedCount = new AtomicLong(0);
    private final AtomicLong dlqBacklog = new AtomicLong(0);
    private final AtomicLong dlqManualBacklog = new AtomicLong(0);
    private final AtomicLong dlqRetryPending = new AtomicLong(0);
    private final AtomicLong blockedTaskCount = new AtomicLong(0);
    private final AtomicLong avgThroughputMilli = new AtomicLong(0);
    private final AtomicLong slaDurationBreachCount = new AtomicLong(0);

    public BatchMetricsPublisher(MeterRegistry meterRegistry,
                                 BatchRunRecordRepository batchRunRecordRepository,
                                 DlqRecordRepository dlqRecordRepository,
                                 TaskExecutionStateRepository taskExecutionStateRepository,
                                 TaskDefinitionRepository taskDefinitionRepository) {
        this.meterRegistry = meterRegistry;
        this.batchRunRecordRepository = batchRunRecordRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
    }

    @PostConstruct
    void init() {
        Gauge.builder("batch_recent_failure_count", recentFailureCount, AtomicLong::get).register(meterRegistry);
        Gauge.builder("batch_recent_completed_count", recentCompletedCount, AtomicLong::get).register(meterRegistry);
        Gauge.builder("batch_dlq_backlog", dlqBacklog, AtomicLong::get).register(meterRegistry);
        Gauge.builder("batch_dlq_manual_backlog", dlqManualBacklog, AtomicLong::get).register(meterRegistry);
        Gauge.builder("batch_dlq_retry_pending", dlqRetryPending, AtomicLong::get).register(meterRegistry);
        Gauge.builder("batch_blocked_task_count", blockedTaskCount, AtomicLong::get).register(meterRegistry);
        Gauge.builder("batch_avg_throughput_rps", avgThroughputMilli, v -> v.get() / 1000.0).register(meterRegistry);
        Gauge.builder("batch_sla_duration_breach_count", slaDurationBreachCount, AtomicLong::get).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${batch.metrics.refresh-ms:30000}")
    public void refresh() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        recentFailureCount.set(batchRunRecordRepository.countByStatusAndCreatedAtAfter("FAILED", since));
        recentCompletedCount.set(batchRunRecordRepository.countByStatusAndCreatedAtAfter("COMPLETED", since));
        dlqBacklog.set(dlqRecordRepository.countByHandledFalse());
        dlqManualBacklog.set(dlqRecordRepository.countByHandledFalseAndManualRequiredTrue());
        dlqRetryPending.set(dlqRecordRepository.countByHandledFalseAndCompensationStatus("RETRY_PENDING"));
        blockedTaskCount.set(taskExecutionStateRepository.countByStatusIn(java.util.List.of("BLOCKED", "READY", "RUNNING")));

        var recent = batchRunRecordRepository.findTop200ByOrderByCreatedAtDesc();
        double avg = recent.stream().mapToDouble(r -> r.getThroughputRps() == null ? 0.0 : r.getThroughputRps()).average().orElse(0.0);
        avgThroughputMilli.set((long) (avg * 1000));

        var taskDefs = taskDefinitionRepository.findByEnabledTrue();
        java.util.Map<String, Long> jobSlaMap = new java.util.HashMap<>();
        for (TaskDefinition def : taskDefs) {
            if (def.getJobName() == null || def.getSlaMaxDurationMs() == null) {
                continue;
            }
            long sla = def.getSlaMaxDurationMs();
            if (sla <= 0) {
                continue;
            }
            jobSlaMap.merge(def.getJobName(), sla, Math::min);
        }
        long breaches = recent.stream()
                .filter(r -> jobSlaMap.containsKey(r.getJobName()))
                .filter(r -> r.getDurationMs() != null && r.getDurationMs() > jobSlaMap.get(r.getJobName()))
                .count();
        slaDurationBreachCount.set(breaches);
    }
}
