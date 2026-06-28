package com.example.filebatchprocessor.batch.writer.strategy;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.PartitionedImportService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 批量快路径:一次批量 INSERT ... ON CONFLICT DO NOTHING 写分区表,再一次批量回查 id 关联 trace。
 *
 * <p>把旧的「每条 SELECT + INSERT + 独立事务 + trace INSERT」从 3N 次 round-trip 压到约 3 次/chunk。
 * 运行在 Spring Batch chunk 事务内,任何异常都会让整个 chunk 回滚,由 Context 降级到逐条路径处理。
 */
public class BatchChunkImportStrategy implements ChunkImportStrategy {

    private static final Logger log = LoggerFactory.getLogger(BatchChunkImportStrategy.class);
    private static final String JOB_NAME = BatchJobNames.FILE_IMPORT_JOB;

    private final PartitionedImportService partitionedImportService;
    private final RecordTraceRepository recordTraceRepository;
    // 用 REQUIRES_NEW 把批量写与 trace 写各自隔离,失败只回滚各自的独立事务,
    // 不把外层 Spring Batch chunk 事务标记 rollback-only,保证降级路径能干净执行。
    private final org.springframework.transaction.support.TransactionTemplate requiresNewTx;

    public BatchChunkImportStrategy(
            PartitionedImportService partitionedImportService,
            RecordTraceRepository recordTraceRepository,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.partitionedImportService = partitionedImportService;
        this.recordTraceRepository = recordTraceRepository;
        this.requiresNewTx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int persist(List<? extends FileRecord> records, ImportContext context) {
        List<ImportedRecordPartitioned> entities = new ArrayList<>(records.size());
        // 主循环算出的 bizKey 与 records 同序保存,trace 循环复用,消除「两次算 key 可能不一致」的隐患。
        List<String> orderedKeys = new ArrayList<>(records.size());
        Set<String> businessKeys = new LinkedHashSet<>();
        for (FileRecord item : records) {
            String bizKey = businessKeyOf(item, context);
            orderedKeys.add(bizKey);
            businessKeys.add(bizKey);

            ImportedRecordPartitioned entity = new ImportedRecordPartitioned();
            entity.setBusinessKey(bizKey);
            entity.setName(item.getName());
            entity.setDescription(item.getDescription());
            entity.setBatchDate(context.batchDate());
            entity.setSourceFileName(context.inputFileName());
            // attributes==null 时 set null,JSONB 列保持 NULL,默认路径零影响。
            entity.setAttributes(item.getAttributes());
            entities.add(entity);
        }

        // 批量写在独立事务中执行:失败只回滚本事务,异常上抛由 Writer 降级到逐条路径,
        // 而不会把外层 chunk 事务标记成 rollback-only(否则降级写也会在 chunk 提交时整体回滚)。
        requiresNewTx.executeWithoutResult(s -> partitionedImportService.batchImportIdempotent(entities));

        // 批量回查 id,关联 trace(回查失败或缺失则 trace 的 partitionId 置空,不阻断导入)
        Map<String, Long> idByKey = partitionedImportService.findIdsByBatchDate(businessKeys, context.batchDate());
        List<RecordTrace> traces = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            FileRecord item = records.get(i);
            String bizKey = orderedKeys.get(i);
            traces.add(buildTrace(bizKey, item.getLineNo(), idByKey.get(bizKey), context));
        }
        try {
            // trace 同样在独立事务中写,失败不波及已提交的业务数据,也不污染外层 chunk 事务
            requiresNewTx.executeWithoutResult(s -> recordTraceRepository.saveAll(traces));
        } catch (Exception ex) {
            // trace 是辅助审计,失败不应回滚已写入的业务数据
            log.warn("Failed to persist {} record traces in batch path", traces.size(), ex);
        }

        return records.size();
    }

    /**
     * 统一的 business_key 组装口径:从 FileRecord 拼 mappedRow 后交给 {@link ImportContext#buildBusinessKeyFromFields}。
     * <p>默认(businessKeyFields=null)退回 {@code name:batchDate},与历史 {@code buildBusinessKey(name)} 字节级一致。
     * 三处(本主循环、trace、PerRecord、Writer.dedup)必须逐字一致。
     */
    public static String businessKeyOf(FileRecord item, ImportContext context) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", item.getName());
        row.put("description", item.getDescription());
        if (item.getAttributes() != null) {
            row.putAll(item.getAttributes());
        }
        return context.buildBusinessKeyFromFields(row);
    }

    private RecordTrace buildTrace(String businessKey, Long lineNo, Long partitionId, ImportContext context) {
        RecordTrace trace = new RecordTrace();
        trace.setBusinessKey(businessKey);
        trace.setBatchDate(context.batchDate());
        trace.setJobName(JOB_NAME);
        trace.setJobExecutionId(context.jobExecutionId());
        trace.setSourceFileName(context.inputFileName());
        trace.setLineNo(lineNo);
        trace.setImportedRecordPartitionId(partitionId);
        return trace;
    }
}
