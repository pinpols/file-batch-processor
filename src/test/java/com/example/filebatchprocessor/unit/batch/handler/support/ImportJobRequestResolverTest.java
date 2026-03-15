package com.example.filebatchprocessor.unit.batch.handler.support;

import com.example.filebatchprocessor.batch.handler.support.ImportJobRequest;
import com.example.filebatchprocessor.batch.handler.support.ImportJobRequestResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImportJobRequestResolverTest {

    @Test
    void shouldResolveAndDecodeParams() throws Exception {
        Path tempFile = Files.createTempFile("import", ".csv");
        Files.writeString(tempFile, "id,name\n1,Alice\n");

        ImportJobRequestResolver resolver = new ImportJobRequestResolver();
        String raw = "input=" + tempFile + "&runMode=backfill&file.delimiter=%7C&priority=7";
        ImportJobRequest request = resolver.resolve(raw, "unused.csv", 0, 1);

        assertEquals(tempFile.toString(), request.getInputFile());
        assertEquals("backfill", request.getRunMode());
        assertEquals("|", request.getFileDelimiter());
        assertEquals(7, request.getPriority());
        assertNotNull(request.getBatchDate());
    }

    @Test
    void shouldFailWhenFileNotExists() {
        ImportJobRequestResolver resolver = new ImportJobRequestResolver();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("input=/tmp/not_exists.csv", "unused.csv", 0, 1));
        assertTrue(ex.getMessage().contains("Input file validation failed"));
    }
}
