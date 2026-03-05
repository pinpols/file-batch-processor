package com.example.filebatchprocessor.batch.reader.spi;

/**
 * Parser provider SPI for platform-managed parser selection.
 */
public interface RecordLineParserProvider {

    /**
     * Whether this provider supports the given format, e.g. CSV/FIXED.
     */
    boolean supports(String format);

    /**
     * Create a parser for the given delimiter (may be ignored by certain formats).
     */
    RecordLineParser create(String delimiter);
}
