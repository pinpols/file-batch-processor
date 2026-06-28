package com.example.filebatchprocessor.unit.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.manifest.JsonManifestParser;
import com.example.filebatchprocessor.manifest.ParsedManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonManifestParserTest {

    private final JsonManifestParser parser = new JsonManifestParser(new ObjectMapper());

    @Test
    void parsesValidManifest() {
        String json = """
            {"manifestId":"M1","sourceSystem":"S","bizDate":"2026-06-27",
             "files":[
               {"fileName":"a.csv","expectedRecordCount":10,"checksum":"abc","required":true},
               {"fileName":"b.csv"}
             ]}""";
        ParsedManifest m = parser.parse(json);
        assertEquals("M1", m.manifestId());
        assertEquals(2, m.files().size());
        assertEquals("a.csv", m.files().get(0).fileName());
        assertEquals(10L, m.files().get(0).expectedRecordCount());
        assertTrue(m.files().get(1).required()); // 默认 required=true
    }

    @Test
    void rejectsMissingManifestId() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{\"files\":[{\"fileName\":\"a.csv\"}]}"));
    }

    @Test
    void rejectsNonMd5ChecksumAlgorithm() {
        String json =
                "{\"manifestId\":\"M\",\"files\":[{\"fileName\":\"a\",\"checksum\":\"x\",\"checksumAlgorithm\":\"SHA-256\"}]}";
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }
}
