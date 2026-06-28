package com.example.filebatchprocessor.batch.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 固定延迟调度器，负责按任务 ID 管理注册、取消和停机释放。 */
public class FixedDelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(FixedDelayScheduler.class);

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    public FixedDelayScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * 注册固定延迟任务。
     *
     * @param taskId 任务唯一标识
     * @param task 待执行逻辑
     * @param delayMs 两次执行之间的延迟，单位毫秒
     */
    public void scheduleFixedDelay(String taskId, Runnable task, long delayMs) {
        // 同一任务重复注册时先取消旧调度，避免并发执行同一逻辑。
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
     * 取消已注册任务。
     *
     * @param taskId 待取消的任务标识
     */
    public void cancelTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.debug("Cancelled scheduled task: {}", taskId);
        }
    }

    /**
     * 判断任务是否仍在调度表中。
     *
     * @param taskId 待检查的任务标识
     * @return 已注册返回 true，否则返回 false
     */
    public boolean isTaskScheduled(String taskId) {
        return scheduledTasks.containsKey(taskId);
    }

    /** 返回当前已注册任务数量。 */
    public int getScheduledTaskCount() {
        return scheduledTasks.size();
    }

    /** 停止调度器并释放线程资源。 */
    public void shutdown() {
        log.info("Shutting down FixedDelayScheduler...");

        // 先取消业务任务，再关闭线程池，避免停机期间继续触发。
        scheduledTasks.forEach((taskId, future) -> {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        });
        scheduledTasks.clear();

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
