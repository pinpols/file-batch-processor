package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class FileDispatchRecordService {

    private static final String DISPATCH_PENDING = "PENDING";
    private static final String DISPATCHING = "DISPATCHING";
    private static final String DISPATCH_SUCCESS = "SUCCESS";
    private static final String DISPATCH_FAILED = "FAILED";
    private static final String DISPATCH_RETRY_PENDING = "RETRY_PENDING";

    private static final String ACK_NOT_REQUIRED = "NOT_REQUIRED";
    private static final String ACK_PENDING = "PENDING";
    private static final String ACK_ACKED = "ACKED";
    private static final String ACK_REJECTED = "REJECTED";
    private static final String ACK_TIMEOUT = "TIMEOUT";

    private final FileDispatchRecordRepository repository;
    private final JobExecutionLogService jobExecutionLogService;
    private final ObjectMapper objectMapper;

    public FileDispatchRecordService(FileDispatchRecordRepository repository,
                                     JobExecutionLogService jobExecutionLogService,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.jobExecutionLogService = jobExecutionLogService;
        this.objectMapper = objectMapper;
    }

    public FileDispatchRecord createPendingDispatch(FileAssetRecord fileRecord,
                                                    Long legacyDistributionTaskId,
                                                    String targetSystem,
                                                    String targetAddress,
                                                    Integer maxAttempts) {
        return createPendingDispatch(fileRecord, legacyDistributionTaskId, targetSystem, targetAddress,
                maxAttempts, false, null, null);
    }

    public FileDispatchRecord createPendingDispatch(FileAssetRecord fileRecord,
                                                    Long legacyDistributionTaskId,
                                                    String targetSystem,
                                                    String targetAddress,
                                                    Integer maxAttempts,
                                                    boolean ackRequired,
                                                    Integer ackTimeoutMinutes,
                                                    Long createdJobInstanceId) {
        String dispatchChannel = normalizeChannel(targetSystem);
        String dispatchKey = fileRecord.getFileNo() + "|" + fileRecord.getVersionNo() + "|" +
                dispatchChannel + "|" + normalize(targetAddress);

        Optional<FileDispatchRecord> existing = repository.findByDispatchKey(dispatchKey);
        if (existing.isPresent()) {
            FileDispatchRecord record = existing.get();
            record.setLegacyDistributionTaskId(legacyDistributionTaskId);
            record.setTargetAddress(targetAddress);
            record.setMaxAttempts(maxAttempts == null ? record.getMaxAttempts() : maxAttempts);
            record.setAckRequired(ackRequired);
            record.setAckTimeoutMinutes(normalizeAckTimeoutMinutes(ackTimeoutMinutes));
            if (record.getCreatedJobInstanceId() == null) {
                record.setCreatedJobInstanceId(createdJobInstanceId);
            }
            FileDispatchRecord saved = repository.save(record);
            logJobEvent(createdJobInstanceId, "DISPATCH_CREATED", "INFO",
                    "Existing dispatch record linked to task", saved,
                    dispatchCreatePayload(legacyDistributionTaskId, ackRequired, saved.getAckTimeoutMinutes()));
            return saved;
        }

        FileDispatchRecord record = new FileDispatchRecord();
        record.setDispatchNo(generateDispatchNo());
        record.setDispatchKey(dispatchKey);
        record.setFileRecordId(fileRecord.getId());
        record.setLegacyDistributionTaskId(legacyDistributionTaskId);
        record.setCreatedJobInstanceId(createdJobInstanceId);
        record.setTargetSystem(targetSystem);
        record.setDispatchChannel(dispatchChannel);
        record.setTargetAddress(targetAddress);
        record.setFileVersionNo(fileRecord.getVersionNo());
        record.setDispatchStatus(DISPATCH_PENDING);
        record.setAckRequired(ackRequired);
        record.setAckStatus(ACK_NOT_REQUIRED);
        record.setAckTimeoutMinutes(normalizeAckTimeoutMinutes(ackTimeoutMinutes));
        record.setAttemptCount(0);
        record.setMaxAttempts(maxAttempts == null ? 3 : maxAttempts);
        FileDispatchRecord saved = repository.save(record);
        logJobEvent(createdJobInstanceId, "DISPATCH_CREATED", "INFO",
                "Dispatch record created", saved,
                dispatchCreatePayload(legacyDistributionTaskId, ackRequired, saved.getAckTimeoutMinutes()));
        return saved;
    }

    public Optional<FileDispatchRecord> findByLegacyDistributionTaskId(Long legacyDistributionTaskId) {
        if (legacyDistributionTaskId == null) {
            return Optional.empty();
        }
        return repository.findByLegacyDistributionTaskId(legacyDistributionTaskId);
    }

    public Optional<FileDispatchRecord> findByDispatchNo(String dispatchNo) {
        if (dispatchNo == null || dispatchNo.isBlank()) {
            return Optional.empty();
        }
        return repository.findByDispatchNo(dispatchNo.trim());
    }

    public List<FileDispatchRecord> findAckTimeoutCandidates(LocalDateTime now, int fallbackAckTimeoutMinutes) {
        LocalDateTime reference = now == null ? LocalDateTime.now() : now;
        int fallback = normalizeAckTimeoutMinutes(fallbackAckTimeoutMinutes);
        return repository.findByAckRequiredTrueAndAckStatus(ACK_PENDING).stream()
                .filter(record -> isAckTimedOut(record, reference, fallback))
                .toList();
    }

    public void markDispatching(Long legacyDistributionTaskId, Long jobInstanceId) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus(DISPATCHING);
            record.setLastDispatchTime(LocalDateTime.now());
            record.setLastDispatchJobInstanceId(jobInstanceId);
            repository.save(record);
            logJobEvent(jobInstanceId, "DISPATCH_STARTED", "INFO",
                    "Outbound dispatch started", record, null);
        });
    }

    public void markSuccess(Long legacyDistributionTaskId,
                            Long jobInstanceId,
                            boolean ackRequired,
                            Integer ackTimeoutMinutes,
                            Map<String, Object> responsePayload) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus(DISPATCH_SUCCESS);
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setLastDispatchTime(LocalDateTime.now());
            record.setLastDispatchJobInstanceId(jobInstanceId);
            record.setAckRequired(ackRequired || Boolean.TRUE.equals(record.getAckRequired()));
            record.setAckTimeoutMinutes(normalizeAckTimeoutMinutes(
                    ackTimeoutMinutes == null ? record.getAckTimeoutMinutes() : ackTimeoutMinutes));
            record.setErrorCode(null);
            record.setErrorMsg(null);
            if (Boolean.TRUE.equals(record.getAckRequired())) {
                record.setAckStatus(ACK_PENDING);
                record.setAckDeadlineAt(record.getLastDispatchTime().plusMinutes(record.getAckTimeoutMinutes()));
                record.setAckTime(null);
                record.setAckMessage(null);
                record.setAckPayload(null);
            } else {
                record.setAckStatus(ACK_NOT_REQUIRED);
                record.setAckDeadlineAt(null);
                record.setAckTime(null);
                record.setAckMessage(null);
                record.setAckPayload(null);
            }
            repository.save(record);

            Map<String, Object> payload = new LinkedHashMap<>();
            if (responsePayload != null && !responsePayload.isEmpty()) {
                payload.putAll(responsePayload);
            }
            payload.put("ackRequired", record.getAckRequired());
            payload.put("ackStatus", record.getAckStatus());
            if (record.getAckDeadlineAt() != null) {
                payload.put("ackDeadlineAt", record.getAckDeadlineAt().toString());
            }
            logJobEvent(jobInstanceId,
                    Boolean.TRUE.equals(record.getAckRequired()) ? "DISPATCH_ACK_PENDING" : "DISPATCH_SUCCEEDED",
                    "INFO",
                    Boolean.TRUE.equals(record.getAckRequired())
                            ? "Outbound dispatch succeeded and is waiting for ack"
                            : "Outbound dispatch succeeded",
                    record,
                    payload);
        });
    }

    public void markRetryPending(Long legacyDistributionTaskId, String errorMessage, Long jobInstanceId) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus(DISPATCH_RETRY_PENDING);
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setLastDispatchTime(LocalDateTime.now());
            record.setLastDispatchJobInstanceId(jobInstanceId);
            record.setNextRetryAt(LocalDateTime.now());
            record.setErrorMsg(truncate(errorMessage, 1000));
            repository.save(record);
            logJobEvent(jobInstanceId, "DISPATCH_RETRY_PENDING", "WARN",
                    "Outbound dispatch moved to retry pending", record,
                    messagePayload(errorMessage));
        });
    }

    public void markFailed(Long legacyDistributionTaskId, String errorMessage, Long jobInstanceId) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus(DISPATCH_FAILED);
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setLastDispatchTime(LocalDateTime.now());
            record.setLastDispatchJobInstanceId(jobInstanceId);
            record.setErrorMsg(truncate(errorMessage, 1000));
            repository.save(record);
            logJobEvent(jobInstanceId, "DISPATCH_FAILED", "ERROR",
                    "Outbound dispatch failed", record,
                    messagePayload(errorMessage));
        });
    }

    public void markPendingForRetry(Long legacyDistributionTaskId, Long jobInstanceId, boolean resend) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus(DISPATCH_PENDING);
            record.setNextRetryAt(null);
            if (resend) {
                record.setResendCount(record.getResendCount() + 1);
            }
            repository.save(record);
            logJobEvent(jobInstanceId,
                    resend ? "DISPATCH_RESEND_REQUESTED" : "DISPATCH_RETRY_REQUESTED",
                    "INFO",
                    resend ? "Dispatch resend requested" : "Dispatch retry requested",
                    record,
                    Map.of("resendCount", record.getResendCount()));
        });
    }

    public Optional<FileDispatchRecord> markAckReceived(Long legacyDistributionTaskId,
                                                        Long jobInstanceId,
                                                        boolean accepted,
                                                        String operatorName,
                                                        String ackMessage,
                                                        Map<String, Object> ackPayload) {
        return findByLegacyDistributionTaskId(legacyDistributionTaskId).map(record -> {
            record.setAckRequired(Boolean.TRUE);
            record.setAckStatus(accepted ? ACK_ACKED : ACK_REJECTED);
            record.setAckTime(LocalDateTime.now());
            record.setAckDeadlineAt(null);
            record.setLastAckJobInstanceId(jobInstanceId);
            record.setAckMessage(truncate(ackMessage, 1000));
            record.setAckPayload(toJson(ackPayload));
            if (!accepted) {
                record.setDispatchStatus(DISPATCH_FAILED);
            }
            FileDispatchRecord saved = repository.save(record);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ackStatus", saved.getAckStatus());
            if (ackPayload != null && !ackPayload.isEmpty()) {
                payload.putAll(ackPayload);
            }
            if (operatorName != null && !operatorName.isBlank()) {
                payload.put("operatorName", operatorName);
            }
            logJobEvent(jobInstanceId,
                    accepted ? "DISPATCH_ACKED" : "DISPATCH_ACK_REJECTED",
                    accepted ? "INFO" : "WARN",
                    accepted ? "Dispatch ack received" : "Dispatch ack rejected",
                    saved,
                    payload);
            return saved;
        });
    }

    public Optional<FileDispatchRecord> markAckTimeout(Long legacyDistributionTaskId,
                                                       Long jobInstanceId,
                                                       String message,
                                                       Map<String, Object> extra) {
        return findByLegacyDistributionTaskId(legacyDistributionTaskId).map(record -> {
            record.setAckRequired(Boolean.TRUE);
            record.setAckStatus(ACK_TIMEOUT);
            record.setAckDeadlineAt(null);
            record.setLastAckJobInstanceId(jobInstanceId);
            record.setAckMessage(truncate(message, 1000));
            if (extra != null && !extra.isEmpty()) {
                record.setAckPayload(toJson(extra));
            }
            record.setDispatchStatus(DISPATCH_RETRY_PENDING);
            FileDispatchRecord saved = repository.save(record);
            logJobEvent(jobInstanceId, "DISPATCH_ACK_TIMEOUT", "WARN",
                    "Dispatch ack timed out", saved, extra);
            return saved;
        });
    }

    private boolean isAckTimedOut(FileDispatchRecord record, LocalDateTime now, int fallbackAckTimeoutMinutes) {
        if (record.getAckDeadlineAt() != null) {
            return !record.getAckDeadlineAt().isAfter(now);
        }
        if (record.getLastDispatchTime() == null) {
            return false;
        }
        int minutes = normalizeAckTimeoutMinutes(record.getAckTimeoutMinutes() == null
                ? fallbackAckTimeoutMinutes
                : record.getAckTimeoutMinutes());
        return !record.getLastDispatchTime().plusMinutes(minutes).isAfter(now);
    }

    private void logJobEvent(Long jobInstanceId,
                             String eventType,
                             String level,
                             String message,
                             FileDispatchRecord record,
                             Map<String, Object> extra) {
        if (jobInstanceId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (record != null) {
            payload.put("dispatchRecordId", record.getId());
            payload.put("dispatchNo", record.getDispatchNo());
            payload.put("dispatchStatus", record.getDispatchStatus());
            payload.put("ackStatus", record.getAckStatus());
            payload.put("targetSystem", record.getTargetSystem());
            payload.put("targetAddress", record.getTargetAddress());
            payload.put("legacyDistributionTaskId", record.getLegacyDistributionTaskId());
        }
        if (extra != null && !extra.isEmpty()) {
            payload.putAll(extra);
        }
        jobExecutionLogService.log(jobInstanceId, null, eventType, level, message, null, payload);
    }

    private String normalizeChannel(String targetSystem) {
        if (targetSystem == null || targetSystem.isBlank()) {
            return "UNKNOWN";
        }
        return targetSystem.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private int normalizeAckTimeoutMinutes(Integer ackTimeoutMinutes) {
        return ackTimeoutMinutes == null || ackTimeoutMinutes < 1 ? 120 : ackTimeoutMinutes;
    }

    private String generateDispatchNo() {
        return "FD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String truncate(String raw, int maxLength) {
        if (raw == null || raw.length() <= maxLength) {
            return raw;
        }
        return raw.substring(0, maxLength);
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return payload.toString();
        }
    }

    private Map<String, Object> dispatchCreatePayload(Long legacyDistributionTaskId,
                                                      boolean ackRequired,
                                                      Integer ackTimeoutMinutes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (legacyDistributionTaskId != null) {
            payload.put("legacyDistributionTaskId", legacyDistributionTaskId);
        }
        payload.put("ackRequired", ackRequired);
        payload.put("ackTimeoutMinutes", ackTimeoutMinutes);
        return payload;
    }

    private Map<String, Object> messagePayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (message != null && !message.isBlank()) {
            payload.put("errorMessage", truncate(message, 1000));
        }
        return payload;
    }
}
