package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.InvalidFileStateTransitionException;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileAssetStatus;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FileAssetStateMachineService {

    private static final Map<FileAssetStatus, EnumSet<FileAssetStatus>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

    private final FileAssetRecordRepository repository;
    private final FileProcessLogService processLogService;
    private final ObjectMapper objectMapper;

    public FileAssetStateMachineService(
            FileAssetRecordRepository repository, FileProcessLogService processLogService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.processLogService = processLogService;
        this.objectMapper = objectMapper;
    }

    public TransitionResult transition(
            Long fileRecordId, FileAssetStatus targetStatus, String reason, Map<String, Object> metadataDelta) {
        FileAssetRecord record = repository
                .findById(fileRecordId)
                .orElseThrow(() -> new IllegalArgumentException("File record not found: " + fileRecordId));

        FileAssetStatus currentStatus = FileAssetStatus.from(record.getStatus());
        validateTransition(fileRecordId, currentStatus, targetStatus, reason);

        applyStatus(record, targetStatus);
        mergeMetadata(record, metadataDelta);
        FileAssetRecord saved = repository.save(record);

        processLogService.log(
                fileRecordId,
                "STATE_TRANSITION",
                "STATUS_CHANGE",
                currentStatus.name(),
                targetStatus.name(),
                "SUCCESS",
                null,
                null,
                0,
                null,
                reason,
                metadataDelta);

        return new TransitionResult(saved, currentStatus, targetStatus);
    }

    public TransitionResult annotate(Long fileRecordId, Map<String, Object> metadataDelta) {
        FileAssetRecord record = repository
                .findById(fileRecordId)
                .orElseThrow(() -> new IllegalArgumentException("File record not found: " + fileRecordId));
        FileAssetStatus currentStatus = FileAssetStatus.from(record.getStatus());
        mergeMetadata(record, metadataDelta);
        return new TransitionResult(repository.save(record), currentStatus, currentStatus);
    }

    public FileAssetRecord initialize(FileAssetRecord record, FileAssetStatus initialStatus) {
        applyStatus(record, initialStatus);
        return record;
    }

    private void validateTransition(
            Long fileRecordId, FileAssetStatus currentStatus, FileAssetStatus targetStatus, String reason) {
        if (currentStatus == targetStatus) {
            return;
        }
        EnumSet<FileAssetStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(FileAssetStatus.class));
        if (!allowed.contains(targetStatus)) {
            throw new InvalidFileStateTransitionException(fileRecordId, currentStatus, targetStatus, reason);
        }
    }

    private void applyStatus(FileAssetRecord record, FileAssetStatus targetStatus) {
        LocalDateTime now = LocalDateTime.now();
        record.setStatus(targetStatus.name());
        switch (targetStatus) {
            case UPLOADING -> {
                // no-op
            }
            case ARRIVED -> {
                if (record.getArrivedTime() == null) {
                    record.setArrivedTime(now);
                }
            }
            case READY -> {
                if (record.getArrivedTime() == null) {
                    record.setArrivedTime(now);
                }
                if (record.getReadyTime() == null) {
                    record.setReadyTime(now);
                }
                record.setIntegrityVerified(Boolean.TRUE);
            }
            case PROCESSING -> {
                if (record.getArrivedTime() == null) {
                    record.setArrivedTime(now);
                }
                if (record.getReadyTime() == null) {
                    record.setReadyTime(now);
                }
                record.setIntegrityVerified(Boolean.TRUE);
                record.setProcessingStartTime(now);
            }
            case PROCESSED -> {
                if (record.getArrivedTime() == null) {
                    record.setArrivedTime(now);
                }
                record.setProcessedTime(now);
            }
            case FAILED -> {
                if (record.getArrivedTime() == null) {
                    record.setArrivedTime(now);
                }
            }
            case DISPATCHING -> {
                if (record.getProcessedTime() == null) {
                    record.setProcessedTime(now);
                }
            }
            case DISPATCHED -> {
                if (record.getProcessedTime() == null) {
                    record.setProcessedTime(now);
                }
            }
            case ARCHIVED -> {
                record.setArchived(Boolean.TRUE);
                record.setArchivedTime(now);
            }
        }
    }

    private void mergeMetadata(FileAssetRecord record, Map<String, Object> metadataDelta) {
        if (metadataDelta == null || metadataDelta.isEmpty()) {
            return;
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        if (record.getMetadata() != null && !record.getMetadata().isBlank()) {
            try {
                merged.putAll(objectMapper.readValue(record.getMetadata(), new TypeReference<>() {}));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to parse file asset metadata JSON", e);
            }
        }
        merged.putAll(metadataDelta);
        try {
            record.setMetadata(objectMapper.writeValueAsString(merged));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize file asset metadata JSON", e);
        }
    }

    private static Map<FileAssetStatus, EnumSet<FileAssetStatus>> buildAllowedTransitions() {
        Map<FileAssetStatus, EnumSet<FileAssetStatus>> transitions = new EnumMap<>(FileAssetStatus.class);
        transitions.put(FileAssetStatus.UPLOADING, EnumSet.of(FileAssetStatus.ARRIVED, FileAssetStatus.FAILED));
        transitions.put(FileAssetStatus.ARRIVED, EnumSet.of(FileAssetStatus.READY, FileAssetStatus.FAILED));
        transitions.put(FileAssetStatus.READY, EnumSet.of(FileAssetStatus.PROCESSING, FileAssetStatus.FAILED));
        transitions.put(FileAssetStatus.PROCESSING, EnumSet.of(FileAssetStatus.PROCESSED, FileAssetStatus.FAILED));
        transitions.put(FileAssetStatus.PROCESSED, EnumSet.of(FileAssetStatus.DISPATCHING, FileAssetStatus.ARCHIVED));
        transitions.put(
                FileAssetStatus.DISPATCHING,
                EnumSet.of(FileAssetStatus.PROCESSED, FileAssetStatus.DISPATCHED, FileAssetStatus.FAILED));
        transitions.put(
                FileAssetStatus.DISPATCHED,
                EnumSet.of(FileAssetStatus.PROCESSED, FileAssetStatus.FAILED, FileAssetStatus.ARCHIVED));
        transitions.put(FileAssetStatus.FAILED, EnumSet.of(FileAssetStatus.READY, FileAssetStatus.PROCESSED));
        transitions.put(FileAssetStatus.ARCHIVED, EnumSet.noneOf(FileAssetStatus.class));
        return transitions;
    }

    public record TransitionResult(FileAssetRecord record, FileAssetStatus from, FileAssetStatus to) {}
}
