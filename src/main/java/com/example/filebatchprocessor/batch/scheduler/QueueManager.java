package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;

public class QueueManager {

    private final int maxQueueSize;
    private final PriorityBlockingQueue<OrchestrationTaskDefinition> queue =
            new PriorityBlockingQueue<>(11, (a, b) -> Integer.compare(b.getPriority().weight(), a.getPriority().weight()));
    private final ConcurrentMap<String, Instant> enqueuedAtMap = new ConcurrentHashMap<>();

    public QueueManager(int maxQueueSize) {
        this.maxQueueSize = Math.max(10, maxQueueSize);
    }

    public boolean offer(OrchestrationTaskDefinition task, String runKey) {
        if (queue.size() >= maxQueueSize) {
            return false;
        }
        enqueuedAtMap.putIfAbsent(runKey, Instant.now());
        queue.offer(task);
        return true;
    }

    void requeue(OrchestrationTaskDefinition task) {
        queue.offer(task);
    }

    public OrchestrationTaskDefinition poll() {
        return queue.poll();
    }

    int size() {
        return queue.size();
    }

    public void removeRunKey(String runKey) {
        enqueuedAtMap.remove(runKey);
    }

    public Instant enqueuedAt(String runKey) {
        return enqueuedAtMap.get(runKey);
    }

    Instant enqueuedAtOrNow(String runKey) {
        return enqueuedAtMap.getOrDefault(runKey, Instant.now());
    }

    public boolean isQueueWaitTimeout(String runKey, long maxWaitMs) {
        Instant enqueuedAt = enqueuedAtMap.get(runKey);
        if (enqueuedAt == null) {
            return false;
        }
        return Duration.between(enqueuedAt, Instant.now()).toMillis() > Math.max(1000, maxWaitMs);
    }

    boolean isElapsed(String runKey, long windowMs) {
        Instant enqueuedAt = enqueuedAtMap.get(runKey);
        if (enqueuedAt == null) {
            return false;
        }
        return Duration.between(enqueuedAt, Instant.now()).toMillis() > Math.max(1000, windowMs);
    }

    long waitedMs(String runKey) {
        Instant enqueuedAt = enqueuedAtMap.get(runKey);
        if (enqueuedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(enqueuedAt, Instant.now()).toMillis());
    }

    Map<String, Instant> snapshotEnqueuedMap() {
        return Map.copyOf(enqueuedAtMap);
    }
}
