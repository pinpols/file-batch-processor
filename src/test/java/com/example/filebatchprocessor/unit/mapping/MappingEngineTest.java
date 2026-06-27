package com.example.filebatchprocessor.unit.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.filebatchprocessor.mapping.MappingEngine;
import com.example.filebatchprocessor.mapping.MappingEngine.MappingRule;
import com.example.filebatchprocessor.mapping.TransformOp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingEngineTest {

    private final MappingEngine engine = new MappingEngine();

    @Test
    void appliesTrimUpperLowerDefault() {
        var rules = List.of(
                new MappingRule("c_name", "name", TransformOp.UPPER, null, true),
                new MappingRule("c_desc", "description", TransformOp.TRIM, null, false),
                new MappingRule("c_cat", "category", TransformOp.DEFAULT, "N/A", false));
        Map<String, Object> src = Map.of("c_name", "alice", "c_desc", "  hi  ", "c_cat", "");
        Map<String, Object> out = engine.apply(rules, src);
        assertEquals("ALICE", out.get("name"));
        assertEquals("hi", out.get("description"));
        assertEquals("N/A", out.get("category"));
    }

    @Test
    void dateFormatToIso() {
        var rules = List.of(new MappingRule("d", "bizDate", TransformOp.DATE_FORMAT, "yyyy/MM/dd", false));
        Map<String, Object> out = engine.apply(rules, Map.of("d", "2026/06/27"));
        assertEquals("2026-06-27", out.get("bizDate"));
    }

    @Test
    void requiredMissingThrows() {
        var rules = List.of(new MappingRule("c", "name", TransformOp.NONE, null, true));
        assertThrows(IllegalArgumentException.class, () -> engine.apply(rules, Map.of("c", "")));
    }
}
