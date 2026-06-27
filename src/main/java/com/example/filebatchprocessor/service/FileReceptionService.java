package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.manifest.JsonManifestParser;
import com.example.filebatchprocessor.manifest.ParsedManifest;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件接收服务：
 * 1. 接收和注册文件
 * 2. 监听文件状态
 * 3. 管理文件等待和重试
 */
@Slf4j
@Service
@Transactional
public class FileReceptionService {

    private final FileReceptionQueueRepository fileReceptionQueueRepository;
    private final FileAssetService fileAssetService;
    private final FileProcessLogService fileProcessLogService;
    private final FileReceptionGuardService fileReceptionGuardService;
    private final JsonManifestParser manifestParser;
    private final ReceptionGroupService receptionGroupService;
    private final String manifestSuffix;
    private final boolean groupEnabled;

    @Autowired
    public FileReceptionService(
            FileReceptionQueueRepository fileReceptionQueueRepository,
            FileAssetService fileAssetService,
            FileProcessLogService fileProcessLogService,
            FileReceptionGuardService fileReceptionGuardService,
            JsonManifestParser manifestParser,
            ReceptionGroupService receptionGroupService,
            @Value("${batch.file.reception.group.manifest-suffix:.manifest.json}") String manifestSuffix,
            @Value("${batch.file.reception.group.enabled:false}") boolean groupEnabled) {
        this.fileReceptionQueueRepository = fileReceptionQueueRepository;
        this.fileAssetService = fileAssetService;
        this.fileProcessLogService = fileProcessLogService;
        this.fileReceptionGuardService = fileReceptionGuardService;
        this.manifestParser = manifestParser;
        this.receptionGroupService = receptionGroupService;
        this.manifestSuffix = manifestSuffix;
        this.groupEnabled = groupEnabled;
    }

    public FileReceptionService(FileReceptionQueueRepository fileReceptionQueueRepository) {
        this(fileReceptionQueueRepository, null, null, FileReceptionGuardService.testingDefaults());
    }

    public FileReceptionService(
            FileReceptionQueueRepository fileReceptionQueueRepository,
            FileAssetService fileAssetService,
            FileProcessLogService fileProcessLogService) {
        this(
                fileReceptionQueueRepository,
                fileAssetService,
                fileProcessLogService,
                FileReceptionGuardService.testingDefaults());
    }

    public FileReceptionService(
            FileReceptionQueueRepository fileReceptionQueueRepository,
            FileAssetService fileAssetService,
            FileProcessLogService fileProcessLogService,
            FileReceptionGuardService fileReceptionGuardService) {
        this(
                fileReceptionQueueRepository,
                fileAssetService,
                fileProcessLogService,
                fileReceptionGuardService,
                null,
                null,
                ".manifest.json",
                false);
    }

    /**
     * 接收文件：将文件注册到接收队列
     */
    public FileReceptionQueue receiveFile(String fileName, String filePath, String sourceSystem) {
        log.info("Receiving file: {} from {}", fileName, sourceSystem);

        if (groupEnabled
                && manifestParser != null
                && receptionGroupService != null
                && fileName != null
                && fileName.endsWith(manifestSuffix)) {
            try {
                String content = Files.readString(Path.of(filePath));
                ParsedManifest parsed = manifestParser.parse(content);
                receptionGroupService.registerFromManifest(parsed);
                log.info("Manifest recognized and registered as reception group: fileName={}", fileName);
            } catch (IOException e) {
                log.error("Failed to read manifest file: {}", fileName, e);
                throw new RuntimeException("Failed to read manifest file: " + e.getMessage(), e);
            }
            // manifest 本身不当作数据文件入队
            return null;
        }

        Optional<FileReceptionQueue> existingByName = fileReceptionQueueRepository.findByFileName(fileName);

        try {
            File file = new File(filePath);
            if (existingByName.isPresent() && !file.exists()) {
                log.warn("Duplicate file name detected before file inspection: {}", fileName);
                throw new IllegalArgumentException("File already exists: " + fileName);
            }
            if (!file.exists()) {
                throw new IOException("File not found: " + filePath);
            }
            fileReceptionGuardService.assertReceivable(fileName, filePath);

            Long fileSize = file.length();
            String fileHash = calculateHash(filePath);
            rejectDuplicateOrConflict(existingByName, fileName, sourceSystem, fileSize, fileHash);
            FileAssetRecord fileRecord =
                    registerInboundFileRecord(fileName, filePath, sourceSystem, fileSize, fileHash);

            FileReceptionQueue queue = new FileReceptionQueue();
            queue.setFileName(fileName);
            queue.setFilePath(filePath);
            queue.setFileSize(fileSize);
            queue.setFileHash(fileHash);
            queue.setSourceSystem(sourceSystem);
            queue.setStatus("RECEIVED");
            queue.setCreatedAt(LocalDateTime.now());
            if (fileRecord != null) {
                queue.setFileRecordId(fileRecord.getId());
            }

            FileReceptionQueue saved = fileReceptionQueueRepository.save(queue);
            logFileProcess(
                    saved.getFileRecordId(),
                    "receiveFile",
                    "RECEIVE",
                    null,
                    "ARRIVED",
                    "SUCCESS",
                    null,
                    null,
                    0,
                    Map.of("sourceSystem", sourceSystem));
            log.info("File received successfully: id={}, fileName={}", saved.getId(), fileName);
            return saved;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to receive file: {}", fileName, e);
            throw new RuntimeException("Failed to receive file: " + e.getMessage(), e);
        }
    }

    /**
     * 标记文件为等待状态（如依赖其他文件或需要特定条件）
     */
    public void markAsWaiting(Long fileId, String waitReason) {
        log.info("Marking file as waiting: id={}, reason={}", fileId, waitReason);

        FileReceptionQueue queue = loadQueue(fileId);
        queue.setStatus("WAITING");
        queue.setWaitReason(waitReason);
        queue.setUpdatedAt(LocalDateTime.now());
        queue = fileReceptionQueueRepository.save(queue);
        queue = ensureFileRecord(queue);
        if (queue.getFileRecordId() != null && fileAssetService != null) {
            var transition = fileAssetService.markWaiting(queue.getFileRecordId(), waitReason);
            logFileProcess(
                    queue.getFileRecordId(),
                    "markAsWaiting",
                    "WAIT",
                    statusFrom(transition, "ARRIVED"),
                    statusTo(transition, "ARRIVED"),
                    "SKIPPED",
                    null,
                    null,
                    queue.getRetryCount(),
                    Map.of("waitReason", waitReason));
        }
    }

    public void markAsReady(Long fileId) {
        log.info("Marking file as ready: id={}", fileId);

        FileReceptionQueue queue = ensureFileRecord(loadQueue(fileId));
        if (queue.getFileRecordId() != null && fileAssetService != null) {
            var transition = fileAssetService.markReady(queue.getFileRecordId(), Map.of("integrityCheck", "passed"));
            logFileProcess(
                    queue.getFileRecordId(),
                    "markAsReady",
                    "VERIFY",
                    statusFrom(transition, "ARRIVED"),
                    statusTo(transition, "READY"),
                    "SUCCESS",
                    null,
                    null,
                    queue.getRetryCount(),
                    Map.of("verified", true));
        }
    }

    /**
     * 标记文件为处理中
     */
    public void markAsProcessing(Long fileId) {
        log.info("Marking file as processing: id={}", fileId);

        FileReceptionQueue queue = loadQueue(fileId);
        queue.setStatus("PROCESSING");
        queue.setUpdatedAt(LocalDateTime.now());
        queue = fileReceptionQueueRepository.save(queue);
        queue = ensureFileRecord(queue);
        if (queue.getFileRecordId() != null && fileAssetService != null) {
            var transition = fileAssetService.markProcessing(queue.getFileRecordId());
            logFileProcess(
                    queue.getFileRecordId(),
                    "markAsProcessing",
                    "PROCESS",
                    statusFrom(transition, "READY"),
                    statusTo(transition, "PROCESSING"),
                    "SUCCESS",
                    null,
                    null,
                    queue.getRetryCount(),
                    null);
        }
    }

    /**
     * 标记文件为已完成
     */
    public void markAsCompleted(Long fileId) {
        log.info("Marking file as completed: id={}", fileId);

        FileReceptionQueue queue = loadQueue(fileId);
        queue.setStatus("COMPLETED");
        queue.setUpdatedAt(LocalDateTime.now());
        queue = fileReceptionQueueRepository.save(queue);
        queue = ensureFileRecord(queue);
        if (queue.getFileRecordId() != null && fileAssetService != null) {
            var transition = fileAssetService.markProcessed(queue.getFileRecordId());
            logFileProcess(
                    queue.getFileRecordId(),
                    "markAsCompleted",
                    "PROCESS",
                    statusFrom(transition, "PROCESSING"),
                    statusTo(transition, "PROCESSED"),
                    "SUCCESS",
                    null,
                    null,
                    queue.getRetryCount(),
                    null);
        }
    }

    /**
     * 标记文件为失败并增加重试计数
     */
    public void markAsFailed(Long fileId, String errorMessage) {
        log.error("Marking file as failed: id={}, error={}", fileId, errorMessage);

        FileReceptionQueue queue = loadQueue(fileId);
        queue.setStatus("FAILED");
        queue.setErrorMessage(errorMessage);
        queue.setRetryCount(queue.getRetryCount() != null ? queue.getRetryCount() + 1 : 1);
        queue.setUpdatedAt(LocalDateTime.now());
        queue = fileReceptionQueueRepository.save(queue);
        queue = ensureFileRecord(queue);
        if (queue.getFileRecordId() != null && fileAssetService != null) {
            var transition = fileAssetService.markFailed(queue.getFileRecordId(), errorMessage);
            logFileProcess(
                    queue.getFileRecordId(),
                    "markAsFailed",
                    "PROCESS",
                    statusFrom(transition, "ARRIVED"),
                    statusTo(transition, "FAILED"),
                    "FAILED",
                    "FILE_RECEPTION_FAILED",
                    errorMessage,
                    queue.getRetryCount(),
                    null);
        }
    }

    public List<FileReceptionQueue> findPendingFiles() {
        return fileReceptionQueueRepository.findByStatusOrderByCreatedAtAsc("RECEIVED");
    }

    public List<FileReceptionQueue> findWaitingFiles() {
        return fileReceptionQueueRepository.findByStatus("WAITING");
    }

    public List<FileReceptionQueue> findOverdueFiles(int minutesTolerance) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesTolerance, ChronoUnit.MINUTES);
        return fileReceptionQueueRepository.findOverdueFiles(threshold);
    }

    public List<FileReceptionQueue> findRetriableFiles(int minutesInterval) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesInterval, ChronoUnit.MINUTES);
        return fileReceptionQueueRepository.findRetriableFiles(threshold);
    }

    public boolean verifyFileIntegrity(Long fileId) throws IOException, NoSuchAlgorithmException {
        FileReceptionQueue queue = loadQueue(fileId);

        File file = new File(queue.getFilePath());
        if (!file.exists()) {
            log.warn("File not found on disk: {}", queue.getFilePath());
            return false;
        }

        if (!queue.getFileSize().equals(file.length())) {
            log.warn("File size mismatch for: {}", queue.getFileName());
            return false;
        }

        String currentHash = calculateHash(queue.getFilePath());
        if (!currentHash.equals(queue.getFileHash())) {
            log.warn("File hash mismatch for: {}", queue.getFileName());
            return false;
        }

        FileReceptionGuardService.ValidationResult receptionValidation =
                fileReceptionGuardService.validateForProcessing(queue.getFileName(), queue.getFilePath());
        if (!receptionValidation.accepted()) {
            log.warn(
                    "File not ready for processing: fileName={}, reason={}",
                    queue.getFileName(),
                    receptionValidation.reason());
            return false;
        }

        return true;
    }

    private String calculateHash(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(filePath)))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        byte[] hashBytes = md.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public FileReceptionStats getStatistics() {
        long receivedCount = fileReceptionQueueRepository.countByStatus("RECEIVED");
        long waitingCount = fileReceptionQueueRepository.countByStatus("WAITING");
        long processingCount = fileReceptionQueueRepository.countByStatus("PROCESSING");
        long completedCount = fileReceptionQueueRepository.countByStatus("COMPLETED");
        long failedCount = fileReceptionQueueRepository.countByStatus("FAILED");

        return new FileReceptionStats(receivedCount, waitingCount, processingCount, completedCount, failedCount);
    }

    public static class FileReceptionStats {
        public long receivedCount;
        public long waitingCount;
        public long processingCount;
        public long completedCount;
        public long failedCount;

        public FileReceptionStats(
                long receivedCount, long waitingCount, long processingCount, long completedCount, long failedCount) {
            this.receivedCount = receivedCount;
            this.waitingCount = waitingCount;
            this.processingCount = processingCount;
            this.completedCount = completedCount;
            this.failedCount = failedCount;
        }
    }

    private FileReceptionQueue loadQueue(Long fileId) {
        return fileReceptionQueueRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
    }

    private FileReceptionQueue ensureFileRecord(FileReceptionQueue queue) {
        if (queue.getFileRecordId() != null || fileAssetService == null) {
            return queue;
        }
        FileAssetRecord fileRecord = registerInboundFileRecord(
                queue.getFileName(),
                queue.getFilePath(),
                queue.getSourceSystem(),
                queue.getFileSize(),
                queue.getFileHash(),
                queue.getId());
        if (fileRecord == null) {
            return queue;
        }
        queue.setFileRecordId(fileRecord.getId());
        queue.setUpdatedAt(LocalDateTime.now());
        return fileReceptionQueueRepository.save(queue);
    }

    private FileAssetRecord registerInboundFileRecord(
            String fileName, String filePath, String sourceSystem, Long fileSize, String fileHash) {
        return registerInboundFileRecord(fileName, filePath, sourceSystem, fileSize, fileHash, null);
    }

    private FileAssetRecord registerInboundFileRecord(
            String fileName, String filePath, String sourceSystem, Long fileSize, String fileHash, Long legacyQueueId) {
        if (fileAssetService == null) {
            return null;
        }
        return fileAssetService.registerInboundFile(
                fileName, filePath, sourceSystem, fileSize, fileHash, "ARRIVED", legacyMetadata(legacyQueueId));
    }

    private Map<String, Object> legacyMetadata(Long legacyQueueId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("legacyTable", "file_reception_queue");
        if (legacyQueueId != null) {
            metadata.put("legacyQueueId", legacyQueueId);
        }
        return metadata;
    }

    private void rejectDuplicateOrConflict(
            Optional<FileReceptionQueue> existingByName,
            String fileName,
            String sourceSystem,
            Long fileSize,
            String fileHash) {
        if (existingByName.isPresent()) {
            FileReceptionQueue queue = existingByName.get();
            boolean sameContent =
                    safeEquals(fileHash, queue.getFileHash()) && safeEquals(fileSize, queue.getFileSize());
            if (sameContent) {
                log.warn("Duplicate file name and content detected: {}", fileName);
                throw new IllegalArgumentException("File already exists: " + fileName);
            }
            log.warn("File name conflict detected with different content: {}", fileName);
            throw new IllegalArgumentException("File name conflict with different content: " + fileName);
        }

        if (fileAssetService == null) {
            return;
        }
        fileAssetService.findDuplicateInbound(sourceSystem, fileHash).ifPresent(existingRecord -> {
            log.warn(
                    "Duplicate inbound file content detected: fileName={}, fileNo={}",
                    fileName,
                    existingRecord.getFileNo());
            throw new IllegalArgumentException(
                    "Duplicate file content already received: " + existingRecord.getFileNo());
        });
    }

    private void logFileProcess(
            Long fileRecordId,
            String stepName,
            String actionType,
            String statusFrom,
            String statusTo,
            String result,
            String errorCode,
            String errorMessage,
            Integer retryNo,
            Map<String, Object> extra) {
        if (fileProcessLogService == null || fileRecordId == null) {
            return;
        }
        fileProcessLogService.log(
                fileRecordId,
                stepName,
                actionType,
                statusFrom,
                statusTo,
                result,
                null,
                "fileReceptionJob",
                retryNo,
                errorCode,
                errorMessage,
                extra);
    }

    private String statusFrom(FileAssetStateMachineService.TransitionResult transition, String fallback) {
        return transition == null ? fallback : transition.from().name();
    }

    private String statusTo(FileAssetStateMachineService.TransitionResult transition, String fallback) {
        return transition == null ? fallback : transition.to().name();
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
