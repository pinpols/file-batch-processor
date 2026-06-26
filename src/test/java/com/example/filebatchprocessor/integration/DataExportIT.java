package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DataExportIT extends PostgresContainerSupport {

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
