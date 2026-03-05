package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

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

    public SftpFileDistributor(FileDistributionService fileDistributionService,
                              SftpConcurrencyLimiter limiter) {
        this.fileDistributionService = fileDistributionService;
        this.limiter = limiter;
    }

    @Override
    public boolean supports(String targetSystem) {
        return "SFTP".equalsIgnoreCase(targetSystem);
    }

    @Override
    public void distribute(FileDistributionTask task) {
        if (task == null) {
            return;
        }
        if (sftpHost == null || sftpHost.isBlank() || sftpUsername == null || sftpUsername.isBlank() || sftpPassword == null || sftpPassword.isBlank()) {
            fileDistributionService.markAsFailed(task.getId(), "SFTP config missing: sftp.host/username/password");
            return;
        }

        Semaphore semaphore = limiter.semaphoreForHost(sftpHost);
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            fileDistributionService.markAsFailed(task.getId(), "SFTP throttled: too many concurrent connections for host=" + sftpHost);
            return;
        }

        try (SSHClient sshClient = new SSHClient()) {
            fileDistributionService.markAsInProgress(task.getId());

            File localFile = new File(task.getFilePath());
            if (!localFile.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Local file not found: " + task.getFilePath());
            }

            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
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

            fileDistributionService.markAsSuccess(task.getId());
        } catch (Exception e) {
            log.error("SFTP distribution failed for taskId={}", task.getId(), e);
            fileDistributionService.markAsFailed(task.getId(), "SFTP transfer failed: " + e.getMessage());
        } finally {
            semaphore.release();
        }
    }
}
