package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 持久层往返冒烟(非性能基准)。
 *
 * <p>原方法名 shouldMeasureThroughput / shouldHandleConcurrentOperations 只做了 assertNotNull,
 * 既不测吞吐也不测并发,属名不副实。真正的吞吐/并发基准在容器化集成测试里易 flaky 且成本高,
 * 故此处诚实降格为「仓库装配 + 基本 CRUD 往返」冒烟,并补一个真实的 save/count 断言。
 */
@SpringBootTest
@ActiveProfiles("test")
class PerformanceIT extends PostgresContainerSupport {

    @Autowired
    private ImportedRecordRepository importedRecordRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @BeforeEach
    void setUp() {
        importedRecordRepository.deleteAll();
        recordTraceRepository.deleteAll();
    }

    /** 批量保存 N 条后,count 应等于 N —— 验证 JPA 映射与持久化往返真实可用。 */
    @Test
    void shouldPersistAndCountImportedRecords() {
        int n = 25;
        for (int i = 0; i < n; i++) {
            ImportedRecord record = new ImportedRecord();
            record.setBusinessKey("PERF-BK-" + i);
            record.setBatchDate("2025-01-20");
            record.setName("perf-" + i);
            importedRecordRepository.save(record);
        }

        assertEquals(n, importedRecordRepository.count(), "保存 25 条后总数应为 25");
        assertEquals(n, importedRecordRepository.countByBatchDate("2025-01-20"), "按批次日期统计应为 25");
        assertTrue(
                importedRecordRepository.existsByBusinessKeyAndBatchDate("PERF-BK-0", "2025-01-20"),
                "已保存的业务键应可被幂等性检查命中");
    }

    /** 仓库 bean 装配冒烟(原 shouldHandleConcurrentOperations,实际从未做并发)。 */
    @Test
    void recordTraceRepositoryIsWired() {
        assertNotNull(recordTraceRepository);
    }
}
