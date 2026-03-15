package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class FtpFileDistributor implements FileDistributor {

    private final FileDistributionService fileDistributionService;

    public FtpFileDistributor(FileDistributionService fileDistributionService) {
        this.fileDistributionService = fileDistributionService;
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
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 21;
            String remoteDir = (uri.getPath() == null || uri.getPath().isBlank()) ? "/" : uri.getPath();

            String username = "anonymous";
            String password = "anonymous";
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }

            fileDistributionService.distributeByFTP(task.getId(), host, port, username, password, remoteDir, jobInstanceId);
        } catch (Exception ex) {
            fileDistributionService.markAsFailed(task.getId(), "Invalid FTP target address: " + targetAddress + ", " + ex.getMessage(), jobInstanceId);
        }
    }
}
