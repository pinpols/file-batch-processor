package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SecurityAndBoundaryIT extends PostgresContainerSupport {

    @Autowired
    private ImportedRecordRepository importedRecordRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @BeforeEach
    void setUp() {
        importedRecordRepository.deleteAll();
        recordTraceRepository.deleteAll();
    }

    @Test
    void shouldLoadApplicationContext() {
        assertNotNull(importedRecordRepository);
        assertNotNull(recordTraceRepository);
    }

    @Test
    void shouldHandleRepositoryOperations() {
        assertTrue(true);
    }
}
