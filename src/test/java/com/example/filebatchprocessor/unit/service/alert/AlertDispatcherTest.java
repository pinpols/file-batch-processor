package com.example.filebatchprocessor.unit.service.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.example.filebatchprocessor.service.alert.AlertSender;
import com.example.filebatchprocessor.service.alert.AlertSeverity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AlertDispatcherTest {

    private static AlertSender sender(String name, boolean enabled, AtomicInteger counter, boolean throwOnSend) {
        return new AlertSender() {
            public String channel() {
                return name;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void send(AlertEvent event) {
                counter.incrementAndGet();
                if (throwOnSend) {
                    throw new RuntimeException("boom");
                }
            }
        };
    }

    private static AlertEvent critical() {
        return AlertEvent.of("CODE", AlertSeverity.CRITICAL, "msg", Map.of("k", "v"));
    }

    @Test
    void dispatchesToEnabledSkipsDisabled() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        AlertDispatcher d =
                new AlertDispatcher(List.of(sender("a", true, a, false), sender("b", false, b, false)), "WARNING");
        d.dispatch(critical());
        assertEquals(1, a.get());
        assertEquals(0, b.get());
    }

    @Test
    void oneSenderThrowsOthersStillRun() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        AlertDispatcher d =
                new AlertDispatcher(List.of(sender("a", true, a, true), sender("b", true, b, false)), "WARNING");
        d.dispatch(critical());
        assertEquals(1, a.get());
        assertEquals(1, b.get());
    }

    @Test
    void belowMinSeveritySuppressed() {
        AtomicInteger a = new AtomicInteger();
        AlertDispatcher d = new AlertDispatcher(List.of(sender("a", true, a, false)), "CRITICAL");
        d.dispatch(AlertEvent.of("CODE", AlertSeverity.WARNING, "msg", Map.of()));
        assertEquals(0, a.get());
    }
}
