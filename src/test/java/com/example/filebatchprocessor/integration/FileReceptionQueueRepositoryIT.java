package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FileReceptionQueueRepositoryIT extends PostgresContainerSupport {

    @Autowired
    private FileReceptionQueueRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveAndFindById() {
        FileReceptionQueue queue = new FileReceptionQueue();
        queue.setFileName("test.csv");
        queue.setFilePath("/data/test.csv");
        queue.setSourceSystem("SOURCE_A");
        queue.setStatus("PENDING");

        FileReceptionQueue saved = repository.save(queue);

        Optional<FileReceptionQueue> result = repository.findById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("test.csv", result.get().getFileName());
    }

    @Test
    void shouldFindBySourceSystem() {
        FileReceptionQueue queue = new FileReceptionQueue();
        queue.setFileName("test.csv");
        queue.setFilePath("/data/test.csv");
        queue.setSourceSystem("SOURCE_A");
        queue.setStatus("PENDING");
        repository.save(queue);

        List<FileReceptionQueue> result = repository.findBySourceSystem("SOURCE_A");

        assertFalse(result.isEmpty());
        assertEquals("SOURCE_A", result.get(0).getSourceSystem());
    }

    @Test
    void shouldFindByStatus() {
        FileReceptionQueue queue = new FileReceptionQueue();
        queue.setFileName("test.csv");
        queue.setFilePath("/data/test.csv");
        queue.setStatus("PENDING");
        repository.save(queue);

        List<FileReceptionQueue> result = repository.findByStatus("PENDING");

        assertFalse(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForUnknownSourceSystem() {
        List<FileReceptionQueue> result = repository.findBySourceSystem("UNKNOWN");

        assertTrue(result.isEmpty());
    }
}
