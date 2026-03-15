package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.example.filebatchprocessor.repository.CompensationRecordRepository;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CompensationRecordRepositoryIT {

    @Autowired
    private CompensationRecordRepository repository;
    
    @Autowired
    private BusinessJobInstanceRepository jobInstanceRepository;
    
    @Autowired
    private FileDistributionTaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveAndFindById() {
        CompensationRecord record = new CompensationRecord();
        record.setCompensationNo("COMP-001");
        record.setActionType("FILE_RETRY");
        record.setStatus("REQUESTED");
        
        CompensationRecord saved = repository.save(record);
        
        Optional<CompensationRecord> result = repository.findById(saved.getId());
        
        assertTrue(result.isPresent());
        assertEquals("FILE_RETRY", result.get().getActionType());
    }

    @Test
    void shouldFindByTargetJobInstanceId() {
        String uniqueNo = "JI-TEST-" + System.currentTimeMillis();
        BusinessJobInstance job = new BusinessJobInstance();
        job.setJobInstanceNo(uniqueNo);
        job.setTaskId("test-task");
        job.setJobName("testJob");
        job.setTriggerSource("MANUAL");
        job.setStatus("COMPLETED");
        BusinessJobInstance savedJob = jobInstanceRepository.save(job);
        
        CompensationRecord record = new CompensationRecord();
        record.setCompensationNo("COMP-" + System.currentTimeMillis());
        record.setActionType("FILE_RETRY");
        record.setStatus("REQUESTED");
        record.setTargetJobInstanceId(savedJob.getId());
        repository.save(record);
        
        List<CompensationRecord> result = repository.findByTargetJobInstanceIdOrderByCreatedAtDesc(savedJob.getId());
        
        assertFalse(result.isEmpty());
    }

    @Test
    void shouldFindByLegacyDistributionTaskId() {
        com.example.filebatchprocessor.model.FileDistributionTask task = new com.example.filebatchprocessor.model.FileDistributionTask();
        task.setFileName("test.csv");
        task.setFilePath("/tmp/test.csv");
        task.setTargetSystem("SFTP");
        task.setStatus("PENDING");
        com.example.filebatchprocessor.model.FileDistributionTask savedTask = taskRepository.save(task);
        
        CompensationRecord record = new CompensationRecord();
        record.setCompensationNo("COMP-003");
        record.setActionType("FILE_RETRY");
        record.setStatus("REQUESTED");
        record.setLegacyDistributionTaskId(savedTask.getId());
        repository.save(record);
        
        List<CompensationRecord> result = repository.findByLegacyDistributionTaskIdOrderByCreatedAtDesc(savedTask.getId());
        
        assertFalse(result.isEmpty());
    }
}
