package com.example.filebatchprocessor.batch.writer;

import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.service.PartitionedImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 导入文件写入器：写表 imported_records，依赖唯一索引保证落库幂等。
 */
public class ImportFileRecordWriter implements ItemWriter<FileRecord>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ImportFileRecordWriter.class);

    private final String batchDate;
    // 分区导入服务，负责写入分区表并保证幂等
    private final PartitionedImportService partitionedImportService;
    private final Set<String> seenKeys = new HashSet<>();
    private long writeCount = 0L;

    // 使用 TransactionTemplate 在每条记录上开启 REQUIRES_NEW 事务，避免单条写入失败影响外层 chunk 事务
    private final TransactionTemplate txTemplate;

    public ImportFileRecordWriter(String batchDate, PartitionedImportService partitionedImportService,
                                  PlatformTransactionManager transactionManager) {
        this.batchDate = StringUtils.hasText(batchDate) ? batchDate : "default";
        this.partitionedImportService = partitionedImportService;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }



    private String buildBusinessKey(FileRecord item) {
        String name = item.getName() == null ? "unknown" : item.getName();
        return name + ":" + batchDate;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        writeCount = 0L;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putLong("write.count", writeCount);
        return stepExecution.getExitStatus();
    }

    @Override
    public void write(Chunk<? extends FileRecord> chunk) throws Exception {
        write(chunk.getItems());
    }

    public void write(List<? extends FileRecord> items) {
        log.info("Writing {} records for batchDate={}", items.size(), batchDate);

        for (FileRecord item : items) {
            String bizKey = buildBusinessKey(item);
            
            // 内存去重：跳过本批次中已处理的记录
            if (!seenKeys.add(bizKey)) {
                log.debug("Skipping duplicate record in current batch: {}", bizKey);
                writeCount++; // 视为已处理
                continue;
            }
            
            // 使用分区导入服务在独立事务中写入分区表，服务负责幂等性检查
            try {
                Boolean saved = txTemplate.execute(status -> {
                    try {
                        partitionedImportService.importRecord(bizKey, item.getName(), item.getDescription(), batchDate, null, null);
                        return Boolean.TRUE;
                    } catch (DataIntegrityViolationException ex) {
                        status.setRollbackOnly();
                        return Boolean.FALSE;
                    }
                });

                if (Boolean.TRUE.equals(saved)) {
                    log.debug("Persisted record key={} data={}", bizKey, item);
                    writeCount++;
                } else {
                    log.debug("Record already exists (idempotent hit): businessKey={}, batchDate={}", bizKey, batchDate);
                    writeCount++; // 视为成功（记录已存在）
                }
            } catch (Exception e) {
                log.error("Failed to persist record key={} for batchDate={}", bizKey, batchDate, e);
            }
        }
    }
}


