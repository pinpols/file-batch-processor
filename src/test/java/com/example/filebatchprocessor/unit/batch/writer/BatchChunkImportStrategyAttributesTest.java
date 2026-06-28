package com.example.filebatchprocessor.unit.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.batch.writer.strategy.BatchChunkImportStrategy;
import com.example.filebatchprocessor.batch.writer.strategy.ImportContext;
import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.PartitionedImportService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 验证批量快路径:attributes 透传落 entity + business_key 配置化口径(默认字节级不变)。
 */
class BatchChunkImportStrategyAttributesTest {

    private PartitionedImportService partitionedImportService;
    private RecordTraceRepository recordTraceRepository;
    private BatchChunkImportStrategy strategy;

    @BeforeEach
    void setup() {
        partitionedImportService = mock(PartitionedImportService.class);
        recordTraceRepository = mock(RecordTraceRepository.class);
        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        // findIdsByBatchDate 默认返回空 map(任意参数)
        when(partitionedImportService.findIdsByBatchDate(any(), anyString())).thenReturn(Map.of());
        strategy = new BatchChunkImportStrategy(partitionedImportService, recordTraceRepository, txm);
    }

    private FileRecord record(String name, Map<String, Object> attrs) {
        FileRecord r = new FileRecord();
        r.setName(name);
        r.setDescription("d-" + name);
        r.setLineNo(1L);
        r.setAttributes(attrs);
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<ImportedRecordPartitioned> captureEntities() {
        ArgumentCaptor<List<ImportedRecordPartitioned>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(partitionedImportService).batchImportIdempotent(captor.capture());
        return captor.getValue();
    }

    @Test
    void 默认无attributes无businessKeyFields_业务键与attributes均不变() {
        ImportContext ctx = new ImportContext("2026-06-26", 1L, "in.csv", null);
        strategy.persist(List.of(record("alice", null)), ctx);

        List<ImportedRecordPartitioned> entities = captureEntities();
        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getBusinessKey()).isEqualTo("alice:2026-06-26");
        assertThat(entities.get(0).getAttributes()).isNull();
    }

    @Test
    void feed有attributes_落entity且业务键仍为默认口径() {
        ImportContext ctx = new ImportContext("2026-06-26", 1L, "in.csv", null);
        Map<String, Object> attrs = Map.of("category", "food");
        strategy.persist(List.of(record("alice", attrs)), ctx);

        List<ImportedRecordPartitioned> entities = captureEntities();
        assertThat(entities.get(0).getBusinessKey()).isEqualTo("alice:2026-06-26");
        assertThat(entities.get(0).getAttributes()).isEqualTo(Map.of("category", "food"));
    }

    @Test
    void 多字段businessKey_按字段拼接() {
        ImportContext ctx = new ImportContext("2026-06-26", 1L, "in.csv", List.of("name", "category"));
        Map<String, Object> attrs = Map.of("category", "food");
        strategy.persist(List.of(record("alice", attrs)), ctx);

        List<ImportedRecordPartitioned> entities = captureEntities();
        assertThat(entities.get(0).getBusinessKey()).isEqualTo("alice|food:2026-06-26");
        assertThat(entities.get(0).getAttributes()).isEqualTo(Map.of("category", "food"));
    }
}
