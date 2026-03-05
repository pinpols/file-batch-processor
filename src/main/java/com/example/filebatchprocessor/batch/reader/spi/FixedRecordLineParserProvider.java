package com.example.filebatchprocessor.batch.reader.spi;

import org.springframework.stereotype.Component;

@Component
public class FixedRecordLineParserProvider implements RecordLineParserProvider {

    @Override
    public boolean supports(String format) {
        return "FIXED".equalsIgnoreCase(format);
    }

    @Override
    public RecordLineParser create(String delimiter) {
        return new FixedRecordLineParser();
    }
}
