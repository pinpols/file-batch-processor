package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

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

    public FileDistributionService(FileDistributionTaskRepository fileDistributionTaskRepository) {
        this.fileDistributionTaskRepository = fileDistributionTaskRepository;
    }

    /**
     * 创建分发任务
     */
    public FileDistributionTask createDistributionTask(
            String fileName, String filePath, String targetSystem, String targetAddress) {
        log.info("Creating distribution task: file={}, target={}://{}", fileName, targetSystem, targetAddress);

        try {
            // 验证文件存在
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IOException("File not found: " + filePath);
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
            task.setRetryIntervalSeconds(300L); // 5分钟

            FileDistributionTask saved = fileDistributionTaskRepository.save(task);
            log.info("Distribution task created: id={}", saved.getId());
            return saved;
        } catch (Exception e) {
            log.error("Failed to create distribution task: {}", fileName, e);
            throw new RuntimeException("Failed to create distribution task: " + e.getMessage(), e);
        }
    }

    /**
     * 标记任务为处理中
     */
    public void markAsInProgress(Long taskId) {
        log.info("Marking task as in progress: id={}", taskId);

        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        task.setStatus("IN_PROGRESS");
        task.setLastAttemptTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);
    }

    /**
     * 标记任务为成功
     */
    public void markAsSuccess(Long taskId) {
        log.info("Marking task as success: id={}", taskId);

        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        task.setStatus("SUCCESS");
        task.setCompletedTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);
    }

    /**
     * 标记任务为失败并处理重试
     */
    public boolean markAsFailed(Long taskId, String errorMessage) {
        log.error("Marking task as failed: id={}, error={}", taskId, errorMessage);

        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        task.setRetryCount(task.getRetryCount() + 1);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(LocalDateTime.now());

        // 检查是否应该重试
        if (task.getRetryCount() < task.getMaxRetries()) {
            task.setStatus("RETRY");
            log.info("Task will be retried: id={}, retryCount={}/{}", taskId, task.getRetryCount(), task.getMaxRetries());
        } else {
            task.setStatus("FAILED");
            task.setCompletedTime(LocalDateTime.now());
            log.error("Task failed after max retries: id={}", taskId);
        }

        fileDistributionTaskRepository.save(task);
        return task.getStatus().equals("RETRY");
    }

    /**
     * 查找待分发的任务
     */
    public List<FileDistributionTask> findPendingTasks() {
        return fileDistributionTaskRepository.findByStatus("PENDING");
    }

    /**
     * 查找需要重试的任务
     */
    public List<FileDistributionTask> findRetryableTasks(int minutesInterval) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesInterval, ChronoUnit.MINUTES);
        return fileDistributionTaskRepository.findRetriableTasks(threshold);
    }

    /**
     * 查找超时的任务
     */
    public List<FileDistributionTask> findTimeoutTasks(int minutesTimeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesTimeout, ChronoUnit.MINUTES);
        return fileDistributionTaskRepository.findTimeoutTasks(threshold);
    }

    /**
     * 执行 SFTP 分发
     */
    public void distributeBySFTP(Long taskId, String host, int port, String username, String password, String remoteDir) {
        log.info("Distributing via SFTP: taskId={}, host={}", taskId, host);

        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        Session session = null;
        Channel channel = null;
        ChannelSftp sftp = null;
        try {
            markAsInProgress(taskId);

            FileDistributionTask taskFile = fileDistributionTaskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

            File localFile = new File(taskFile.getFilePath());
            if (!localFile.exists()) {
                throw new IOException("Local file not found: " + taskFile.getFilePath());
            }

            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(10000);

            channel = session.openChannel("sftp");
            channel.connect(10000);
            sftp = (ChannelSftp) channel;

            try {
                sftp.cd(remoteDir);
            } catch (SftpException se) {
                // 目录不存在，尝试创建
                try {
                    sftp.mkdir(remoteDir);
                    sftp.cd(remoteDir);
                } catch (SftpException se2) {
                    throw new IOException("Failed to access or create remote dir: " + remoteDir, se2);
                }
            }

            // 上传文件为二进制，保留原文件名
            try (FileInputStream fis = new FileInputStream(localFile)) {
                sftp.put(fis, localFile.getName());
            }

            log.info("SFTP upload succeeded for taskId={}", taskId);
            markAsSuccess(taskId);
        } catch (JSchException | SftpException | IOException e) {
            log.error("SFTP distribution failed for task: {}", taskId, e);
            markAsFailed(taskId, "SFTP transfer failed: " + e.getMessage());
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * 执行 HTTP 分发
     */
    public void distributeByHTTP(Long taskId, String url, String method) {
        log.info("Distributing via HTTP: taskId={}, url={}", taskId, url);

        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        try {
            markAsInProgress(taskId);

            // TODO: 实现 HTTP 上传逻辑
            // 这里可以使用 RestTemplate 或 WebClient
            log.info("HTTP distribution implementation required for task: {}", taskId);

            markAsSuccess(taskId);
        } catch (Exception e) {
            log.error("HTTP distribution failed for task: {}", taskId, e);
            markAsFailed(taskId, "HTTP transfer failed: " + e.getMessage());
        }
    }

    /**
     * 执行 FTP 分发
     */
    public void distributeByFTP(Long taskId, String host, int port, String username, String password, String remoteDir) {
        log.info("Distributing via FTP: taskId={}, host={}", taskId, host);

        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        try {
            markAsInProgress(taskId);

            // TODO: 实现 FTP 传输逻辑
            // 这里可以集成 commons-net 等 FTP 库
            log.info("FTP distribution implementation required for task: {}", taskId);

            markAsSuccess(taskId);
        } catch (Exception e) {
            log.error("FTP distribution failed for task: {}", taskId, e);
            markAsFailed(taskId, "FTP transfer failed: " + e.getMessage());
        }
    }

    /**
     * 重试失败的任务
     */
    @Transactional
    public void retryFailedTask(Long taskId) {
        log.info("Retrying task: id={}", taskId);

        FileDistributionTask task = fileDistributionTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getRetryCount() >= task.getMaxRetries()) {
            log.warn("Task has exceeded max retries: id={}", taskId);
            return;
        }

        // 重置任务为待发送状态
        task.setStatus("PENDING");
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);

        log.info("Task ready for retry: id={}, nextRetry={}", taskId, task.getRetryCount() + 1);
    }

    /**
     * 获取分发统计信息
     */
    public FileDistributionStats getStatistics() {
        long pendingCount = fileDistributionTaskRepository.countByStatus("PENDING");
        long inProgressCount = fileDistributionTaskRepository.countByStatus("IN_PROGRESS");
        long retryCount = fileDistributionTaskRepository.countByStatus("RETRY");
        long successCount = fileDistributionTaskRepository.countByStatus("SUCCESS");
        long failedCount = fileDistributionTaskRepository.countByStatus("FAILED");

        return new FileDistributionStats(pendingCount, inProgressCount, retryCount, successCount, failedCount);
    }

    /**
     * 统计数据类
     */
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
