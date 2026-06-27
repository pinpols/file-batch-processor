package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.reader.spi.JsonDocumentRecordReader;
import com.example.filebatchprocessor.model.FileRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class JsonDocumentRecordReaderTest {

    @Test
    void parsesJsonArrayToFileRecords() throws Exception {
        String json = "[{\"id\":1,\"name\":\"a\",\"description\":\"x\"},{\"name\":\"b\"}]";
        JsonDocumentRecordReader reader = new JsonDocumentRecordReader(new ObjectMapper());
        Iterator<FileRecord> it = reader.open(new ByteArrayResource(json.getBytes()));
        List<FileRecord> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        reader.close();

        assertEquals(2, out.size());
        assertEquals(1L, out.get(0).getId());
        assertEquals("a", out.get(0).getName());
        assertEquals("x", out.get(0).getDescription());
        assertEquals("b", out.get(1).getName());
    }

    @Test
    void nonObjectArrayElementThrows() throws Exception {
        String json = "[{\"name\":\"a\"}, 5, {\"name\":\"b\"}]";
        com.example.filebatchprocessor.batch.reader.spi.JsonDocumentRecordReader reader =
                new com.example.filebatchprocessor.batch.reader.spi.JsonDocumentRecordReader(
                        new com.fasterxml.jackson.databind.ObjectMapper());
        java.util.Iterator<com.example.filebatchprocessor.model.FileRecord> it =
                reader.open(new org.springframework.core.io.ByteArrayResource(json.getBytes()));
        // 第一条 a 正常
        org.junit.jupiter.api.Assertions.assertEquals("a", it.next().getName());
        // 遇到标量 5 → 抛异常(而非静默结束)
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            if (it.hasNext()) {
                it.next();
            }
        });
        reader.close();
    }
}
