package com.example.filebatchprocessor.service.alert;

import java.time.LocalDateTime;
import java.util.Map;

/** 统一告警事件。各评估器构造它,sender 消费它。 */
public record AlertEvent(
        String source,
        String alertCode,
        AlertSeverity severity,
        String title,
        String message,
        Map<String, Object> data,
        LocalDateTime timestamp) {

    public static AlertEvent of(String alertCode, AlertSeverity severity, String message, Map<String, Object> data) {
        return new AlertEvent(
                "file-batch-processor", alertCode, severity, alertCode, message, data, LocalDateTime.now());
    }
}
