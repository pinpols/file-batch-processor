package com.example.filebatchprocessor.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class BatchMetrics {

    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public BatchMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void counter(String name, String... tags) {
        counters.computeIfAbsent(key(name, tags), _k -> Counter.builder(name).tags(tags).register(registry)).increment();
    }

    public <T> T time(String name, Supplier<T> supplier, String... tags) {
        Timer timer = timers.computeIfAbsent(key(name, tags), _k -> Timer.builder(name).tags(tags).register(registry));
        return timer.record(supplier);
    }

    public void gauge(String name, Supplier<Number> supplier, String... tags) {
        io.micrometer.core.instrument.Gauge.builder(name, supplier).tags(tags).register(registry);
    }

    private String key(String name, String... tags) {
        if (tags == null || tags.length == 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        for (String t : tags) {
            sb.append('|').append(t);
        }
        return sb.toString();
    }
}
