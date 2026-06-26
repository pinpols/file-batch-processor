package com.example.filebatchprocessor.service.distribution;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SftpConcurrencyLimiter {

    private final ConcurrentMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    private final int maxConcurrentPerHost;

    public SftpConcurrencyLimiter(@Value("${distribution.sftp.max-concurrent-per-host:2}") int maxConcurrentPerHost) {
        this.maxConcurrentPerHost = Math.max(1, maxConcurrentPerHost);
    }

    public Semaphore semaphoreForHost(String host) {
        String key = host == null ? "" : host.trim().toLowerCase();
        return semaphores.computeIfAbsent(key, _k -> new Semaphore(maxConcurrentPerHost));
    }
}
