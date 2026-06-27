package com.example.filebatchprocessor.unit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.model.RowData;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RowDataTest {
    @Test
    void holdsValuesAndLineNo() {
        RowData r = new RowData(Map.of("a", "1"), 5L);
        assertEquals("1", r.values().get("a"));
        assertEquals(5L, r.lineNo());
    }
}
