package com.example.filebatchprocessor.unit.service.alert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.service.alert.AlertSeverity;
import org.junit.jupiter.api.Test;

class AlertSeverityTest {
    @Test
    void ordinalOrderingInfoLtWarningLtCritical() {
        assertTrue(AlertSeverity.INFO.ordinal() < AlertSeverity.WARNING.ordinal());
        assertTrue(AlertSeverity.WARNING.ordinal() < AlertSeverity.CRITICAL.ordinal());
    }
}
