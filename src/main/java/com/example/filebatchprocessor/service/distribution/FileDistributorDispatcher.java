package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.model.FileDistributionTask;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FileDistributorDispatcher {

    private final List<FileDistributor> distributors;

    public FileDistributorDispatcher(List<FileDistributor> distributors) {
        this.distributors = distributors;
    }

    public void distribute(FileDistributionTask task) {
        distribute(task, null);
    }

    public void distribute(FileDistributionTask task, Long jobInstanceId) {
        if (task == null) {
            return;
        }
        String targetSystem = task.getTargetSystem();
        FileDistributor distributor = distributors.stream()
                .filter(d -> d.supports(targetSystem))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_ARGUMENT, "Unknown distribution target: " + targetSystem));
        distributor.distribute(task, jobInstanceId);
    }
}
