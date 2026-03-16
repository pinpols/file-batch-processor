package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FileImportIT extends PostgresContainerSupport {

    @Autowired
    private ImportedRecordRepository importedRecordRepository;

    @Autowired
    private DlqRecordRepository dlqRecordRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @BeforeEach
    void setUp() {
        importedRecordRepository.deleteAll();
        dlqRecordRepository.deleteAll();
        recordTraceRepository.deleteAll();
    }

    @Test
    void shouldLoadImportRepositories() {
        assertNotNull(importedRecordRepository);
        assertNotNull(dlqRecordRepository);
        assertNotNull(recordTraceRepository);
    }
}
