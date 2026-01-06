package com.example.filebatchprocessor.batch.writer;

import com.example.filebatchprocessor.model.FileRecord;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
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

/**
 * 导入文件写入器：写表 imported_records，依赖唯一索引保证落库幂等。
 */
public class ImportFileRecordWriter implements ItemWriter<FileRecord>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ImportFileRecordWriter.class);

    private final String batchDate;
    // DAO，依赖数据库唯一约束(business_key,batch_date)实现落库幂等
    private final ImportedRecordRepository importedRecordRepository;
    private final Set<String> seenKeys = new HashSet<>();
    private long writeCount = 0L;

    public ImportFileRecordWriter(String batchDate, ImportedRecordRepository importedRecordRepository) {
        this.batchDate = StringUtils.hasText(batchDate) ? batchDate : "default";
        this.importedRecordRepository = importedRecordRepository;
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
            
            // 直接尝试保存，依赖唯一约束来处理幂等性
            // 这样可以避免 Hibernate session 中的实体状态问题
            ImportedRecord entity = new ImportedRecord();
            entity.setBusinessKey(bizKey);
            entity.setName(item.getName());
            entity.setDescription(item.getDescription());
            entity.setBatchDate(batchDate);
            
            try {
                importedRecordRepository.save(entity);
                log.debug("Persisted record key={} data={}", bizKey, item);
                writeCount++;
            } catch (DataIntegrityViolationException e) {
                // 唯一约束违反：视为幂等命中（记录已存在）
                // 这是预期的行为，不是错误
                log.debug("Record already exists (idempotent hit): businessKey={}, batchDate={}", bizKey, batchDate);
                writeCount++; // 视为成功（记录已存在）
            } catch (Exception e) {
                // 其他异常：记录错误但不中断处理
                log.error("Failed to persist record key={} for batchDate={}", bizKey, batchDate, e);
                // 注意：这里不增加 writeCount，因为写入失败
            }
        }
    }
}


