package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.TaskDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskMergeService {

    private static class MergeBucket {
        private Instant createdAt = Instant.now();
        private final List<TaskDefinition> tasks = new ArrayList<>();
    }

    private final Map<String, MergeBucket> buckets = new ConcurrentHashMap<>();

    public synchronized List<TaskDefinition> merge(String key, TaskDefinition incoming, Duration window) {
        if (!incoming.isAllowMerge()) {
            return List.of(incoming);
        }
        MergeBucket bucket = buckets.computeIfAbsent(key, k -> new MergeBucket());
        bucket.tasks.add(incoming);
        if (Duration.between(bucket.createdAt, Instant.now()).compareTo(window) >= 0) {
            buckets.remove(key);
            log.info("Merging {} tasks into one batch for key {}", bucket.tasks.size(), key);
            return new ArrayList<>(bucket.tasks);
        }
        return List.of(incoming);
    }
}

