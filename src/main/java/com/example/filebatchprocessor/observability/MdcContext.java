package com.example.filebatchprocessor.observability;

import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.MDC;

/**
 * Utility for safe MDC context management, especially for batch job/task execution.
 */
public final class MdcContext {

    private MdcContext() {}

    /**
     * Run within MDC context, automatically clearing after execution.
     */
    public static <T> T withContext(Map<String, String> context, Supplier<T> action) {
        if (context == null || context.isEmpty()) {
            return action.get();
        }
        try {
            context.forEach(MDC::put);
            return action.get();
        } finally {
            context.keySet().forEach(MDC::remove);
        }
    }

    /**
     * Run within MDC context, automatically clearing after execution (Runnable).
     */
    public static void withContext(Map<String, String> context, Runnable action) {
        withContext(context, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Put a single key-value into MDC.
     */
    public static void put(String key, String value) {
        if (key != null && value != null) {
            MDC.put(key, value);
        }
    }

    /**
     * Remove a key from MDC.
     */
    public static void remove(String key) {
        if (key != null) {
            MDC.remove(key);
        }
    }

    /**
     * Clear all MDC.
     */
    public static void clear() {
        MDC.clear();
    }
}
