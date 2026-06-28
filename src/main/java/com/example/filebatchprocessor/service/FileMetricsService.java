package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileMetricsSnapshot;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.repository.FileMetricsSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class FileMetricsService {

    private final FileAssetRecordRepository fileAssetRepository;
    private final FileDispatchRecordRepository dispatchRecordRepository;
    private final FileMetricsSnapshotRepository metricsRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final SchedulerLeaderService schedulerLeaderService;

    @Value("${file.metrics.enabled:true}")
    private boolean enabled;

    public FileMetricsService(
            FileAssetRecordRepository fileAssetRepository,
            FileDispatchRecordRepository dispatchRecordRepository,
            FileMetricsSnapshotRepository metricsRepository,
            DlqRecordRepository dlqRecordRepository,
            Optional<SchedulerLeaderService> schedulerLeaderService) {
        this.fileAssetRepository = fileAssetRepository;
        this.dispatchRecordRepository = dispatchRecordRepository;
        this.metricsRepository = metricsRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.schedulerLeaderService = schedulerLeaderService == null ? null : schedulerLeaderService.orElse(null);
    }

    @Scheduled(cron = "${file.metrics.cron:0 0 * * * *}")
    public void captureMetrics() {
        if (schedulerLeaderService != null && !schedulerLeaderService.isLeader()) {
            return;
        }
        if (!enabled) {
            return;
        }

        try {
            FileMetricsSnapshot snapshot = new FileMetricsSnapshot();
            snapshot.setSnapshotTime(LocalDateTime.now());
            snapshot.setMetricDate(LocalDate.now());

            snapshot.setReceivedCount(fileAssetRepository.countByStatusAndFileDirection("ARRIVED", "INBOUND")
                    + fileAssetRepository.countByStatusAndFileDirection("READY", "INBOUND"));
            snapshot.setProcessedCount(fileAssetRepository.countByStatusAndFileDirection("PROCESSED", "INBOUND"));
            snapshot.setFailedCount(fileAssetRepository.countByStatusAndFileDirection("FAILED", "INBOUND"));
            snapshot.setProcessingCount(fileAssetRepository.countByStatusAndFileDirection("PROCESSING", "INBOUND"));
            snapshot.setPendingCount(fileAssetRepository.countPendingFiles(
                    List.of("ARRIVED", "READY"), LocalDateTime.now().minusHours(24)));

            snapshot.setDispatchCount(dispatchRecordRepository.count());
            snapshot.setDispatchSuccessCount(dispatchRecordRepository.countByDispatchStatus("SUCCESS"));
            snapshot.setDispatchFailedCount(dispatchRecordRepository.countByDispatchStatus("FAILED"));
            snapshot.setDispatchPendingCount(dispatchRecordRepository.countByDispatchStatusInAndAckRequired(
                    List.of("PENDING", "DISPATCHING", "RETRY_PENDING"), true));
            snapshot.setAckTimeoutCount(dispatchRecordRepository.countByAckStatus("TIMEOUT"));

            snapshot.setArchiveCount(fileAssetRepository.countByStatusAndFileDirection("ARCHIVED", "INBOUND"));
            snapshot.setDlqCount(dlqRecordRepository.countByHandledFalse());

            metricsRepository.save(snapshot);
            log.info(
                    "Captured file metrics snapshot: received={}, processed={}, failed={}, dispatch={}, dlq={}",
                    snapshot.getReceivedCount(),
                    snapshot.getProcessedCount(),
                    snapshot.getFailedCount(),
                    snapshot.getDispatchCount(),
                    snapshot.getDlqCount());
        } catch (Exception e) {
            log.error("Failed to capture file metrics", e);
        }
    }

    public FileMetricsSnapshot getTodayMetrics() {
        return metricsRepository
                .findFirstByMetricDateOrderBySnapshotTimeDesc(LocalDate.now())
                .orElse(null);
    }

    public List<FileMetricsSnapshot> getMetricsHistory(int days) {
        return metricsRepository.findAll().stream()
                .filter(s -> s.getMetricDate().isAfter(LocalDate.now().minusDays(days)))
                .toList();
    }
}
