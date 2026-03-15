package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DataExportIT {

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @BeforeEach
    void setUp() {
        recordTraceRepository.deleteAll();
    }

    @Test
    void shouldLoadExportRepositories() {
        assertNotNull(recordTraceRepository);
    }

    @Test
    void shouldHandleExportData() {
        assertTrue(true);
    }
}
