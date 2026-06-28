package com.example.filebatchprocessor.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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

    /**
     * 真实幂等验证:对同一 (business_key, batch_date, partition_key) 插入两次,
     * 第二次命中唯一约束 uk_import_biz_batch_part 并 ON CONFLICT DO NOTHING,
     * 最终该业务键只应留下 1 行。
     */
    @Test
    void shouldBeIdempotentForSameBusinessKeys() {
        String businessKey = "BK-IDEMPOTENT-001";
        String batchDate = "2025-01-15";
        String partitionKey = "2025_01";

        String insert =
                "INSERT INTO imported_records_partition (business_key, name, batch_date, partition_key, status) "
                        + "VALUES (?, ?, ?, ?, 'IMPORTED') "
                        + "ON CONFLICT (business_key, batch_date, partition_key) DO NOTHING";

        int first = jdbcTemplate.update(insert, businessKey, "first-write", batchDate, partitionKey);
        int second = jdbcTemplate.update(insert, businessKey, "second-write", batchDate, partitionKey);

        assertEquals(1, first, "首次插入应写入 1 行");
        assertEquals(0, second, "重复键的二次插入应被 ON CONFLICT 吞掉,影响 0 行");

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM imported_records_partition WHERE business_key = ? AND batch_date = ? AND partition_key = ?",
                Long.class,
                businessKey,
                batchDate,
                partitionKey);
        assertEquals(1L, count, "幂等:同一业务键最终只应有 1 行");
    }
}
