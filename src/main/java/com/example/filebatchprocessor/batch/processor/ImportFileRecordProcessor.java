package com.example.filebatchprocessor.batch.processor;

import com.example.filebatchprocessor.model.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 导入文件处理器：示例将 name 转大写，可在此扩展业务校验与转换。
 */
@Component

public class ImportFileRecordProcessor implements ItemProcessor<FileRecord, FileRecord> {

    private static final Logger log = LoggerFactory.getLogger(ImportFileRecordProcessor.class);

    @Override
    public FileRecord process(final FileRecord record) {
        if (record == null) {
            return null;
        }
        String processedName = record.getName() == null ? null : record.getName().toUpperCase();

        FileRecord processedRecord = new FileRecord();
        processedRecord.setId(record.getId());
        processedRecord.setName(processedName);
        processedRecord.setDescription(record.getDescription());

        log.info("Processing record: {} -> {}", record, processedRecord);

        return processedRecord;
    }
}

