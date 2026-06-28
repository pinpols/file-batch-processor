package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 导入相关仓库的装配冒烟(context-load smoke),非端到端导入行为测试。
 * 仅验证 Spring 上下文能起、相关 JpaRepository bean 被正确装配;真正的导入流程行为
 * 覆盖在专门的导入流程测试中。
 */
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
