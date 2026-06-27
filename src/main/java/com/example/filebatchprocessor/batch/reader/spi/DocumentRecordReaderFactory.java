package com.example.filebatchprocessor.batch.reader.spi;

import java.util.List;
import org.springframework.stereotype.Component;

/** 文档级 reader 工厂(对标 RecordLineParserFactory)。 */
@Component
public class DocumentRecordReaderFactory {

    private final List<DocumentRecordReaderProvider> providers;

    public DocumentRecordReaderFactory(List<DocumentRecordReaderProvider> providers) {
        this.providers = providers;
    }

    public boolean supportsDocument(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        String f = format.trim().toUpperCase();
        return providers.stream().anyMatch(p -> p.supports(f));
    }

    public DocumentRecordReader create(String format, DocumentReadOptions options) {
        String f = format.trim().toUpperCase();
        return providers.stream()
                .filter(p -> p.supports(f))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported document format: " + f))
                .create(options);
    }
}
