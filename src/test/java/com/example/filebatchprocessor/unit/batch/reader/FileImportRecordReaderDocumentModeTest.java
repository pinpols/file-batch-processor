package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.filebatchprocessor.batch.reader.FileImportRecordReader;
import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory;
import com.example.filebatchprocessor.batch.reader.spi.JsonDocumentRecordReaderProvider;
import com.example.filebatchprocessor.model.FileRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.core.io.ByteArrayResource;

class FileImportRecordReaderDocumentModeTest {

    private DocumentRecordReaderFactory jsonFactory() {
        return new DocumentRecordReaderFactory(List.of(new JsonDocumentRecordReaderProvider(new ObjectMapper())));
    }

    private FileImportRecordReader reader(String json, Integer shardIndex, Integer shardTotal) {
        return new FileImportRecordReader(
                new ByteArrayResource(json.getBytes()),
                shardIndex,
                shardTotal,
                "JSON",
                null,
                null,
                jsonFactory(),
                new DocumentReadOptions(null, null));
    }

    @Test
    void readsAllJsonRecordsThenNull() throws Exception {
        FileImportRecordReader r = reader("[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]", 0, 1);
        r.open(new ExecutionContext());
        assertEquals("a", r.read().getName());
        assertEquals("b", r.read().getName());
        assertEquals("c", r.read().getName());
        assertNull(r.read());
    }

    @Test
    void shardingByRecordSeq() throws Exception {
        FileImportRecordReader r =
                reader("[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"},{\"name\":\"d\"}]", 0, 2);
        r.open(new ExecutionContext());
        FileRecord first = r.read();
        FileRecord second = r.read();
        assertEquals("a", first.getName());
        assertEquals("c", second.getName());
        assertNull(r.read());
    }
}
