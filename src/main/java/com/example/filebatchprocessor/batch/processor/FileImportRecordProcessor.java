package com.example.filebatchprocessor.batch.processor;

import com.example.filebatchprocessor.exception.RecordValidationException;
import com.example.filebatchprocessor.model.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 导入文件处理器：执行基础字段校验，并将名称字段标准化为大写。 */
@Component
public class FileImportRecordProcessor implements ItemProcessor<FileRecord, FileRecord> {

    private static final Logger log = LoggerFactory.getLogger(FileImportRecordProcessor.class);

    @Override
    public FileRecord process(final FileRecord record) {
        if (record == null) {
            throw new RecordValidationException("Record is null");
        }
        if (!StringUtils.hasText(record.getName())) {
            throw new RecordValidationException("Record name is required");
        }
        String processedName =
                record.getName() == null ? null : record.getName().toUpperCase();

        FileRecord processedRecord = new FileRecord();
        processedRecord.setId(record.getId());
        processedRecord.setName(processedName);
        processedRecord.setDescription(record.getDescription());

        log.debug("Processing record id={}", record.getId());

        return processedRecord;
    }
}
