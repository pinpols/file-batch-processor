package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;

/**
 * 简单 CSV 行解析实现，按分隔符拆分字段。
 */
public class CsvRecordLineParser implements RecordLineParser {

    private final String delimiter;

    public CsvRecordLineParser(String delimiter) {
        this.delimiter = (delimiter == null || delimiter.isEmpty()) ? "," : delimiter;
    }

    @Override
    public FileRecord parse(String line) {
        String[] fields = line.split(delimiter, -1);
        FileRecord record = new FileRecord();
        if (fields.length > 0 && !fields[0].trim().isEmpty()) {
            record.setId(Long.parseLong(fields[0].trim()));
        }
        if (fields.length > 1) {
            record.setName(fields[1].trim());
        }
        if (fields.length > 2) {
            record.setDescription(fields[2].trim());
        }
        return record;
    }
}

