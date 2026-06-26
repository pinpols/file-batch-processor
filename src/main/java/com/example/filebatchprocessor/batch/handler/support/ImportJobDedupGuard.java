package com.example.filebatchprocessor.batch.handler.support;

import com.example.filebatchprocessor.service.ExecutionDedupService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class ImportJobDedupGuard {

    private final ExecutionDedupService executionDedupService;
    private final ConcurrentMap<String, Instant> recentExecutions = new ConcurrentHashMap<>();

    public ImportJobDedupGuard(ExecutionDedupService executionDedupService) {
        this.executionDedupService = executionDedupService;
    }

    public boolean isDuplicate(String dedupKey, String batchDate, String rerunId, long dedupWindowSeconds) {
        boolean acquired = executionDedupService.tryAcquire(dedupKey, batchDate, rerunId, dedupWindowSeconds);
        if (!acquired) {
            return true;
        }
        Instant now = Instant.now();
        Instant last = recentExecutions.get(dedupKey);
        if (last != null && Duration.between(last, now).getSeconds() < dedupWindowSeconds) {
            return true;
        }
        recentExecutions.put(dedupKey, now);
        return false;
    }
}
