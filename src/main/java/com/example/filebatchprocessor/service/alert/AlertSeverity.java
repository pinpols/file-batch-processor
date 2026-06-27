package com.example.filebatchprocessor.service.alert;

/** 告警严重度。ordinal 升序(INFO<WARNING<CRITICAL)用于 min-severity 过滤。 */
public enum AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}
