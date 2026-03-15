package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.model.FileDistributionTask;
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
class FileDistributionTaskRepositoryIT {

    @Autowired
    private FileDistributionTaskRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveAndFindById() {
        FileDistributionTask task = new FileDistributionTask();
        task.setFileName("test-file.csv");
        task.setFilePath("/tmp/test-file.csv");
        task.setTargetSystem("TARGET_A");
        task.setStatus("PENDING");
        
        FileDistributionTask saved = repository.save(task);
        
        Optional<FileDistributionTask> result = repository.findById(saved.getId());
        
        assertTrue(result.isPresent());
        assertEquals("test-file.csv", result.get().getFileName());
    }

    @Test
    void shouldFindByTargetSystem() {
        FileDistributionTask task = new FileDistributionTask();
        task.setFileName("test-file.csv");
        task.setFilePath("/tmp/test-file.csv");
        task.setTargetSystem("TARGET_A");
        task.setStatus("PENDING");
        repository.save(task);
        
        List<FileDistributionTask> result = repository.findByTargetSystem("TARGET_A");
        
        assertFalse(result.isEmpty());
        assertEquals("TARGET_A", result.get(0).getTargetSystem());
    }

    @Test
    void shouldFindByStatus() {
        FileDistributionTask task = new FileDistributionTask();
        task.setFileName("test-file.csv");
        task.setFilePath("/tmp/test-file.csv");
        task.setTargetSystem("SFTP");
        task.setStatus("PENDING");
        repository.save(task);
        
        List<FileDistributionTask> result = repository.findByStatus("PENDING");
        
        assertFalse(result.isEmpty());
        assertEquals("PENDING", result.get(0).getStatus());
    }
}
