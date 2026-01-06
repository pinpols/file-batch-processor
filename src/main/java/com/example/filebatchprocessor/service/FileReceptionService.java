package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

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

    public FileReceptionService(FileReceptionQueueRepository fileReceptionQueueRepository) {
        this.fileReceptionQueueRepository = fileReceptionQueueRepository;
    }

    /**
     * 接收文件：将文件注册到接收队列
     */
    public FileReceptionQueue receiveFile(String fileName, String filePath, String sourceSystem) {
        log.info("Receiving file: {} from {}", fileName, sourceSystem);

        // 检查重复
        Optional<FileReceptionQueue> existing = fileReceptionQueueRepository.findByFileName(fileName);
        if (existing.isPresent()) {
            log.warn("File already exists in reception queue: {}", fileName);
            throw new IllegalArgumentException("File already exists: " + fileName);
        }

        try {
            // 验证文件存在
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IOException("File not found: " + filePath);
            }

            FileReceptionQueue queue = new FileReceptionQueue();
            queue.setFileName(fileName);
            queue.setFilePath(filePath);
            queue.setFileSize(file.length());
            queue.setFileHash(calculateHash(filePath));
            queue.setSourceSystem(sourceSystem);
            queue.setStatus("RECEIVED");
            queue.setCreatedAt(LocalDateTime.now());

            FileReceptionQueue saved = fileReceptionQueueRepository.save(queue);
            log.info("File received successfully: id={}, fileName={}", saved.getId(), fileName);
            return saved;
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

        FileReceptionQueue queue = fileReceptionQueueRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        queue.setStatus("WAITING");
        queue.setWaitReason(waitReason);
        queue.setUpdatedAt(LocalDateTime.now());
        fileReceptionQueueRepository.save(queue);
    }

    /**
     * 标记文件为处理中
     */
    public void markAsProcessing(Long fileId) {
        log.info("Marking file as processing: id={}", fileId);

        FileReceptionQueue queue = fileReceptionQueueRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        queue.setStatus("PROCESSING");
        queue.setUpdatedAt(LocalDateTime.now());
        fileReceptionQueueRepository.save(queue);
    }

    /**
     * 标记文件为已完成
     */
    public void markAsCompleted(Long fileId) {
        log.info("Marking file as completed: id={}", fileId);

        FileReceptionQueue queue = fileReceptionQueueRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        queue.setStatus("COMPLETED");
        queue.setUpdatedAt(LocalDateTime.now());
        fileReceptionQueueRepository.save(queue);
    }

    /**
     * 标记文件为失败并增加重试计数
     */
    public void markAsFailed(Long fileId, String errorMessage) {
        log.error("Marking file as failed: id={}, error={}", fileId, errorMessage);

        FileReceptionQueue queue = fileReceptionQueueRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        queue.setStatus("FAILED");
        queue.setErrorMessage(errorMessage);
        queue.setRetryCount(queue.getRetryCount() != null ? queue.getRetryCount() + 1 : 1);
        queue.setUpdatedAt(LocalDateTime.now());
        fileReceptionQueueRepository.save(queue);
    }

    /**
     * 查找待处理文件（已接收且未处理的文件）
     */
    public List<FileReceptionQueue> findPendingFiles() {
        return fileReceptionQueueRepository.findByStatusOrderByCreatedAtAsc("RECEIVED");
    }

    /**
     * 查找等待中的文件
     */
    public List<FileReceptionQueue> findWaitingFiles() {
        return fileReceptionQueueRepository.findByStatus("WAITING");
    }

    /**
     * 查找超时的文件
     */
    public List<FileReceptionQueue> findOverdueFiles(int minutesTolerance) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesTolerance, ChronoUnit.MINUTES);
        return fileReceptionQueueRepository.findOverdueFiles(threshold);
    }

    /**
     * 查找需要重试的文件
     */
    public List<FileReceptionQueue> findRetriableFiles(int minutesInterval) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesInterval, ChronoUnit.MINUTES);
        return fileReceptionQueueRepository.findRetriableFiles(threshold);
    }

    /**
     * 检查文件完整性（文件大小和哈希值）
     */
    public boolean verifyFileIntegrity(Long fileId) throws IOException, NoSuchAlgorithmException {
        FileReceptionQueue queue = fileReceptionQueueRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        File file = new File(queue.getFilePath());
        if (!file.exists()) {
            log.warn("File not found on disk: {}", queue.getFilePath());
            return false;
        }

        // 检查文件大小
        if (!queue.getFileSize().equals(file.length())) {
            log.warn("File size mismatch for: {}", queue.getFileName());
            return false;
        }

        // 检查文件哈希
        String currentHash = calculateHash(queue.getFilePath());
        if (!currentHash.equals(queue.getFileHash())) {
            log.warn("File hash mismatch for: {}", queue.getFileName());
            return false;
        }

        return true;
    }

    /**
     * 计算文件哈希值（MD5）
     */
    private String calculateHash(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        byte[] hashBytes = md.digest(fileBytes);

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 获取文件接收统计信息
     */
    public FileReceptionStats getStatistics() {
        long receivedCount = fileReceptionQueueRepository.countByStatus("RECEIVED");
        long waitingCount = fileReceptionQueueRepository.countByStatus("WAITING");
        long processingCount = fileReceptionQueueRepository.countByStatus("PROCESSING");
        long completedCount = fileReceptionQueueRepository.countByStatus("COMPLETED");
        long failedCount = fileReceptionQueueRepository.countByStatus("FAILED");

        return new FileReceptionStats(receivedCount, waitingCount, processingCount, completedCount, failedCount);
    }

    /**
     * 统计数据类
     */
    public static class FileReceptionStats {
        public long receivedCount;
        public long waitingCount;
        public long processingCount;
        public long completedCount;
        public long failedCount;

        public FileReceptionStats(long receivedCount, long waitingCount, long processingCount, 
                                 long completedCount, long failedCount) {
            this.receivedCount = receivedCount;
            this.waitingCount = waitingCount;
            this.processingCount = processingCount;
            this.completedCount = completedCount;
            this.failedCount = failedCount;
        }
    }
}
