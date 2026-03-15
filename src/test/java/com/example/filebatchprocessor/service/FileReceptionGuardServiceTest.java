package com.example.filebatchprocessor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileReceptionGuardServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectTemporaryPartialSuffix() throws Exception {
        FileReceptionGuardService service =
                new FileReceptionGuardService(List.of(".part", ".tmp"), false, ".done", false, 1, 0);

        Path file = tempDir.resolve("inbound.csv.part");
        Files.writeString(file, "payload");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.assertReceivable(file.getFileName().toString(), file.toString()));
        assertEquals("Temporary/partial file name is not receivable: inbound.csv.part", ex.getMessage());
    }

    @Test
    void shouldRequireDoneMarkerWhenEnabled() throws Exception {
        FileReceptionGuardService service =
                new FileReceptionGuardService(List.of(".part"), true, ".done", false, 1, 0);

        Path file = tempDir.resolve("inbound.csv");
        Files.writeString(file, "payload");

        var rejected = service.validateForProcessing(file.getFileName().toString(), file.toString());
        assertFalse(rejected.accepted());
        assertEquals("Done marker not found for file: inbound.csv", rejected.reason());

        Files.writeString(Path.of(file.toString() + ".done"), "ok");
        var accepted = service.validateForProcessing(file.getFileName().toString(), file.toString());
        assertTrue(accepted.accepted());
    }
}
