package com.example.filebatchprocessor.batch.reader.spi;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecordLineParserFactory {

    private final List<RecordLineParserProvider> providers;

    public RecordLineParserFactory(List<RecordLineParserProvider> providers) {
        this.providers = providers;
    }

    public RecordLineParser create(String format, String delimiter) {
        String resolvedFormat =
                (format == null || format.isBlank()) ? "CSV" : format.trim().toUpperCase();
        return providers.stream()
                .filter(p -> p.supports(resolvedFormat))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file.format: " + resolvedFormat))
                .create(delimiter);
    }
}
