package com.example.filebatchprocessor.unit.batch.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.filebatchprocessor.batch.reader.FileImportRecordReader;
import com.example.filebatchprocessor.model.FileRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.core.io.ByteArrayResource;

class FileImportRecordReaderFeedModeTest {

    private FileImportRecordReader feedReader(String csv, List<String> feedHeaderColumns) {
        return new FileImportRecordReader(
                new ByteArrayResource(csv.getBytes()),
                0, 1, "CSV", ",", null,
                null, null, feedHeaderColumns);
    }

    @Test
    void feedModeAutoDetectHeader() throws Exception {
        String csv = "c_name,c_cat\nalice,food\nbob,drink\n";
        FileImportRecordReader r = feedReader(csv, List.of());
        r.open(new ExecutionContext());

        FileRecord first = r.read();
        assertEquals(2L, first.getLineNo());
        assertNull(first.getName());
        assertNull(first.getId());
        assertEquals("alice", first.getRawValues().get("c_name"));
        assertEquals("food", first.getRawValues().get("c_cat"));

        FileRecord second = r.read();
        assertEquals(3L, second.getLineNo());
        assertEquals("bob", second.getRawValues().get("c_name"));
        assertEquals("drink", second.getRawValues().get("c_cat"));

        assertNull(r.read());
        r.close();
    }

    @Test
    void feedModeExplicitColumnsSkipsFileHeader() throws Exception {
        String csv = "h1,h2\nx,y\n";
        FileImportRecordReader r = feedReader(csv, List.of("col_a", "col_b"));
        r.open(new ExecutionContext());

        FileRecord rec = r.read();
        assertNull(rec.getName());
        assertEquals("x", rec.getRawValues().get("col_a"));
        assertEquals("y", rec.getRawValues().get("col_b"));

        assertNull(r.read());
        r.close();
    }

    @Test
    void defaultModeUnaffected() throws Exception {
        String csv = "id,name,description\n1,alice,hi\n";
        FileImportRecordReader r = new FileImportRecordReader(
                new ByteArrayResource(csv.getBytes()), 0, 1, "CSV", ",");
        r.open(new ExecutionContext());

        FileRecord rec = r.read();
        assertEquals("alice", rec.getName());
        assertNull(rec.getRawValues());

        assertNull(r.read());
        r.close();
    }
}
