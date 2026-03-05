package com.example.filebatchprocessor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileExportServiceTest {

    private final FileExportService service = new FileExportService();

    @Test
    void exportToJSON_shouldCreateReadableJsonFile(@TempDir Path tempDir) throws IOException {
        String filePath = service.exportToJSON(
                tempDir.toString(),
                "records.json",
                List.of(Map.of("id", "1", "name", "alice"))
        );

        Path path = Path.of(filePath);
        assertTrue(Files.exists(path));
        String content = Files.readString(path);
        assertTrue(content.contains("\"id\""));
        assertTrue(content.contains("\"alice\""));
    }

    @Test
    void exportCompressed_shouldCreateZipFile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "sample-data");

        String zipPath = service.exportCompressed(tempDir.toString(), "archive.zip", source.toString());
        assertTrue(Files.exists(Path.of(zipPath)));
        assertTrue(Files.size(Path.of(zipPath)) > 0);
    }
}
