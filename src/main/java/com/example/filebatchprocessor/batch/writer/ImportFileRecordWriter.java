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
            if (!seenKeys.add(bizKey)) {
                continue;
            }
            ImportedRecord entity = new ImportedRecord();
            entity.setBusinessKey(bizKey);
            entity.setName(item.getName());
            entity.setDescription(item.getDescription());
            entity.setBatchDate(batchDate);
            try {
                importedRecordRepository.save(entity);
                log.info("Persisted record key={} data={}", bizKey, item);
            } catch (Exception e) {
                log.error("Failed to persist record key={} for batchDate={}", bizKey, batchDate, e);
                // 违反唯一约束时视为幂等命中，可忽略或做更新逻辑
            }
            writeCount++;
        }
    }
}


