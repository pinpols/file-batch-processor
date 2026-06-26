package com.example.filebatchprocessor.batch.writer.strategy;

import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.PartitionedImportService;
import java.util.ArrayList;
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
    private static final String JOB_NAME = "importJob";

    private final PartitionedImportService partitionedImportService;
    private final RecordTraceRepository recordTraceRepository;

    public BatchChunkImportStrategy(
            PartitionedImportService partitionedImportService, RecordTraceRepository recordTraceRepository) {
        this.partitionedImportService = partitionedImportService;
        this.recordTraceRepository = recordTraceRepository;
    }

    @Override
    public int persist(List<? extends FileRecord> records, ImportContext context) {
        List<ImportedRecordPartitioned> entities = new ArrayList<>(records.size());
        Set<String> businessKeys = new LinkedHashSet<>();
        for (FileRecord item : records) {
            String bizKey = context.buildBusinessKey(item.getName());
            businessKeys.add(bizKey);

            ImportedRecordPartitioned entity = new ImportedRecordPartitioned();
            entity.setBusinessKey(bizKey);
            entity.setName(item.getName());
            entity.setDescription(item.getDescription());
            entity.setBatchDate(context.batchDate());
            entity.setSourceFileName(context.inputFileName());
            entities.add(entity);
        }

        partitionedImportService.batchImportIdempotent(entities);

        // 批量回查 id,关联 trace(回查失败或缺失则 trace 的 partitionId 置空,不阻断导入)
        Map<String, Long> idByKey = partitionedImportService.findIdsByBatchDate(businessKeys, context.batchDate());
        List<RecordTrace> traces = new ArrayList<>(records.size());
        for (FileRecord item : records) {
            String bizKey = context.buildBusinessKey(item.getName());
            traces.add(buildTrace(bizKey, item.getLineNo(), idByKey.get(bizKey), context));
        }
        try {
            recordTraceRepository.saveAll(traces);
        } catch (Exception ex) {
            // trace 是辅助审计,失败不应回滚已写入的业务数据
            log.warn("Failed to persist {} record traces in batch path", traces.size(), ex);
        }

        return records.size();
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
