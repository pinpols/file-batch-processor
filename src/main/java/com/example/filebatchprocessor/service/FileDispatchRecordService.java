package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class FileDispatchRecordService {

    private final FileDispatchRecordRepository repository;

    @Autowired
    public FileDispatchRecordService(FileDispatchRecordRepository repository) {
        this.repository = repository;
    }

    public FileDispatchRecord createPendingDispatch(FileAssetRecord fileRecord,
                                                    Long legacyDistributionTaskId,
                                                    String targetSystem,
                                                    String targetAddress,
                                                    Integer maxAttempts) {
        String dispatchChannel = normalizeChannel(targetSystem);
        String dispatchKey = fileRecord.getFileNo() + "|" + fileRecord.getVersionNo() + "|" +
                dispatchChannel + "|" + normalize(targetAddress);

        Optional<FileDispatchRecord> existing = repository.findByDispatchKey(dispatchKey);
        if (existing.isPresent()) {
            FileDispatchRecord record = existing.get();
            record.setLegacyDistributionTaskId(legacyDistributionTaskId);
            record.setTargetAddress(targetAddress);
            record.setMaxAttempts(maxAttempts == null ? record.getMaxAttempts() : maxAttempts);
            return repository.save(record);
        }

        FileDispatchRecord record = new FileDispatchRecord();
        record.setDispatchNo(generateDispatchNo());
        record.setDispatchKey(dispatchKey);
        record.setFileRecordId(fileRecord.getId());
        record.setLegacyDistributionTaskId(legacyDistributionTaskId);
        record.setTargetSystem(targetSystem);
        record.setDispatchChannel(dispatchChannel);
        record.setTargetAddress(targetAddress);
        record.setFileVersionNo(fileRecord.getVersionNo());
        record.setDispatchStatus("PENDING");
        record.setAckStatus("NOT_REQUIRED");
        record.setAttemptCount(0);
        record.setMaxAttempts(maxAttempts == null ? 3 : maxAttempts);
        return repository.save(record);
    }

    public Optional<FileDispatchRecord> findByLegacyDistributionTaskId(Long legacyDistributionTaskId) {
        if (legacyDistributionTaskId == null) {
            return Optional.empty();
        }
        return repository.findByLegacyDistributionTaskId(legacyDistributionTaskId);
    }

    public void markDispatching(Long legacyDistributionTaskId) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus("DISPATCHING");
            record.setLastDispatchTime(LocalDateTime.now());
            repository.save(record);
        });
    }

    public void markSuccess(Long legacyDistributionTaskId) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus("SUCCESS");
            record.setLastDispatchTime(LocalDateTime.now());
            record.setErrorCode(null);
            record.setErrorMsg(null);
            repository.save(record);
        });
    }

    public void markRetryPending(Long legacyDistributionTaskId, String errorMessage) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus("RETRY_PENDING");
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setLastDispatchTime(LocalDateTime.now());
            record.setNextRetryAt(LocalDateTime.now());
            record.setErrorMsg(truncate(errorMessage, 1000));
            repository.save(record);
        });
    }

    public void markFailed(Long legacyDistributionTaskId, String errorMessage) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus("FAILED");
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setLastDispatchTime(LocalDateTime.now());
            record.setErrorMsg(truncate(errorMessage, 1000));
            repository.save(record);
        });
    }

    public void markPendingForRetry(Long legacyDistributionTaskId) {
        findByLegacyDistributionTaskId(legacyDistributionTaskId).ifPresent(record -> {
            record.setDispatchStatus("PENDING");
            record.setNextRetryAt(null);
            repository.save(record);
        });
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

    private String generateDispatchNo() {
        return "FD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String truncate(String raw, int maxLength) {
        if (raw == null || raw.length() <= maxLength) {
            return raw;
        }
        return raw.substring(0, maxLength);
    }
}
