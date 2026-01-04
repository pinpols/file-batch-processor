package com.example.filebatchprocessor.scheduler;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalCacheService {

    private static class Entry {
        private final Object value;
        private final Instant expireAt;

        Entry(Object value, Instant expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public void put(String key, Object value, Duration ttl) {
        cache.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    public Object get(String key) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt.isBefore(Instant.now())) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    public void evict(String key) {
        cache.remove(key);
    }
}

