package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.config.SchedulerConcurrencyProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

@Component
public class SchedulerConcurrencyLimiter {

    private final SchedulerConcurrencyProperties properties;
    private final ConcurrentMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    public SchedulerConcurrencyLimiter(SchedulerConcurrencyProperties properties) {
        this.properties = properties;
    }

    public Permit tryAcquire(String key) {
        Integer max = properties.getMaxConcurrentByKey().get(key);
        if (max == null || max <= 0) {
            return Permit.NOOP;
        }
        Semaphore semaphore = semaphores.computeIfAbsent(key, _k -> new Semaphore(Math.max(1, max)));
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            return null;
        }
        return () -> semaphore.release();
    }

    @FunctionalInterface
    public interface Permit {
        void release();

        Permit NOOP = () -> {
        };
    }
}
