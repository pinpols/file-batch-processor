package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;

/**
 * 默认 CSV 行解析器，处理兼容旧导入链路的三列结构：id、name、description。
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
