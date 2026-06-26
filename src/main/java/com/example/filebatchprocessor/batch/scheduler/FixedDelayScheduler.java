package com.example.filebatchprocessor.batch.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced fixed delay scheduler that supports task management and graceful shutdown.
 */
public class FixedDelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(FixedDelayScheduler.class);

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    public FixedDelayScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Schedule a task to run with fixed delay between executions.
     *
     * @param taskId unique identifier for the task
     * @param task the task to execute
     * @param delayMs delay between task executions in milliseconds
     */
    public void scheduleFixedDelay(String taskId, Runnable task, long delayMs) {
        // Cancel existing task if present
        cancelTask(taskId);

        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("Error executing scheduled task: {}", taskId, e);
                    }
                },
                delayMs,
                delayMs,
                TimeUnit.MILLISECONDS);

        scheduledTasks.put(taskId, future);
        log.debug("Scheduled fixed delay task: {} with delay: {}ms", taskId, delayMs);
    }

    /**
     * Cancel a scheduled task.
     *
     * @param taskId the task identifier to cancel
     */
    public void cancelTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.debug("Cancelled scheduled task: {}", taskId);
        }
    }

    /**
     * Check if a task is currently scheduled.
     *
     * @param taskId the task identifier to check
     * @return true if the task is scheduled, false otherwise
     */
    public boolean isTaskScheduled(String taskId) {
        return scheduledTasks.containsKey(taskId);
    }

    /**
     * Get the number of currently scheduled tasks.
     *
     * @return the number of scheduled tasks
     */
    public int getScheduledTaskCount() {
        return scheduledTasks.size();
    }

    /**
     * Shutdown the scheduler gracefully.
     */
    public void shutdown() {
        log.info("Shutting down FixedDelayScheduler...");

        // Cancel all scheduled tasks
        scheduledTasks.forEach((taskId, future) -> {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        });
        scheduledTasks.clear();

        // Shutdown the executor
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Scheduler did not terminate gracefully, forcing shutdown");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for scheduler termination");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("FixedDelayScheduler shutdown completed");
    }
}
