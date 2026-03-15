package com.example.filebatchprocessor.model;

import java.util.Locale;

public enum FileAssetStatus {
    UPLOADING,
    ARRIVED,
    READY,
    PROCESSING,
    PROCESSED,
    FAILED,
    DISPATCHING,
    DISPATCHED,
    ARCHIVED;

    public static FileAssetStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("File asset status must not be blank");
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
