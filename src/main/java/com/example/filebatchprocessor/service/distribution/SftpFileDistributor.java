package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SftpFileDistributor implements FileDistributor {

    private final FileDistributionService fileDistributionService;
    private final SftpConcurrencyLimiter limiter;

    @Value("${sftp.host:}")
    private String sftpHost;

    @Value("${sftp.port:22}")
    private int sftpPort;

    @Value("${sftp.username:}")
    private String sftpUsername;

    @Value("${sftp.password:}")
    private String sftpPassword;

    @Value("${sftp.remoteDir:/upload}")
    private String sftpRemoteDir;

    @Value("${sftp.known-hosts-path:}")
    private String sftpKnownHostsPath;

    @Value("${sftp.insecure-skip-host-key-check:false}")
    private boolean sftpInsecureSkipHostKeyCheck;

    @Value("${sftp.connect-timeout-ms:5000}")
    private int sftpConnectTimeoutMs;

    @Value("${sftp.socket-timeout-ms:30000}")
    private int sftpSocketTimeoutMs;

    public SftpFileDistributor(FileDistributionService fileDistributionService, SftpConcurrencyLimiter limiter) {
        this.fileDistributionService = fileDistributionService;
        this.limiter = limiter;
    }

    @Override
    public boolean supports(String targetSystem) {
        return "SFTP".equalsIgnoreCase(targetSystem);
    }

    @Override
    public void distribute(FileDistributionTask task) {
        distribute(task, null);
    }

    @Override
    public void distribute(FileDistributionTask task, Long jobInstanceId) {
        if (task == null) {
            return;
        }
        if (sftpHost == null
                || sftpHost.isBlank()
                || sftpUsername == null
                || sftpUsername.isBlank()
                || sftpPassword == null
                || sftpPassword.isBlank()) {
            fileDistributionService.markAsFailed(
                    task.getId(), "SFTP config missing: sftp.host/username/password", jobInstanceId);
            return;
        }

        Semaphore semaphore = limiter.semaphoreForHost(sftpHost);
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            fileDistributionService.markAsFailed(
                    task.getId(),
                    "SFTP throttled: too many concurrent connections for host=" + sftpHost,
                    jobInstanceId);
            return;
        }

        try (SSHClient sshClient = new SSHClient()) {
            fileDistributionService.markAsInProgress(task.getId(), jobInstanceId);

            File localFile = new File(task.getFilePath());
            if (!localFile.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Local file not found: " + task.getFilePath());
            }

            SftpHostKeyVerification.apply(sshClient, sftpKnownHostsPath, sftpInsecureSkipHostKeyCheck);
            sshClient.setConnectTimeout(Math.max(1000, sftpConnectTimeoutMs));
            sshClient.setTimeout(Math.max(1000, sftpSocketTimeoutMs));
            sshClient.connect(sftpHost, sftpPort);
            sshClient.authPassword(sftpUsername, sftpPassword);
            try (SFTPClient sftpClient = sshClient.newSFTPClient()) {
                try {
                    sftpClient.statExistence(sftpRemoteDir);
                    if (sftpClient.statExistence(sftpRemoteDir) == null) {
                        sftpClient.mkdirs(sftpRemoteDir);
                    }
                } catch (IOException e) {
                    throw new IOException("Failed to access or create remote dir: " + sftpRemoteDir, e);
                }
                String remotePath = sftpRemoteDir.endsWith("/")
                        ? sftpRemoteDir + localFile.getName()
                        : sftpRemoteDir + "/" + localFile.getName();
                sftpClient.put(new FileSystemFile(localFile), remotePath);
            }

            fileDistributionService.markAsSuccess(task.getId(), jobInstanceId, false, null, null);
        } catch (Exception e) {
            log.error("SFTP distribution failed for taskId={}", task.getId(), e);
            fileDistributionService.markAsFailed(
                    task.getId(), "SFTP transfer failed: " + e.getMessage(), jobInstanceId);
        } finally {
            semaphore.release();
        }
    }
}
