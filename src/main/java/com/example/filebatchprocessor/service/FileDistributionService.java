package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.exception.SystemException;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * 文件分发服务：
 * 1. 创建分发任务
 * 2. 管理重传机制
 * 3. 支持多种传输协议（SFTP、HTTP、FTP 等）
 */
@Slf4j
@Service
@Transactional
public class FileDistributionService {

    private final FileDistributionTaskRepository fileDistributionTaskRepository;
    private final RecordTraceRepository recordTraceRepository;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public FileDistributionService(FileDistributionTaskRepository fileDistributionTaskRepository,
                                   RecordTraceRepository recordTraceRepository) {
        this.fileDistributionTaskRepository = fileDistributionTaskRepository;
        this.recordTraceRepository = recordTraceRepository;
    }

    /**
     * 创建分发任务
     */
    public FileDistributionTask createDistributionTask(
            String fileName, String filePath, String targetSystem, String targetAddress) {
        log.info("Creating distribution task: file={}, target={}://{}", fileName, targetSystem, targetAddress);

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + filePath);
            }

            FileDistributionTask task = new FileDistributionTask();
            task.setFileName(fileName);
            task.setFilePath(filePath);
            task.setFileSize(file.length());
            task.setTargetSystem(targetSystem);
            task.setTargetAddress(targetAddress);
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setMaxRetries(3);
            task.setRetryIntervalSeconds(300L);

            FileDistributionTask saved = fileDistributionTaskRepository.save(task);
            log.info("Distribution task created: id={}", saved.getId());
            return saved;
        } catch (BusinessException e) {
            log.warn("Failed to create distribution task (business): {}", fileName, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create distribution task: {}", fileName, e);
            throw new SystemException(ErrorCode.INTERNAL_ERROR, "Failed to create distribution task: " + e.getMessage(), e);
        }
    }

    public void markAsInProgress(Long taskId) {
        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + taskId));

        task.setStatus("IN_PROGRESS");
        task.setLastAttemptTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);

        persistDistributionTrace(task, "DISTRIBUTE", "IN_PROGRESS", null);
    }

    public void markAsSuccess(Long taskId) {
        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + taskId));

        task.setStatus("SUCCESS");
        task.setCompletedTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);

        persistDistributionTrace(task, "DISTRIBUTE", "SUCCESS", null);
    }

    public boolean markAsFailed(Long taskId, String errorMessage) {
        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + taskId));

        task.setRetryCount(task.getRetryCount() + 1);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(LocalDateTime.now());

        if (task.getRetryCount() < task.getMaxRetries()) {
            task.setStatus("RETRY");
            log.info("Task will be retried: id={}, retryCount={}/{}", taskId, task.getRetryCount(), task.getMaxRetries());
        } else {
            task.setStatus("FAILED");
            task.setCompletedTime(LocalDateTime.now());
            log.error("Task failed after max retries: id={}", taskId);
        }

        fileDistributionTaskRepository.save(task);

        persistDistributionTrace(task, "DISTRIBUTE", task.getStatus(), errorMessage);
        return "RETRY".equals(task.getStatus());
    }

    private void persistDistributionTrace(FileDistributionTask task, String eventType, String status, String message) {
        try {
            RecordTrace trace = new RecordTrace();
            trace.setBusinessKey("FILE:" + task.getId());
            trace.setBatchDate(null);
            trace.setJobName("fileDistribution");
            trace.setSourceFileName(task.getFilePath());
            trace.setEventType(eventType);
            trace.setStatus(status);
            trace.setMessage(message);
            trace.setTargetSystem(task.getTargetSystem());
            trace.setTargetAddress(task.getTargetAddress());
            recordTraceRepository.save(trace);
        } catch (Exception ex) {
            log.error("Failed to persist distribution trace: taskId={}", task.getId(), ex);
        }
    }

    public List<FileDistributionTask> findPendingTasks() {
        return fileDistributionTaskRepository.findByStatus("PENDING");
    }

    public List<FileDistributionTask> findRetryableTasks(int minutesInterval) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesInterval, ChronoUnit.MINUTES);
        return fileDistributionTaskRepository.findRetriableTasks(threshold);
    }

    public List<FileDistributionTask> findTimeoutTasks(int minutesTimeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesTimeout, ChronoUnit.MINUTES);
        return fileDistributionTaskRepository.findTimeoutTasks(threshold);
    }

    public void distributeBySFTP(Long taskId, String host, int port, String username, String password, String remoteDir) {
        log.info("Distributing via SFTP: taskId={}, host={}", taskId, host);

        SSHClient sshClient = new SSHClient();
        try {
            markAsInProgress(taskId);

            FileDistributionTask taskFile = fileDistributionTaskRepository.findById(taskId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + taskId));

            File localFile = new File(taskFile.getFilePath());
            if (!localFile.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Local file not found: " + taskFile.getFilePath());
            }

            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
            sshClient.connect(host, port);
            sshClient.authPassword(username, password);
            try (SFTPClient sftpClient = sshClient.newSFTPClient()) {
                try {
                    sftpClient.statExistence(remoteDir);
                    if (sftpClient.statExistence(remoteDir) == null) {
                        sftpClient.mkdirs(remoteDir);
                    }
                } catch (IOException e) {
                    throw new IOException("Failed to access or create remote dir: " + remoteDir, e);
                }
                String remotePath = remoteDir.endsWith("/") ? remoteDir + localFile.getName() : remoteDir + "/" + localFile.getName();
                sftpClient.put(new FileSystemFile(localFile), remotePath);
            }

            markAsSuccess(taskId);
        } catch (BusinessException e) {
            log.warn("SFTP distribution failed (business) for task: {}", taskId, e);
            markAsFailed(taskId, "SFTP transfer failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("SFTP distribution failed for task: {}", taskId, e);
            markAsFailed(taskId, "SFTP transfer failed: " + e.getMessage());
        } finally {
            if (sshClient.isConnected()) {
                try {
                    sshClient.disconnect();
                } catch (IOException e) {
                    log.warn("Failed to disconnect SSH client for task {}", taskId, e);
                }
            }
        }
    }

    public void distributeByHTTP(Long taskId, String url, String method) {
        log.info("Distributing via HTTP: taskId={}, url={}", taskId, url);

        try {
            markAsInProgress(taskId);
            FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + taskId));

            File localFile = new File(task.getFilePath());
            if (!localFile.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Local file not found: " + task.getFilePath());
            }
            if (url == null || url.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "HTTP target URL is required");
            }

            String normalizedMethod = (method == null || method.isBlank())
                    ? "POST" : method.toUpperCase(Locale.ROOT);
            if (!"POST".equals(normalizedMethod) && !"PUT".equals(normalizedMethod)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Unsupported HTTP method: " + normalizedMethod);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/octet-stream")
                    .header("X-File-Name", task.getFileName())
                    .method(normalizedMethod, HttpRequest.BodyPublishers.ofFile(localFile.toPath()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                markAsSuccess(taskId);
            } else {
                markAsFailed(taskId, "HTTP transfer failed with status " + response.statusCode());
            }
        } catch (BusinessException e) {
            log.warn("HTTP distribution failed (business) for task: {}", taskId, e);
            markAsFailed(taskId, "HTTP transfer failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("HTTP distribution failed for task: {}", taskId, e);
            markAsFailed(taskId, "HTTP transfer failed: " + e.getMessage());
        }
    }

    public void distributeByFTP(Long taskId, String host, int port, String username, String password, String remoteDir) {
        log.info("Distributing via FTP: taskId={}, host={}", taskId, host);
        markAsFailed(taskId, "FTP distribution is not implemented yet");
    }

    @Transactional
    public void retryFailedTask(Long taskId) {
        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getRetryCount() >= task.getMaxRetries()) {
            log.warn("Task has exceeded max retries: id={}", taskId);
            return;
        }

        task.setStatus("PENDING");
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);

        log.info("Task ready for retry: id={}, nextRetry={}", taskId, task.getRetryCount() + 1);
    }

    public FileDistributionStats getStatistics() {
        long pendingCount = fileDistributionTaskRepository.countByStatus("PENDING");
        long inProgressCount = fileDistributionTaskRepository.countByStatus("IN_PROGRESS");
        long retryCount = fileDistributionTaskRepository.countByStatus("RETRY");
        long successCount = fileDistributionTaskRepository.countByStatus("SUCCESS");
        long failedCount = fileDistributionTaskRepository.countByStatus("FAILED");

        return new FileDistributionStats(pendingCount, inProgressCount, retryCount, successCount, failedCount);
    }

    public static class FileDistributionStats {
        public long pendingCount;
        public long inProgressCount;
        public long retryCount;
        public long successCount;
        public long failedCount;

        public FileDistributionStats(long pendingCount, long inProgressCount, long retryCount,
                                     long successCount, long failedCount) {
            this.pendingCount = pendingCount;
            this.inProgressCount = inProgressCount;
            this.retryCount = retryCount;
            this.successCount = successCount;
            this.failedCount = failedCount;
        }
    }
}
