package com.example.filebatchprocessor.unit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.filebatchprocessor.model.FileRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileRecordAttributesTest {
    @Test
    void rawValuesAndAttributesDefaultNull() {
        FileRecord r = new FileRecord();
        assertNull(r.getRawValues());
        assertNull(r.getAttributes());
    }

    @Test
    void canSetRawValuesAndAttributes() {
        FileRecord r = new FileRecord();
        r.setRawValues(Map.of("c", "v"));
        r.setAttributes(Map.of("k", "x"));
        assertEquals("v", r.getRawValues().get("c"));
        assertEquals("x", r.getAttributes().get("k"));
    }
}
