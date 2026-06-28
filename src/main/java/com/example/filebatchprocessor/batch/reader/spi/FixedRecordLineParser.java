package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;

/**
 * 默认定长行解析器，字段位置与当前内置导入样例保持一致。
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
