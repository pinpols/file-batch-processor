package com.example.filebatchprocessor.batch.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixedDelaySchedulerTest {

    @Mock
    private FixedDelayScheduler fixedDelayScheduler;

    private String testTaskId = "test-task";

    @BeforeEach
    void setUp() {
        // 使用反射设置私有字段进行测试
        // 注意：实际字段名是 scheduledTasks，不是 runningTasks
        ReflectionTestUtils.setField(fixedDelayScheduler, "scheduledTasks", 
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>>());
    }

    @AfterEach
    void tearDown() {
        // 清理测试任务，避免测试间相互影响
        if (fixedDelayScheduler != null) {
            fixedDelayScheduler.cancelTask(testTaskId);
        }
    }

    @Test
    void shouldScheduleFixedDelayTask() throws InterruptedException {
        // Given
        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        long delayMs = 1000;

        Runnable testTask = () -> {
            taskExecuted.set(true);
            latch.countDown();
        };

        // When
        fixedDelayScheduler.scheduleFixedDelay(testTaskId, testTask, delayMs);

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should be executed");
        assertTrue(taskExecuted.get(), "Task should be marked as executed");
        assertTrue(fixedDelayScheduler.isTaskScheduled(testTaskId), "Task should be running");
    }

    @Test
    void shouldCancelFixedDelayTask() {
        // Given
        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        Runnable testTask = () -> taskExecuted.set(true);

        fixedDelayScheduler.scheduleFixedDelay(testTaskId, testTask, 5000);

        // When
        fixedDelayScheduler.cancelTask(testTaskId);

        // Then
        assertFalse(fixedDelayScheduler.isTaskScheduled(testTaskId), "Task should be cancelled");
    }

    @Test
    void shouldHandleTaskExecutionDelay() throws InterruptedException {
        // Given
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);
        long delayMs = 500;

        Runnable testTask = () -> {
            executionCount.incrementAndGet();
            latch.countDown();
            try {
                Thread.sleep(100); // 模拟任务执行时间
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // When
        fixedDelayScheduler.scheduleFixedDelay(testTaskId, testTask, delayMs);
        Instant startTime = Instant.now();

        // Then
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Both executions should complete");
        
        // 验证第二次执行在第一次完成后约delayMs时间开始
        Duration actualDelay = Duration.between(startTime, Instant.now()).minusMillis(100); // 减去任务执行时间
        assertTrue(actualDelay.toMillis() >= delayMs - 50, // 允许50ms误差
            "Second execution should start after delay from first completion");
    }

    @Test
    void shouldHandleTaskException() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean exceptionThrown = new AtomicBoolean(false);

        Runnable testTask = () -> {
            latch.countDown();
            if (!exceptionThrown.get()) {
                exceptionThrown.set(true);
                throw new RuntimeException("Test exception");
            }
        };

        // When
        fixedDelayScheduler.scheduleFixedDelay(testTaskId, testTask, 200);

        // Then
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should be executed twice despite exception");
        assertTrue(fixedDelayScheduler.isTaskScheduled(testTaskId), "Task should continue running after exception");
    }

    @Test
    void shouldGetRunningTaskCount() {
        // Given
        assertEquals(0, fixedDelayScheduler.getScheduledTaskCount(), "Should start with no running tasks");

        // When
        fixedDelayScheduler.scheduleFixedDelay("task1", () -> {}, 1000);
        fixedDelayScheduler.scheduleFixedDelay("task2", () -> {}, 1000);

        // Then
        assertEquals(2, fixedDelayScheduler.getScheduledTaskCount(), "Should count running tasks");
    }

    @Test
    void shouldHandleMultipleTasks() throws InterruptedException {
        // Given
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        Runnable task1 = () -> latch1.countDown();
        Runnable task2 = () -> latch2.countDown();

        // When
        fixedDelayScheduler.scheduleFixedDelay("task1", task1, 500);
        fixedDelayScheduler.scheduleFixedDelay("task2", task2, 500);

        // Then
        assertTrue(latch1.await(1, TimeUnit.SECONDS), "Task1 should be executed");
        assertTrue(latch2.await(1, TimeUnit.SECONDS), "Task2 should be executed");
        assertEquals(2, fixedDelayScheduler.getScheduledTaskCount(), "Both tasks should be running");
    }

    @Test
    void shouldHandleTaskReplacement() {
        // Given
        Runnable originalTask = () -> {};
        Runnable newTask = () -> {};

        // When
        fixedDelayScheduler.scheduleFixedDelay(testTaskId, originalTask, 1000);
        assertTrue(fixedDelayScheduler.isTaskScheduled(testTaskId), "Original task should be scheduled");

        fixedDelayScheduler.scheduleFixedDelay(testTaskId, newTask, 500);

        // Then
        assertTrue(fixedDelayScheduler.isTaskScheduled(testTaskId), "New task should be scheduled");
        assertEquals(1, fixedDelayScheduler.getScheduledTaskCount(), "Should still be one task");
    }
}
