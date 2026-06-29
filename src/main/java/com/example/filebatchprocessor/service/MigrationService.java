package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileAssetStatus;
import com.example.filebatchprocessor.model.MigrationStatus;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.MigrationStatusRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class MigrationService {

    private static final Set<String> SUPPORTED_READ_SWITCH_TYPES =
            Set.of("FILE_RECORD", "FILE_DISPATCH_RECORD", "FILE_RECEPTION_QUEUE", "FILE_DISTRIBUTION_TASK");
    private static final Set<String> SUPPORTED_DEPRECATED_TABLES =
            Set.of("file_reception_queue", "file_distribution_task");

    private final MigrationStatusRepository migrationStatusRepository;
    private final FileAssetRecordRepository fileAssetRepository;
    private final FileDispatchRecordRepository dispatchRecordRepository;
    private final FileReceptionQueueRepository receptionQueueRepository;
    private final FileDistributionTaskRepository distributionTaskRepository;
    private final FileAssetStateMachineService stateMachineService;
    private final SchedulerLeaderService schedulerLeaderService;

    @Value("${migration.enabled:false}")
    private boolean enabled;

    @Value("${migration.batch-size:100}")
    private int batchSize;

    public MigrationService(
            MigrationStatusRepository migrationStatusRepository,
            FileAssetRecordRepository fileAssetRepository,
            FileDispatchRecordRepository dispatchRecordRepository,
            FileReceptionQueueRepository receptionQueueRepository,
            FileDistributionTaskRepository distributionTaskRepository,
            FileAssetStateMachineService stateMachineService,
            Optional<SchedulerLeaderService> schedulerLeaderService) {
        this.migrationStatusRepository = migrationStatusRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.dispatchRecordRepository = dispatchRecordRepository;
        this.receptionQueueRepository = receptionQueueRepository;
        this.distributionTaskRepository = distributionTaskRepository;
        this.stateMachineService = stateMachineService;
        this.schedulerLeaderService = schedulerLeaderService == null ? null : schedulerLeaderService.orElse(null);
    }

    public Map<String, Object> getMigrationStatus(String migrationName) {
        return migrationStatusRepository
                .findByMigrationName(migrationName)
                .map(m -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("name", m.getMigrationName());
                    result.put("phase", m.getMigrationPhase());
                    result.put("status", m.getStatus());
                    result.put("progress", m.getProgressPercent());
                    result.put("total", m.getTotalRecords() != null ? m.getTotalRecords() : 0);
                    result.put("processed", m.getProcessedRecords() != null ? m.getProcessedRecords() : 0);
                    result.put("failed", m.getFailedRecords() != null ? m.getFailedRecords() : 0);
                    result.put(
                            "startedAt",
                            m.getStartedAt() != null ? m.getStartedAt().toString() : "");
                    result.put(
                            "completedAt",
                            m.getCompletedAt() != null ? m.getCompletedAt().toString() : "");
                    return result;
                })
                .orElse(Map.of("status", "NOT_STARTED"));
    }

    public List<Map<String, Object>> getAllMigrations() {
        return migrationStatusRepository.findAll().stream()
                .map(m -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("name", m.getMigrationName());
                    result.put("phase", m.getMigrationPhase());
                    result.put("status", m.getStatus());
                    result.put("progress", m.getProgressPercent());
                    return result;
                })
                .toList();
    }

    @Scheduled(fixedDelayString = "${migration.check-interval-ms:3600000}")
    public void runScheduledMigration() {
        if (schedulerLeaderService != null && !schedulerLeaderService.isLeader()) {
            return;
        }
        if (!enabled) {
            return;
        }

        migrationStatusRepository.findByMigrationName("FILE_RECORD_BACKFILL").ifPresent(m -> {
            if ("IN_PROGRESS".equals(m.getStatus())) {
                log.info("Migration already in progress: {}", m.getMigrationName());
            } else if ("PENDING".equals(m.getStatus())) {
                backfillFileRecords();
            }
        });
    }

    public void backfillFileRecords() {
        MigrationStatus migration = migrationStatusRepository
                .findByMigrationName("FILE_RECORD_BACKFILL")
                .orElseGet(() -> {
                    MigrationStatus m = MigrationStatus.create("FILE_RECORD_BACKFILL", "DUAL_WRITE");
                    return migrationStatusRepository.save(m);
                });

        if ("COMPLETED".equals(migration.getStatus())) {
            log.info("Migration already completed: {}", migration.getMigrationName());
            return;
        }

        migration.start();
        migration.setTotalRecords((long) receptionQueueRepository.findAll().size());
        migrationStatusRepository.save(migration);

        long processed = 0;
        long failed = 0;
        long lastId = 0;

        try {
            var queues = receptionQueueRepository.findAll();
            for (var queue : queues) {
                try {
                    if (queue.getFileRecordId() == null) {
                        FileAssetRecord record = new FileAssetRecord();
                        record.setFileNo("FR-BACKFILL-" + queue.getId());
                        record.setOriginalName(queue.getFileName());
                        record.setStoredPath(queue.getFilePath());
                        record.setFileSize(queue.getFileSize());
                        record.setSourceSystem(queue.getSourceSystem());
                        record.setStatus(queue.getStatus());
                        record.setFileDirection("INBOUND");
                        record.setStorageType("LOCAL");
                        record.setVersionNo(1);
                        record.setLatestVersion(true);
                        record.setArrivedTime(queue.getCreatedAt());

                        stateMachineService.initialize(record, FileAssetStatus.ARRIVED);
                        fileAssetRepository.save(record);

                        queue.setFileRecordId(record.getId());
                        receptionQueueRepository.save(queue);
                    }
                    processed++;
                    lastId = queue.getId();

                    if (processed % batchSize == 0) {
                        migration.updateProgress(processed, failed, lastId);
                        migrationStatusRepository.save(migration);
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to migrate file reception queue: {}", queue.getId(), e);
                }
            }

            migration.updateProgress(processed, failed, lastId);
            migration.complete();
            migrationStatusRepository.save(migration);

            log.info("Migration completed: processed={}, failed={}", processed, failed);
        } catch (Exception e) {
            migration.fail(e.getMessage());
            migrationStatusRepository.save(migration);
            log.error("Migration failed", e);
        }
    }

    public Map<String, Object> switchToNewModel(String tableType) {
        String normalizedTableType = normalizeReadSwitchType(tableType);
        MigrationStatus migration = migrationStatusRepository
                .findByMigrationName("READ_SWITCH_" + normalizedTableType)
                .orElseGet(() -> {
                    MigrationStatus m = MigrationStatus.create("READ_SWITCH_" + normalizedTableType, "READ_SWITCH");
                    return migrationStatusRepository.save(m);
                });

        migration.start();
        migration.setTotalRecords(1L);
        migration.complete();
        migrationStatusRepository.save(migration);

        log.info("Switched to new model for: {}", normalizedTableType);
        return Map.of(
                "table", normalizedTableType,
                "status", "SWITCHED",
                "message", "Read path now uses new model");
    }

    public Map<String, Object> deprecateLegacyTable(String tableName) {
        String normalizedTableName = normalizeLegacyTableName(tableName);
        MigrationStatus migration = migrationStatusRepository
                .findByMigrationName("DEPRECATE_" + normalizedTableName)
                .orElseGet(() -> {
                    MigrationStatus m = MigrationStatus.create("DEPRECATE_" + normalizedTableName, "DEPRECATION");
                    return migrationStatusRepository.save(m);
                });

        migration.start();
        migration.setTotalRecords(1L);
        migration.complete();
        migrationStatusRepository.save(migration);

        log.info("旧表已标记为退役: {}", normalizedTableName);

        Map<String, Object> result = new HashMap<>();
        result.put("table", normalizedTableName);
        result.put("status", "DEPRECATED");
        result.put("message", "旧表已标记为退役，只读保留");
        return result;
    }

    public Map<String, Object> getMigrationHealth() {
        long totalFileRecords = fileAssetRepository.count();
        long totalDispatchRecords = dispatchRecordRepository.count();

        long queuesWithFileRecord = receptionQueueRepository.findAll().stream()
                .filter(q -> q.getFileRecordId() != null)
                .count();
        long totalQueues = receptionQueueRepository.count();

        double coverage = totalQueues > 0 ? (double) queuesWithFileRecord / totalQueues * 100 : 100;

        return Map.of(
                "fileRecordCount",
                totalFileRecords,
                "dispatchRecordCount",
                totalDispatchRecords,
                "receptionQueueCoverage",
                coverage,
                "legacyReceptionQueueCount",
                totalQueues - queuesWithFileRecord,
                "readyForReadSwitch",
                coverage >= 100,
                "readyForDeprecation",
                coverage >= 100);
    }

    private String normalizeReadSwitchType(String tableType) {
        if (tableType == null || tableType.isBlank()) {
            throw new IllegalArgumentException("tableType is required");
        }
        String normalized = tableType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!SUPPORTED_READ_SWITCH_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported migration tableType: " + tableType);
        }
        return normalized;
    }

    private String normalizeLegacyTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName is required");
        }
        String normalized = tableName.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!SUPPORTED_DEPRECATED_TABLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported legacy table: " + tableName);
        }
        return normalized;
    }
}
