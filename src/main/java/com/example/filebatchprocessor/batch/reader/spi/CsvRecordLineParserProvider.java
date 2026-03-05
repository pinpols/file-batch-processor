package com.example.filebatchprocessor.batch.reader.spi;

import org.springframework.stereotype.Component;

@Component
public class CsvRecordLineParserProvider implements RecordLineParserProvider {

    @Override
    public boolean supports(String format) {
        return format == null || format.isBlank() || "CSV".equalsIgnoreCase(format);
    }

    @Override
    public RecordLineParser create(String delimiter) {
        return new CsvRecordLineParser(delimiter);
    }
}
