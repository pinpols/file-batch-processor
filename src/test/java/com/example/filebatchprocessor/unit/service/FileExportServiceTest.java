package com.example.filebatchprocessor.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class FileExportServiceTest {

    @Test
    void exportToJSON_shouldCreateReadableJsonFile(@TempDir Path tempDir) throws IOException {
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        FileExportService service = new FileExportService(new ObjectMapper(), fileAssetService, fileProcessLogService);
        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(1L);
        when(fileAssetService.registerOutboundFile(
                        eq("records.json"),
                        any(),
                        eq("FILE_EXPORT"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq("PROCESSED"),
                        any()))
                .thenReturn(fileRecord);

        String filePath =
                service.exportToJSON(tempDir.toString(), "records.json", List.of(Map.of("id", "1", "name", "alice")));

        Path path = Path.of(filePath);
        assertTrue(Files.exists(path));
        String content = Files.readString(path);
        assertTrue(content.contains("\"id\""));
        assertTrue(content.contains("\"alice\""));
        verify(fileAssetService)
                .registerOutboundFile(
                        eq("records.json"),
                        eq(path.toString()),
                        eq("FILE_EXPORT"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq("PROCESSED"),
                        any());
    }

    @Test
    void exportCompressed_shouldCreateZipFile(@TempDir Path tempDir) throws IOException {
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        FileExportService service = new FileExportService(new ObjectMapper(), fileAssetService, fileProcessLogService);
        FileAssetRecord fileRecord = new FileAssetRecord();
        fileRecord.setId(2L);
        when(fileAssetService.registerOutboundFile(
                        eq("archive.zip"),
                        any(),
                        eq("FILE_EXPORT"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq("PROCESSED"),
                        any()))
                .thenReturn(fileRecord);

        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "sample-data");

        String zipPath = service.exportCompressed(tempDir.toString(), "archive.zip", source.toString());
        assertTrue(Files.exists(Path.of(zipPath)));
        assertTrue(Files.size(Path.of(zipPath)) > 0);
        verify(fileAssetService)
                .registerOutboundFile(
                        eq("archive.zip"),
                        eq(zipPath),
                        eq("FILE_EXPORT"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq("PROCESSED"),
                        any());
    }

    @Test
    void exportToJSON_shouldRejectPathEscapingConfiguredBaseDir(@TempDir Path tempDir) {
        FileExportService service = new FileExportService(
                new ObjectMapper(), mock(FileAssetService.class), mock(FileProcessLogService.class));
        Path baseDir = tempDir.resolve("exports");
        ReflectionTestUtils.setField(service, "outputBaseDir", baseDir.toString());

        assertThrows(
                RuntimeException.class, () -> service.exportToJSON("", "../escape.json", List.of(Map.of("id", "1"))));

        assertFalse(Files.exists(tempDir.resolve("escape.json")));
    }
}
