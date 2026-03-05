package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;

/**
 * 简单定长行解析实现，字段起止位置可按需要调整。
 */
public class FixedRecordLineParser implements RecordLineParser {

    @Override
    public FileRecord parse(String line) {
        FileRecord record = new FileRecord();
        String idPart = safeSub(line, 0, 10).trim();
        if (!idPart.isEmpty()) {
            record.setId(Long.parseLong(idPart));
        }
        record.setName(safeSub(line, 10, 40).trim());
        record.setDescription(safeSub(line, 40, line.length()).trim());
        return record;
    }

    private String safeSub(String line, int start, int end) {
        if (line == null || start >= line.length()) {
            return "";
        }
        int realEnd = Math.min(end, line.length());
        return line.substring(start, realEnd);
    }
}

