package com.example.filebatchprocessor.batch.reader.spi;

import org.springframework.stereotype.Component;

@Component
public class ExcelDocumentRecordReaderProvider implements DocumentRecordReaderProvider {

    @Override
    public boolean supports(String format) {
        return "EXCEL".equals(format) || "XLSX".equals(format);
    }

    @Override
    public DocumentRecordReader create(DocumentReadOptions options) {
        return new ExcelDocumentRecordReader(options);
    }
}
