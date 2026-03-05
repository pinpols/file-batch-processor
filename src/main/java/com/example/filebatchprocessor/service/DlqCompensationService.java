package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 死信补偿服务：支持按记录重放和按任务重触发。
 */
@Slf4j
@Service
public class DlqCompensationService {

    private final DlqRecordRepository dlqRecordRepository;
    private final PartitionedImportService partitionedImportService;
    private final JobLauncher jobLauncher;
    private final Job processFileJob;
    private final int maxReplayCount;
    private final long retryDelayMs;

    public DlqCompensationService(DlqRecordRepository dlqRecordRepository,
                                  PartitionedImportService partitionedImportService,
                                  @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
                                  @Qualifier("processFileJob") Job processFileJob,
                                  @Value("${batch.dlq.max-replay-count:5}") int maxReplayCount,
                                  @Value("${batch.dlq.retry-delay-ms:60000}") long retryDelayMs) {
        this.dlqRecordRepository = dlqRecordRepository;
        this.partitionedImportService = partitionedImportService;
        this.jobLauncher = jobLauncher;
        this.processFileJob = processFileJob;
        this.maxReplayCount = Math.max(1, maxReplayCount);
        this.retryDelayMs = Math.max(1000L, retryDelayMs);
    }

    @Transactional
    public int replayPending(int limit) {
        List<DlqRecord> records = dlqRecordRepository
                .findTop100ByHandledFalseAndManualRequiredFalseAndRetryableTrueAndNextRetryAtBeforeOrderByCreatedAtAsc(LocalDateTime.now());
        int processed = 0;
        for (DlqRecord record : records) {
            if (processed >= Math.max(limit, 1)) {
                break;
            }
            long currentReplay = record.getReplayCount() == null ? 0L : record.getReplayCount();
            if (currentReplay >= maxReplayCount) {
                record.setManualRequired(true);
                record.setCompensationStatus("MANUAL_REQUIRED");
                record.setLastReplayError("Exceeded max replay count: " + maxReplayCount);
                dlqRecordRepository.save(record);
                continue;
            }
            try {
                replay(record);
                record.setHandled(true);
                record.setHandledAt(LocalDateTime.now());
                record.setLastReplayError(null);
                record.setReplayCount(currentReplay + 1);
                record.setCompensationStatus("REPLAYED");
                dlqRecordRepository.save(record);
                processed++;
            } catch (Exception e) {
                record.setReplayCount(currentReplay + 1);
                String msg = e.getMessage();
                record.setLastReplayError(msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg);
                record.setCompensationStatus("RETRY_PENDING");
                record.setNextRetryAt(LocalDateTime.now().plusNanos(retryDelayMs * 1_000_000));
                dlqRecordRepository.save(record);
                log.error("DLQ replay failed for id={}", record.getId(), e);
            }
        }
        return processed;
    }

    private void replay(DlqRecord record) throws Exception {
        Map<String, String> params = parse(record.getParams());
        String source = params.getOrDefault("source", "");
        if ("record-writer".equals(source)) {
            String businessKey = params.get("businessKey");
            String name = params.get("name");
            String description = params.get("description");
            String batchDate = params.get("batchDate");
            partitionedImportService.importRecord(businessKey, name, description, batchDate, null, null);
            return;
        }

        // 默认按任务级重触发
        String raw = record.getParams();
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis()) // Use current time for uniqueness
                .addString("job.param", raw == null ? "" : raw)
                .addLong("dlq.replay.id", record.getId()) // Add DLQ record ID for uniqueness
                .addString("dlq.replay.time", String.valueOf(System.currentTimeMillis())); // Add replay timestamp
        
        // Parse original parameters but exclude the time parameter to avoid conflicts
        Map<String, String> originalParams = parse(raw);
        originalParams.forEach((key, value) -> {
            if (!"time".equals(key)) { // Skip the original time parameter
                builder.addString(key, value);
            }
        });
        
        jobLauncher.run(processFileJob, builder.toJobParameters());
    }

    private Map<String, String> parse(String param) {
        Map<String, String> map = new HashMap<>();
        if (param == null || param.isBlank()) {
            return map;
        }
        String[] pairs = param.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}
