package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDistributionService;
import org.springframework.stereotype.Component;

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
        if (task == null) {
            return;
        }
        fileDistributionService.markAsFailed(task.getId(), "FTP distribution is not implemented yet");
    }
}
