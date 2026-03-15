package com.example.filebatchprocessor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class FileReceptionGuardService {

    private final List<String> temporarySuffixes;
    private final boolean requireDoneMarker;
    private final String doneMarkerSuffix;
    private final boolean stableSizeCheckEnabled;
    private final int stableSizeCheckRounds;
    private final long stableSizeCheckIntervalMs;

    @Autowired
    public FileReceptionGuardService(
            @Value("${batch.file.reception.partial.temp-suffixes:.part,.tmp,.upload}") String temporarySuffixes,
            @Value("${batch.file.reception.partial.require-done-marker:false}") boolean requireDoneMarker,
            @Value("${batch.file.reception.partial.done-marker-suffix:.done}") String doneMarkerSuffix,
            @Value("${batch.file.reception.partial.stable-size-check-enabled:true}") boolean stableSizeCheckEnabled,
            @Value("${batch.file.reception.partial.stable-size-check-rounds:2}") int stableSizeCheckRounds,
            @Value("${batch.file.reception.partial.stable-size-check-interval-ms:200}") long stableSizeCheckIntervalMs) {
        this.temporarySuffixes = Arrays.stream(temporarySuffixes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        this.requireDoneMarker = requireDoneMarker;
        this.doneMarkerSuffix = doneMarkerSuffix == null || doneMarkerSuffix.isBlank() ? ".done" : doneMarkerSuffix.trim();
        this.stableSizeCheckEnabled = stableSizeCheckEnabled;
        this.stableSizeCheckRounds = Math.max(1, stableSizeCheckRounds);
        this.stableSizeCheckIntervalMs = Math.max(0L, stableSizeCheckIntervalMs);
    }

    FileReceptionGuardService(List<String> temporarySuffixes,
                              boolean requireDoneMarker,
                              String doneMarkerSuffix,
                              boolean stableSizeCheckEnabled,
                              int stableSizeCheckRounds,
                              long stableSizeCheckIntervalMs) {
        this.temporarySuffixes = temporarySuffixes;
        this.requireDoneMarker = requireDoneMarker;
        this.doneMarkerSuffix = doneMarkerSuffix;
        this.stableSizeCheckEnabled = stableSizeCheckEnabled;
        this.stableSizeCheckRounds = stableSizeCheckRounds;
        this.stableSizeCheckIntervalMs = stableSizeCheckIntervalMs;
    }

    public static FileReceptionGuardService testingDefaults() {
        return new FileReceptionGuardService(List.of(".part", ".tmp", ".upload"), false, ".done", false, 1, 0);
    }

    public void assertReceivable(String fileName, String filePath) {
        Path path = Path.of(filePath);
        validateTemporaryName(fileName);
        validateDoneMarker(path);
    }

    public ValidationResult validateForProcessing(String fileName, String filePath) {
        Path path = Path.of(filePath);
        try {
            validateTemporaryName(fileName);
            validateDoneMarker(path);
            validateStableSize(path);
            return ValidationResult.success();
        } catch (IllegalArgumentException ex) {
            return ValidationResult.failure(ex.getMessage());
        }
    }

    private void validateTemporaryName(String fileName) {
        String normalized = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        for (String suffix : temporarySuffixes) {
            if (normalized.endsWith(suffix)) {
                throw new IllegalArgumentException("Temporary/partial file name is not receivable: " + fileName);
            }
        }
    }

    private void validateDoneMarker(Path path) {
        if (!requireDoneMarker) {
            return;
        }
        Path doneMarker = Path.of(path.toString() + doneMarkerSuffix);
        if (!Files.exists(doneMarker)) {
            throw new IllegalArgumentException("Done marker not found for file: " + path.getFileName());
        }
    }

    private void validateStableSize(Path path) {
        if (!stableSizeCheckEnabled) {
            return;
        }
        try {
            long previousSize = Files.size(path);
            for (int i = 1; i < stableSizeCheckRounds; i++) {
                if (stableSizeCheckIntervalMs > 0) {
                    Thread.sleep(stableSizeCheckIntervalMs);
                }
                long currentSize = Files.size(path);
                if (currentSize != previousSize) {
                    throw new IllegalArgumentException("File size is still changing: " + path.getFileName());
                }
                previousSize = currentSize;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to inspect file readiness: " + path.getFileName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during file stability check", e);
        }
    }

    public record ValidationResult(boolean accepted, String reason) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
