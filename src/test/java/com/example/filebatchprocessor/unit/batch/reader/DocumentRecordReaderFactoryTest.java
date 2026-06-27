package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReader;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderProvider;
import com.example.filebatchprocessor.model.FileRecord;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class DocumentRecordReaderFactoryTest {

    private static DocumentRecordReaderProvider provider(String fmt) {
        return new DocumentRecordReaderProvider() {
            public boolean supports(String format) {
                return fmt.equals(format);
            }

            public DocumentRecordReader create(DocumentReadOptions options) {
                return new DocumentRecordReader() {
                    public Iterator<FileRecord> open(Resource resource) {
                        return List.<FileRecord>of().iterator();
                    }

                    public void close() {}
                };
            }
        };
    }

    @Test
    void supportsDocumentTrueForRegisteredFormat() {
        DocumentRecordReaderFactory f = new DocumentRecordReaderFactory(List.of(provider("JSON")));
        assertTrue(f.supportsDocument("JSON"));
        assertFalse(f.supportsDocument("CSV"));
    }

    @Test
    void createReturnsMatchingReader() {
        DocumentRecordReaderFactory f = new DocumentRecordReaderFactory(List.of(provider("JSON")));
        DocumentRecordReader r = f.create("JSON", new DocumentReadOptions(null, null));
        org.junit.jupiter.api.Assertions.assertNotNull(r);
    }
}
