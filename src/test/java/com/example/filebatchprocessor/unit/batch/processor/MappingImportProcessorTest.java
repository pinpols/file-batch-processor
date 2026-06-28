package com.example.filebatchprocessor.unit.batch.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.filebatchprocessor.batch.processor.FileImportRecordProcessor;
import com.example.filebatchprocessor.batch.processor.MappingImportProcessor;
import com.example.filebatchprocessor.exception.RecordValidationException;
import com.example.filebatchprocessor.mapping.MappingEngine;
import com.example.filebatchprocessor.mapping.MappingEngine.MappingRule;
import com.example.filebatchprocessor.mapping.TransformOp;
import com.example.filebatchprocessor.model.FileRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingImportProcessorTest {

    private final FileImportRecordProcessor delegate = new FileImportRecordProcessor();
    private final MappingEngine mappingEngine = new MappingEngine();

    private MappingImportProcessor processor(List<MappingRule> rules) {
        return new MappingImportProcessor(delegate, mappingEngine, rules);
    }

    @Test
    void feedModeMapsNameAndOverflowToAttributes() {
        var rules = List.of(
                new MappingRule("c_name", "name", TransformOp.UPPER, null, false),
                new MappingRule("c_cat", "category", TransformOp.NONE, null, false));
        FileRecord in = new FileRecord();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("c_name", "alice");
        raw.put("c_cat", "food");
        in.setRawValues(raw);

        FileRecord out = processor(rules).process(in);

        assertEquals("ALICE", out.getName());
        assertNull(out.getDescription());
        assertEquals(Map.of("category", "food"), out.getAttributes());
    }

    @Test
    void feedModeMapsDescriptionAndNoOverflowGivesNullAttributes() {
        var rules = List.of(
                new MappingRule("c_name", "name", TransformOp.NONE, null, false),
                new MappingRule("c_desc", "description", TransformOp.NONE, null, false));
        FileRecord in = new FileRecord();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("c_name", "bob");
        raw.put("c_desc", "hello");
        in.setRawValues(raw);

        FileRecord out = processor(rules).process(in);

        assertEquals("bob", out.getName());
        assertEquals("hello", out.getDescription());
        assertNull(out.getAttributes());
    }

    @Test
    void defaultModeDelegatesToOriginalProcessor() {
        FileRecord in = new FileRecord();
        in.setName("bob");

        FileRecord out = processor(List.of()).process(in);

        assertEquals("BOB", out.getName());
    }

    @Test
    void feedModeMissingNameThrows() {
        var rules = List.of(new MappingRule("c_x", "category", TransformOp.NONE, null, false));
        FileRecord in = new FileRecord();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("c_x", "v");
        in.setRawValues(raw);

        assertThrows(RecordValidationException.class, () -> processor(rules).process(in));
    }

    @Test
    void nullRecordThrows() {
        assertThrows(RecordValidationException.class, () -> processor(List.of()).process(null));
    }
}
