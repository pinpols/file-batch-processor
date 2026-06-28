package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchAlertEvaluator {

    private final BatchRunRecordRepository batchRunRecordRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final AlertDispatcher alertDispatcher;
    private final SchedulerLeaderService schedulerLeaderService;

    @Value("${batch.alert.enabled:true}")
    private boolean enabled;

    @Value("${batch.alert.failure-rate-threshold:0.2}")
    private double failureRateThreshold;

    @Value("${batch.alert.dlq-backlog-threshold:100}")
    private long dlqBacklogThreshold;

    @Value("${batch.alert.dlq-manual-threshold:20}")
    private long dlqManualThreshold;

    @Value("${batch.alert.min-throughput-rps-threshold:5}")
    private double minThroughputRpsThreshold;

    public BatchAlertEvaluator(
            BatchRunRecordRepository batchRunRecordRepository,
            DlqRecordRepository dlqRecordRepository,
            AlertDispatcher alertDispatcher,
            Optional<SchedulerLeaderService> schedulerLeaderService) {
        this.batchRunRecordRepository = batchRunRecordRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.alertDispatcher = alertDispatcher;
        this.schedulerLeaderService = schedulerLeaderService == null ? null : schedulerLeaderService.orElse(null);
    }

    @Scheduled(fixedDelayString = "${batch.alert.evaluate-ms:60000}")
    public void evaluate() {
        if (schedulerLeaderService != null && !schedulerLeaderService.isLeader()) {
            return;
        }
        if (!enabled) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        long failures = batchRunRecordRepository.countByStatusAndCreatedAtAfter("FAILED", since);
        long completed = batchRunRecordRepository.countByStatusAndCreatedAtAfter("COMPLETED", since);
        long partial = batchRunRecordRepository.countByStatusAndCreatedAtAfter("PARTIAL", since);
        long total = failures + completed + partial;

        if (total > 0) {
            double failureRate = (double) failures / total;
            if (failureRate >= failureRateThreshold) {
                log.error("ALERT failure rate high: {} (threshold={})", failureRate, failureRateThreshold);
                alertDispatcher.dispatch(AlertEvent.of(
                        "BATCH_FAILURE_RATE_HIGH",
                        AlertSeverity.CRITICAL,
                        "Failure ratio > threshold",
                        Map.of("failureRate", failureRate, "threshold", failureRateThreshold, "windowMinutes", 15)));
            }
        }

        long backlog = dlqRecordRepository.countByHandledFalse();
        if (backlog >= dlqBacklogThreshold) {
            log.error("ALERT DLQ backlog high: {} (threshold={})", backlog, dlqBacklogThreshold);
            alertDispatcher.dispatch(AlertEvent.of(
                    "BATCH_DLQ_BACKLOG_HIGH",
                    AlertSeverity.WARNING,
                    "DLQ backlog exceeded threshold",
                    Map.of("backlog", backlog, "threshold", dlqBacklogThreshold)));
        }

        long manualBacklog = dlqRecordRepository.countByHandledFalseAndManualRequiredTrue();
        if (manualBacklog >= dlqManualThreshold) {
            log.error("ALERT DLQ manual backlog high: {} (threshold={})", manualBacklog, dlqManualThreshold);
            alertDispatcher.dispatch(AlertEvent.of(
                    "BATCH_DLQ_MANUAL_BACKLOG_HIGH",
                    AlertSeverity.CRITICAL,
                    "DLQ manual-required backlog exceeded threshold",
                    Map.of("manualBacklog", manualBacklog, "threshold", dlqManualThreshold)));
        }

        var recent = batchRunRecordRepository.findTop200ByOrderByCreatedAtDesc();
        double avgThroughput = recent.stream()
                .mapToDouble(v -> v.getThroughputRps() == null ? 0.0 : v.getThroughputRps())
                .average()
                .orElse(0.0);
        if (!recent.isEmpty() && avgThroughput < minThroughputRpsThreshold) {
            log.error("ALERT throughput degraded: {} rps (threshold={})", avgThroughput, minThroughputRpsThreshold);
            alertDispatcher.dispatch(AlertEvent.of(
                    "BATCH_THROUGHPUT_LOW",
                    AlertSeverity.WARNING,
                    "Average throughput below threshold",
                    Map.of("avgThroughputRps", avgThroughput, "threshold", minThroughputRpsThreshold)));
        }
    }
}
