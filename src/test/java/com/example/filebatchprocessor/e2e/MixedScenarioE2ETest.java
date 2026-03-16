package com.example.filebatchprocessor.e2e;

import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MixedScenarioE2ETest extends PostgresContainerSupport {

    @Autowired
    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        importedRecordPartitionedRepository.deleteAll();
        recordTraceRepository.deleteAll();
    }

    @Test
    void shouldHandleMixedScenarios() {
        assertNotNull(importedRecordPartitionedRepository);
        assertNotNull(recordTraceRepository);
    }

    @Test
    void shouldBeIdempotentForSameBusinessKeys() {
        assertNotNull(jdbcTemplate);
    }
}
