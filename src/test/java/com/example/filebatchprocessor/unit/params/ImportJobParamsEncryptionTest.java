package com.example.filebatchprocessor.unit.params;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.params.ImportJobParams;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportJobParamsEncryptionTest {

    @Test
    void parsesEncryptionParams() {
        ImportJobParams p = ImportJobParams.from(Map.of(
                "input.file.name", "x.csv.gz.pgp",
                "input.file.encrypted", "true",
                "input.file.compression", "gz"));
        assertEquals(Boolean.TRUE, p.getInputFileEncrypted());
        assertEquals("gz", p.getInputFileCompression());
    }
}
