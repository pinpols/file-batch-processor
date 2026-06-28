package com.example.filebatchprocessor.service.distribution;

import cn.hutool.extra.ftp.Ftp;
import cn.hutool.extra.ftp.FtpConfig;
import cn.hutool.extra.ftp.FtpMode;
import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.exception.SystemException;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FtpFileDistributor implements FileDistributor {

    private final FileDistributionService fileDistributionService;
    private final DistributionTargetValidator targetValidator;
    private final long connectTimeoutMs;
    private final long socketTimeoutMs;

    @org.springframework.beans.factory.annotation.Autowired
    public FtpFileDistributor(
            FileDistributionService fileDistributionService,
            DistributionTargetValidator targetValidator,
            @Value("${distribution.ftp.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${distribution.ftp.socket-timeout-ms:30000}") long socketTimeoutMs) {
        this.fileDistributionService = fileDistributionService;
        this.targetValidator = targetValidator;
        this.connectTimeoutMs = Math.max(1000L, connectTimeoutMs);
        this.socketTimeoutMs = Math.max(1000L, socketTimeoutMs);
    }

    public FtpFileDistributor(
            FileDistributionService fileDistributionService, DistributionTargetValidator targetValidator) {
        this(fileDistributionService, targetValidator, 5000L, 30000L);
    }

    @Override
    public boolean supports(String targetSystem) {
        return "FTP".equalsIgnoreCase(targetSystem);
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
        String targetAddress = task.getTargetAddress();
        if (targetAddress == null || targetAddress.isBlank()) {
            fileDistributionService.markAsFailed(task.getId(), "FTP target address is required", jobInstanceId);
            return;
        }

        String normalized = targetAddress.startsWith("ftp://") ? targetAddress : "ftp://" + targetAddress;

        String host;
        int port;
        String remoteDir;
        String username;
        String password;
        try {
            // SSRF 防护:校验目标地址(白名单 + 拦内网/环回/元数据)+ 解析连接参数
            targetValidator.validate(normalized);
            URI uri = URI.create(normalized);
            host = uri.getHost();
            port = uri.getPort() > 0 ? uri.getPort() : 21;
            remoteDir = (uri.getPath() == null || uri.getPath().isBlank()) ? "/" : uri.getPath();

            username = "anonymous";
            password = "anonymous";
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }
        } catch (Exception ex) {
            fileDistributionService.markAsFailed(
                    task.getId(),
                    "Invalid FTP target address: " + targetAddress + ", " + ex.getMessage(),
                    jobInstanceId);
            return;
        }

        // 自包含传输:直接用 Hutool Ftp 上传,只回调状态机(与 SFTP/HTTP 范式一致)
        Ftp ftp = null;
        try {
            fileDistributionService.markAsInProgress(task.getId(), jobInstanceId);
            if (task.getFilePath() == null || task.getFilePath().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Local file path is required");
            }
            File localFile = new File(task.getFilePath());
            if (!localFile.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Local file not found: " + task.getFilePath());
            }
            String targetDir = (remoteDir == null || remoteDir.isBlank()) ? "/" : remoteDir;
            FtpConfig config = FtpConfig.create()
                    .setHost(host)
                    .setPort(port)
                    .setUser(username)
                    .setPassword(password)
                    .setConnectionTimeout(connectTimeoutMs)
                    .setSoTimeout(socketTimeoutMs);
            ftp = new Ftp(config, FtpMode.Active);
            boolean uploaded = ftp.upload(targetDir, localFile);
            if (!uploaded) {
                throw new SystemException(ErrorCode.INTERNAL_ERROR, "FTP upload returned false");
            }
            fileDistributionService.markAsSuccess(task.getId(), jobInstanceId, false, null, null);
        } catch (Exception e) {
            log.error("FTP distribution failed for taskId={}", task.getId(), e);
            fileDistributionService.markAsFailed(task.getId(), "FTP transfer failed: " + e.getMessage(), jobInstanceId);
        } finally {
            if (ftp != null) {
                try {
                    ftp.close();
                } catch (IOException e) {
                    log.warn("Failed to close FTP client for taskId={}", task.getId(), e);
                }
            }
        }
    }
}
