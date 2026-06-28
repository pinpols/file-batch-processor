package com.example.filebatchprocessor.unit.batch.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.batch.writer.strategy.ImportContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportContextTest {
    @Test
    void defaultBusinessKeyUnchanged() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", null);
        assertEquals("alice:2026-06-28", ctx.buildBusinessKey("alice"));
    }

    @Test
    void nullNameFallsBackToUnknown() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", null);
        assertEquals("unknown:2026-06-28", ctx.buildBusinessKey(null));
    }

    @Test
    void fromFieldsDefaultEqualsBuildBusinessKey() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", null);
        assertEquals("alice:2026-06-28", ctx.buildBusinessKeyFromFields(Map.of("name", "alice")));
    }

    @Test
    void fromFieldsMissingNameIsUnknown() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", null);
        // mappedRow 非空但无 name 键 → 与默认 name=null 口径一致 = unknown
        assertEquals("unknown:2026-06-28", ctx.buildBusinessKeyFromFields(new HashMap<>()));
    }

    @Test
    void configuredFieldsBusinessKey() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", List.of("name", "category"));
        Map<String, Object> row = new HashMap<>();
        row.put("name", "alice");
        row.put("category", "food");
        assertEquals("alice|food:2026-06-28", ctx.buildBusinessKeyFromFields(row));
    }

    @Test
    void configuredFieldsNullValueBecomesEmpty() {
        ImportContext ctx = new ImportContext("2026-06-28", 1L, "f.csv", List.of("name", "category"));
        Map<String, Object> row = new HashMap<>();
        row.put("name", "alice");
        // category 缺失 → 空串
        assertEquals("alice|:2026-06-28", ctx.buildBusinessKeyFromFields(row));
    }
}
