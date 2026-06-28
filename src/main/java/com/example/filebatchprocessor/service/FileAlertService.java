package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileAlertLog;
import com.example.filebatchprocessor.repository.FileAlertLogRepository;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class FileAlertService {

    private final FileAlertLogRepository alertLogRepository;
    private final FileAssetRecordRepository fileAssetRepository;
    private final FileDispatchRecordRepository dispatchRecordRepository;
    private final ObjectMapper objectMapper;
    private final AlertDispatcher alertDispatcher;
    private final SchedulerLeaderService schedulerLeaderService;

    @Value("${file.alert.enabled:true}")
    private boolean enabled = true;

    @Value("${file.alert.externalize-min-severity:CRITICAL}")
    private String fileExternalizeMinSeverity = "CRITICAL";

    @Value("${file.alert.timeout.minutes:120}")
    private long timeoutMinutes = 120;

    @Value("${file.alert.unprocessed.threshold:100}")
    private long unprocessedThreshold = 100;

    @Value("${file.alert.dispatch-ack-timeout.minutes:60}")
    private long dispatchAckTimeoutMinutes = 60;

    public FileAlertService(
            FileAlertLogRepository alertLogRepository,
            FileAssetRecordRepository fileAssetRepository,
            FileDispatchRecordRepository dispatchRecordRepository,
            ObjectMapper objectMapper,
            AlertDispatcher alertDispatcher,
            Optional<SchedulerLeaderService> schedulerLeaderService) {
        this.alertLogRepository = alertLogRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.dispatchRecordRepository = dispatchRecordRepository;
        this.objectMapper = objectMapper;
        this.alertDispatcher = alertDispatcher;
        this.schedulerLeaderService = schedulerLeaderService == null ? null : schedulerLeaderService.orElse(null);
    }

    @Scheduled(fixedDelayString = "${file.alert.evaluate-ms:300000}")
    public void evaluateAlerts() {
        if (schedulerLeaderService != null && !schedulerLeaderService.isLeader()) {
            return;
        }
        if (!enabled) {
            return;
        }
        checkFileTimeout();
        checkUnprocessedFiles();
        checkDispatchAckTimeout();
    }

    public void checkFileTimeout() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        var timedOutFiles = fileAssetRepository.findTimeoutFiles("ARRIVED", threshold, 100);

        if (!timedOutFiles.isEmpty()) {
            log.warn(
                    "ALERT: {} files arrived but not processed for > {} minutes", timedOutFiles.size(), timeoutMinutes);

            for (var file : timedOutFiles) {
                createAlert(
                        "FILE_TIMEOUT",
                        "FILE_UNPROCESSED",
                        "WARNING",
                        "File arrived but not processed",
                        file.getId(),
                        file.getSourceSystem(),
                        file.getBizDate(),
                        null,
                        Map.of("arrivedTime", file.getArrivedTime(), "timeoutMinutes", timeoutMinutes));
            }
        }
    }

    public void checkUnprocessedFiles() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        long pending = fileAssetRepository.countPendingFiles(List.of("ARRIVED", "READY"), threshold);

        if (pending >= unprocessedThreshold) {
            log.error("ALERT: {} files pending for > 24 hours (threshold={})", pending, unprocessedThreshold);
            createAlert(
                    "FILE_UNPROCESSED_LONG",
                    "FILE_UNPROCESSED",
                    "CRITICAL",
                    "Large number of files pending for > 24 hours",
                    null,
                    null,
                    null,
                    null,
                    Map.of("pendingCount", pending, "threshold", unprocessedThreshold));
        }
    }

    public void checkDispatchAckTimeout() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(dispatchAckTimeoutMinutes);
        var timedOutDispatches = dispatchRecordRepository.findAckTimeoutDispatches("DISPATCHING", threshold, 100);

        if (!timedOutDispatches.isEmpty()) {
            log.warn("ALERT: {} dispatches waiting for ACK timeout", timedOutDispatches.size());

            for (var dispatch : timedOutDispatches) {
                createAlert(
                        "DISPATCH_ACK_TIMEOUT",
                        "DISPATCH_NO_ACK",
                        "WARNING",
                        "Dispatch waiting for ACK timeout",
                        dispatch.getFileRecordId(),
                        null,
                        null,
                        dispatch.getTargetSystem(),
                        Map.of(
                                "dispatchNo", dispatch.getDispatchNo(),
                                "lastDispatchTime", dispatch.getLastDispatchTime(),
                                "timeoutMinutes", dispatchAckTimeoutMinutes));
            }
        }
    }

    public FileAlertLog createAlert(
            String alertCode,
            String alertType,
            String severity,
            String title,
            Long fileRecordId,
            String sourceSystem,
            String bizDate,
            String targetSystem,
            Map<String, Object> payload) {
        FileAlertLog alert = alertLogRepository
                .findFirstByAlertCodeAndFileRecordIdAndTargetSystemAndResolvedFalseOrderByCreatedAtDesc(
                        alertCode, fileRecordId, targetSystem)
                .orElseGet(FileAlertLog::new);
        alert.setAlertCode(alertCode);
        alert.setAlertType(alertType);
        alert.setSeverity(escalateSeverity(alert.getSeverity(), severity));
        alert.setTitle(title);
        alert.setFileRecordId(fileRecordId);
        alert.setSourceSystem(sourceSystem);
        alert.setBizDate(bizDate);
        alert.setTargetSystem(targetSystem);

        try {
            alert.setPayload(payload != null ? objectMapper.writeValueAsString(payload) : null);
        } catch (Exception e) {
            log.warn("Failed to serialize alert payload", e);
        }

        FileAlertLog saved = alertLogRepository.save(alert);
        try {
            AlertSeverity sev = AlertSeverity.valueOf(saved.getSeverity().trim().toUpperCase());
            AlertSeverity floor =
                    AlertSeverity.valueOf(fileExternalizeMinSeverity.trim().toUpperCase());
            if (sev.ordinal() >= floor.ordinal()) {
                alertDispatcher.dispatch(AlertEvent.of(alertCode, sev, title, payload == null ? Map.of() : payload));
            }
        } catch (Exception e) {
            log.error("file alert externalize failed: code={}", alertCode, e);
        }
        return saved;
    }

    private String escalateSeverity(String existing, String incoming) {
        if (existing == null || existing.isBlank()) {
            return incoming;
        }
        try {
            AlertSeverity current = AlertSeverity.valueOf(existing.trim().toUpperCase());
            AlertSeverity next = AlertSeverity.valueOf(incoming.trim().toUpperCase());
            return next.ordinal() > current.ordinal() ? incoming : existing;
        } catch (Exception e) {
            return incoming;
        }
    }

    public void acknowledgeAlert(Long alertId, String operator) {
        alertLogRepository.findById(alertId).ifPresent(alert -> {
            alert.setAcknowledged(true);
            alert.setAcknowledgedBy(operator);
            alert.setAcknowledgedAt(LocalDateTime.now());
            alertLogRepository.save(alert);
        });
    }

    public void resolveAlert(Long alertId, String operator, String notes) {
        alertLogRepository.findById(alertId).ifPresent(alert -> {
            alert.setResolved(true);
            alert.setResolvedBy(operator);
            alert.setResolvedAt(LocalDateTime.now());
            alert.setResolutionNotes(notes);
            alertLogRepository.save(alert);
        });
    }
}
