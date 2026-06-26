package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.ExecutionDedupRecordRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 历史数据清理，避免批量系统核心表持续膨胀。
 */
@Slf4j
@Service
public class BatchDataRetentionService {

    private final ExecutionDedupRecordRepository dedupRecordRepository;
    private final TaskExecutionStateRepository taskExecutionStateRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final BatchRunRecordRepository batchRunRecordRepository;

    @Value("${batch.retention.enabled:true}")
    private boolean retentionEnabled;

    @Value("${batch.retention.dedup-days:7}")
    private int dedupDays;

    @Value("${batch.retention.task-state-days:30}")
    private int taskStateDays;

    @Value("${batch.retention.dlq-handled-days:30}")
    private int dlqHandledDays;

    @Value("${batch.retention.batch-run-days:90}")
    private int batchRunDays;

    public BatchDataRetentionService(
            ExecutionDedupRecordRepository dedupRecordRepository,
            TaskExecutionStateRepository taskExecutionStateRepository,
            DlqRecordRepository dlqRecordRepository,
            BatchRunRecordRepository batchRunRecordRepository) {
        this.dedupRecordRepository = dedupRecordRepository;
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.batchRunRecordRepository = batchRunRecordRepository;
    }

    @Transactional
    @Scheduled(cron = "${batch.retention.cron:0 30 3 * * ?}")
    public void cleanup() {
        if (!retentionEnabled) {
            return;
        }

        long dedupDeleted = dedupRecordRepository.deleteByCreatedAtBefore(
                LocalDateTime.now().minusDays(Math.max(1, dedupDays)));
        long stateDeleted = taskExecutionStateRepository.deleteByUpdatedAtBeforeAndStatusIn(
                LocalDateTime.now().minusDays(Math.max(1, taskStateDays)), List.of("SUCCESS", "FAILED", "TIMEOUT"));
        long dlqDeleted = dlqRecordRepository.deleteByHandledTrueAndHandledAtBefore(
                LocalDateTime.now().minusDays(Math.max(1, dlqHandledDays)));
        long runDeleted = batchRunRecordRepository.deleteByCreatedAtBefore(
                LocalDateTime.now().minusDays(Math.max(1, batchRunDays)));

        log.info(
                "Retention cleanup finished: dedup={}, taskState={}, dlqHandled={}, batchRun={}",
                dedupDeleted,
                stateDeleted,
                dlqDeleted,
                runDeleted);
    }
}
