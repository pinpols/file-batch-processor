package com.example.filebatchprocessor.model;

import java.util.Locale;

/**
 * Unified task execution states for orchestration governance.
 */
public enum TaskExecutionStatus {
    READY,
    RUNNING,
    BLOCKED,
    SUCCESS,
    PARTIAL,
    FAILED,
    SKIPPED;

    public static String normalize(String status) {
        if (status == null || status.isBlank()) {
            return READY.name();
        }
        try {
            return TaskExecutionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))
                    .name();
        } catch (IllegalArgumentException ex) {
            return status.trim().toUpperCase(Locale.ROOT);
        }
    }
}
