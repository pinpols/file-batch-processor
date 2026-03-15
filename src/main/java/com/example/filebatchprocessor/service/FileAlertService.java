package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileAlertLog;
import com.example.filebatchprocessor.repository.FileAlertLogRepository;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
public class FileAlertService {

    private final FileAlertLogRepository alertLogRepository;
    private final FileAssetRecordRepository fileAssetRepository;
    private final FileDispatchRecordRepository dispatchRecordRepository;
    private final ObjectMapper objectMapper;

    @Value("${file.alert.enabled:true}")
    private boolean enabled;
    @Value("${file.alert.timeout.minutes:120}")
    private long timeoutMinutes;
    @Value("${file.alert.unprocessed.threshold:100}")
    private long unprocessedThreshold;
    @Value("${file.alert.dispatch-ack-timeout.minutes:60}")
    private long dispatchAckTimeoutMinutes;

    public FileAlertService(FileAlertLogRepository alertLogRepository,
                           FileAssetRecordRepository fileAssetRepository,
                           FileDispatchRecordRepository dispatchRecordRepository,
                           ObjectMapper objectMapper) {
        this.alertLogRepository = alertLogRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.dispatchRecordRepository = dispatchRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${file.alert.evaluate-ms:300000}")
    public void evaluateAlerts() {
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
            log.warn("ALERT: {} files arrived but not processed for > {} minutes", 
                    timedOutFiles.size(), timeoutMinutes);
            
            for (var file : timedOutFiles) {
                createAlert("FILE_TIMEOUT", "FILE_UNPROCESSED", "WARNING",
                        "File arrived but not processed",
                        file.getId(), file.getSourceSystem(), file.getBizDate(),
                        null, Map.of("arrivedTime", file.getArrivedTime(), "timeoutMinutes", timeoutMinutes));
            }
        }
    }

    public void checkUnprocessedFiles() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        long pending = fileAssetRepository.countPendingFiles(List.of("ARRIVED", "READY"), threshold);
        
        if (pending >= unprocessedThreshold) {
            log.error("ALERT: {} files pending for > 24 hours (threshold={})", pending, unprocessedThreshold);
            createAlert("FILE_UNPROCESSED_LONG", "FILE_UNPROCESSED", "CRITICAL",
                    "Large number of files pending for > 24 hours",
                    null, null, null, null, Map.of("pendingCount", pending, "threshold", unprocessedThreshold));
        }
    }

    public void checkDispatchAckTimeout() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(dispatchAckTimeoutMinutes);
        var timedOutDispatches = dispatchRecordRepository.findAckTimeoutDispatches("DISPATCHING", threshold, 100);
        
        if (!timedOutDispatches.isEmpty()) {
            log.warn("ALERT: {} dispatches waiting for ACK timeout", timedOutDispatches.size());
            
            for (var dispatch : timedOutDispatches) {
                createAlert("DISPATCH_ACK_TIMEOUT", "DISPATCH_NO_ACK", "WARNING",
                        "Dispatch waiting for ACK timeout",
                        dispatch.getFileRecordId(), null, null,
                        dispatch.getTargetSystem(),
                        Map.of("dispatchNo", dispatch.getDispatchNo(), 
                               "lastDispatchTime", dispatch.getLastDispatchTime(),
                               "timeoutMinutes", dispatchAckTimeoutMinutes));
            }
        }
    }

    public FileAlertLog createAlert(String alertCode, String alertType, String severity,
                                    String title, Long fileRecordId, String sourceSystem,
                                    String bizDate, String targetSystem, Map<String, Object> payload) {
        FileAlertLog alert = new FileAlertLog();
        alert.setAlertCode(alertCode);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
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
        
        return alertLogRepository.save(alert);
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
