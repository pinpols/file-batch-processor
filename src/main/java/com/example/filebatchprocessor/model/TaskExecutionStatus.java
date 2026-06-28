package com.example.filebatchprocessor.model;

import java.util.Locale;

/** 编排治理统一使用的任务执行状态。 */
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
