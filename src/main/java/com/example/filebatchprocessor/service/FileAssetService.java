package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileAssetStatus;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class FileAssetService {

    private static final DateTimeFormatter FILE_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final FileAssetRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final FileAssetStateMachineService stateMachineService;

    @Autowired
    public FileAssetService(FileAssetRecordRepository repository,
                            ObjectMapper objectMapper,
                            FileAssetStateMachineService stateMachineService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.stateMachineService = stateMachineService;
    }

    public FileAssetRecord registerInboundFile(String fileName,
                                               String filePath,
                                               String sourceSystem,
                                               Long fileSize,
                                               String fileHash,
                                               String status,
                                               Map<String, Object> metadata) {
        return registerFile(
                "INBOUND",
                fileName,
                filePath,
                sourceSystem,
                "FILE_RECEPTION",
                null,
                null,
                null,
                fileSize,
                fileHash,
                "MD5",
                status,
                metadata
        );
    }

    public FileAssetRecord registerOutboundFile(String fileName,
                                                String filePath,
                                                String bizType,
                                                String batchDate,
                                                String tenantId,
                                                String bizDomain,
                                                String status,
                                                Map<String, Object> metadata) {
        return registerFile(
                "OUTBOUND",
                fileName,
                filePath,
                null,
                bizType,
                batchDate,
                tenantId,
                bizDomain,
                null,
                null,
                "MD5",
                status,
                metadata
        );
    }

    public Optional<FileAssetRecord> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<FileAssetRecord> findLatestByStoredPath(String storedPath) {
        return repository.findFirstByStoredPathAndLatestVersionTrueOrderByCreatedAtDesc(storedPath);
    }

    public Optional<FileAssetRecord> findDuplicateInbound(String sourceSystem, String fileHash) {
        if (fileHash == null || fileHash.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKey(buildInboundIdempotencyKey(sourceSystem, fileHash, null));
    }

    public FileAssetStateMachineService.TransitionResult markWaiting(Long fileRecordId, String waitReason) {
        return stateMachineService.annotate(fileRecordId, Map.of("waitReason", waitReason));
    }

    public FileAssetStateMachineService.TransitionResult markReady(Long fileRecordId, Map<String, Object> metadata) {
        return stateMachineService.transition(fileRecordId, FileAssetStatus.READY, "markReady", metadata);
    }

    public FileAssetStateMachineService.TransitionResult markProcessing(Long fileRecordId) {
        return stateMachineService.transition(fileRecordId, FileAssetStatus.PROCESSING, "markProcessing", null);
    }

    public FileAssetStateMachineService.TransitionResult markProcessed(Long fileRecordId) {
        return stateMachineService.transition(fileRecordId, FileAssetStatus.PROCESSED, "markProcessed", null);
    }

    public FileAssetStateMachineService.TransitionResult markDispatching(Long fileRecordId) {
        return stateMachineService.transition(fileRecordId, FileAssetStatus.DISPATCHING, "markDispatching", null);
    }

    public FileAssetStateMachineService.TransitionResult markDispatched(Long fileRecordId) {
        return stateMachineService.transition(fileRecordId, FileAssetStatus.DISPATCHED, "markDispatched", null);
    }

    public FileAssetStateMachineService.TransitionResult markFailed(Long fileRecordId, String errorMessage) {
        return stateMachineService.transition(
                fileRecordId,
                FileAssetStatus.FAILED,
                "markFailed",
                Map.of("lastError", truncate(errorMessage, 1000))
        );
    }

    public FileAssetStateMachineService.TransitionResult resetToProcessed(Long fileRecordId, Map<String, Object> metadata) {
        return stateMachineService.transition(fileRecordId, FileAssetStatus.PROCESSED, "resetToProcessed", metadata);
    }

    public FileAssetStateMachineService.TransitionResult resetToReady(Long fileRecordId, Map<String, Object> metadata) {
        return stateMachineService.transition(fileRecordId, FileAssetStatus.READY, "resetToReady", metadata);
    }

    private FileAssetRecord registerFile(String fileDirection,
                                         String fileName,
                                         String filePath,
                                         String sourceSystem,
                                         String bizType,
                                         String batchDate,
                                         String tenantId,
                                         String bizDomain,
                                         Long fileSize,
                                         String fileHash,
                                         String hashAlgorithm,
                                         String status,
                                         Map<String, Object> metadata) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        String resolvedHashAlgorithm = hashAlgorithm == null ? "MD5" : hashAlgorithm;
        String resolvedFileHash = fileHash != null ? fileHash : safeHash(path, resolvedHashAlgorithm);
        String idempotencyKey = buildIdempotencyKey(fileDirection, sourceSystem, batchDate, resolvedFileHash);
        if (idempotencyKey != null) {
            Optional<FileAssetRecord> existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Duplicate file content already received: fileNo="
                        + existing.get().getFileNo());
            }
        }

        FileAssetRecord previous = repository.findFirstByStoredPathAndLatestVersionTrueOrderByCreatedAtDesc(path.toString())
                .orElse(null);

        if (previous != null) {
            previous.setLatestVersion(Boolean.FALSE);
            repository.save(previous);
        }

        FileAssetStatus initialStatus = FileAssetStatus.from(status);
        FileAssetRecord record = new FileAssetRecord();
        record.setFileNo(generateFileNo());
        record.setSourceSystem(sourceSystem);
        record.setBizType(bizType);
        record.setFileDirection(fileDirection);
        record.setOriginalName(fileName);
        record.setStoredName(path.getFileName().toString());
        record.setStoredPath(path.toString());
        record.setStorageType("LOCAL");
        record.setFileSize(fileSize != null ? fileSize : safeSize(path));
        record.setFileHash(resolvedFileHash);
        record.setIdempotencyKey(idempotencyKey);
        record.setHashAlgorithm(resolvedHashAlgorithm);
        record.setFileExt(extractExtension(fileName));
        record.setMimeType(resolveMimeType(path));
        record.setCharset(null);
        record.setBizDate(batchDate);
        record.setBatchNo(null);
        record.setTenantId(tenantId);
        record.setBizDomain(bizDomain);
        record.setParentFileId(previous == null ? null : previous.getId());
        record.setVersionNo(previous == null ? 1 : previous.getVersionNo() + 1);
        record.setLatestVersion(Boolean.TRUE);
        record.setArchiveRequired(Boolean.FALSE);
        record.setArchived(Boolean.FALSE);
        record.setDeletable(Boolean.FALSE);
        record.setDeletedFlag(Boolean.FALSE);
        record.setMetadata(toJson(metadata));
        stateMachineService.initialize(record, initialStatus);
        try {
            return repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey != null) {
                throw new IllegalArgumentException("Duplicate file content already received: key=" + idempotencyKey, ex);
            }
            throw ex;
        }
    }

    private String generateFileNo() {
        return "FR-" + LocalDate.now().format(FILE_NO_DATE) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private String resolveMimeType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (IOException e) {
            return null;
        }
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to determine file size for " + path, e);
        }
    }

    private String safeHash(Path path, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm == null ? "MD5" : algorithm);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Failed to hash file " + path, e);
        }
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize file metadata", e);
        }
    }

    private String truncate(String raw, int maxLength) {
        if (raw == null || raw.length() <= maxLength) {
            return raw;
        }
        return raw.substring(0, maxLength);
    }

    private String buildIdempotencyKey(String fileDirection,
                                       String sourceSystem,
                                       String batchDate,
                                       String fileHash) {
        if (!"INBOUND".equalsIgnoreCase(fileDirection) || fileHash == null || fileHash.isBlank()) {
            return null;
        }
        return buildInboundIdempotencyKey(sourceSystem, fileHash, batchDate);
    }

    private String buildInboundIdempotencyKey(String sourceSystem, String fileHash, String batchDate) {
        String normalizedSource = sourceSystem == null || sourceSystem.isBlank()
                ? "UNKNOWN" : sourceSystem.trim().toUpperCase();
        String normalizedBizDate = batchDate == null || batchDate.isBlank()
                ? "NA" : batchDate.trim();
        return "INBOUND|" + normalizedSource + "|" + normalizedBizDate + "|" + fileHash.trim().toUpperCase();
    }
}
