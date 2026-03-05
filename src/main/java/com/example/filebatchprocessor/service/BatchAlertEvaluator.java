package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import org.springframework.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class BatchAlertEvaluator {

    private final BatchRunRecordRepository batchRunRecordRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final RestClient restClient;

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
    @Value("${batch.alert.webhook.enabled:false}")
    private boolean webhookEnabled;
    @Value("${batch.alert.webhook.url:}")
    private String webhookUrl;

    public BatchAlertEvaluator(BatchRunRecordRepository batchRunRecordRepository,
                               DlqRecordRepository dlqRecordRepository) {
        this.batchRunRecordRepository = batchRunRecordRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.restClient = RestClient.builder().build();
    }

    @Scheduled(fixedDelayString = "${batch.alert.evaluate-ms:60000}")
    public void evaluate() {
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
                notifyWebhook("BATCH_FAILURE_RATE_HIGH",
                        "Failure ratio > threshold",
                        Map.of("failureRate", failureRate, "threshold", failureRateThreshold, "windowMinutes", 15));
            }
        }

        long backlog = dlqRecordRepository.countByHandledFalse();
        if (backlog >= dlqBacklogThreshold) {
            log.error("ALERT DLQ backlog high: {} (threshold={})", backlog, dlqBacklogThreshold);
            notifyWebhook("BATCH_DLQ_BACKLOG_HIGH",
                    "DLQ backlog exceeded threshold",
                    Map.of("backlog", backlog, "threshold", dlqBacklogThreshold));
        }

        long manualBacklog = dlqRecordRepository.countByHandledFalseAndManualRequiredTrue();
        if (manualBacklog >= dlqManualThreshold) {
            log.error("ALERT DLQ manual backlog high: {} (threshold={})", manualBacklog, dlqManualThreshold);
            notifyWebhook("BATCH_DLQ_MANUAL_BACKLOG_HIGH",
                    "DLQ manual-required backlog exceeded threshold",
                    Map.of("manualBacklog", manualBacklog, "threshold", dlqManualThreshold));
        }

        var recent = batchRunRecordRepository.findTop200ByOrderByCreatedAtDesc();
        double avgThroughput = recent.stream()
                .mapToDouble(v -> v.getThroughputRps() == null ? 0.0 : v.getThroughputRps())
                .average().orElse(0.0);
        if (!recent.isEmpty() && avgThroughput < minThroughputRpsThreshold) {
            log.error("ALERT throughput degraded: {} rps (threshold={})", avgThroughput, minThroughputRpsThreshold);
            notifyWebhook("BATCH_THROUGHPUT_LOW",
                    "Average throughput below threshold",
                    Map.of("avgThroughputRps", avgThroughput, "threshold", minThroughputRpsThreshold));
        }
    }

    private void notifyWebhook(String code, String message, Map<String, Object> data) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertCode", code);
            payload.put("message", message);
            payload.put("service", "file-batch-processor");
            payload.put("timestamp", LocalDateTime.now().toString());
            payload.put("data", data);

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send webhook alert: code={}, url={}", code, webhookUrl, e);
        }
    }
}
