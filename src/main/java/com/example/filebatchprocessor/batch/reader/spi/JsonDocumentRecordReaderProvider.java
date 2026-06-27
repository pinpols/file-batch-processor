package com.example.filebatchprocessor.batch.reader.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonDocumentRecordReaderProvider implements DocumentRecordReaderProvider {

    private final ObjectMapper objectMapper;

    public JsonDocumentRecordReaderProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String format) {
        return "JSON".equals(format);
    }

    @Override
    public DocumentRecordReader create(DocumentReadOptions options) {
        return new JsonDocumentRecordReader(objectMapper);
    }
}
