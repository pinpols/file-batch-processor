package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.model.FileDistributionTask;

public interface FileDistributor {

    boolean supports(String targetSystem);

    void distribute(FileDistributionTask task);
}
