package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.scheduler.SchedulerConcurrencyLimiter;
import com.example.filebatchprocessor.batch.scheduler.TargetSystemCircuitBreaker;
import com.example.filebatchprocessor.batch.scheduler.TaskGraphManager;
import com.example.filebatchprocessor.batch.scheduler.TaskMergeService;
import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.scheduler.LocalCacheService;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskTrigger;
import com.example.filebatchprocessor.service.*;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class FixedDelayBackpressurePolicyTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldThrottleRescheduleFrequencyWhenTaskKeepsFailing() throws Exception {
        Scheduler quartzScheduler = mock(Scheduler.class);
        when(quartzScheduler.checkExists(any(org.quartz.JobKey.class))).thenReturn(false);
        when(quartzScheduler.scheduleJob(any(Trigger.class)))
                .thenAnswer(invocation -> ((Trigger) invocation.getArgument(0)).getStartTime());

        TaskSchedulerService service = new TaskSchedulerService(
                mock(JobOperator.class),
                mock(BatchJobResolver.class),
                mock(TaskGraphManager.class),
                mock(LocalCacheService.class),
                mock(TaskMergeService.class),
                mock(ExecutionDedupService.class),
                mock(TaskExecutionStateService.class),
                mock(TaskExecutionStateRepository.class),
                mock(SchedulerLeaderService.class),
                mock(SchedulerQueueService.class),
                mock(TaskExecutionAuditService.class),
                mock(JobInstanceService.class),
                mock(DlqRecordRepository.class),
                quartzScheduler,
                mock(ThreadPoolTaskExecutor.class),
                mock(JdbcTemplate.class),
                mock(SchedulerConcurrencyLimiter.class),
                mock(TargetSystemCircuitBreaker.class),
                mock(BatchMetrics.class),
                2000,
                4,
                300_000,
                1_800_000,
                1,
                600_000,
                86_400_000,
                60_000,
                0.0,
                3,
                1500,
                5000,
                2000,
                60,
                2.0,
                300_000,
                60);

        OrchestrationTaskDefinition def = OrchestrationTaskDefinition.builder()
                .id("fd-backpressure-task")
                .jobName("fileDistributionJob")
                .enabled(true)
                .parameters(Map.of("batchDate", "2026-03-14"))
                .trigger(OrchestrationTaskTrigger.builder()
                        .type(com.example.filebatchprocessor.batch.scheduler.TriggerType.FIXED_DELAY)
                        .fixedDelayMs(1000L)
                        .build())
                .build();

        Method scheduleFixedDelay = TaskSchedulerService.class.getDeclaredMethod(
                "scheduleFixedDelayOnce", OrchestrationTaskDefinition.class, Instant.class);
        scheduleFixedDelay.setAccessible(true);
        Method recordOutcome = TaskSchedulerService.class.getDeclaredMethod(
                "recordFixedDelayOutcome", OrchestrationTaskDefinition.class, boolean.class);
        recordOutcome.setAccessible(true);

        scheduleFixedDelay.invoke(service, def, Instant.now());
        Trigger first = captureLastScheduledTrigger(quartzScheduler);
        Instant firstAt = first.getStartTime().toInstant();

        // Two continuous failures -> failureStreak=2 -> base 1000 * 2^2 = 4000ms backoff.
        recordOutcome.invoke(service, def, false);
        recordOutcome.invoke(service, def, false);

        scheduleFixedDelay.invoke(service, def, Instant.now());
        Trigger second = captureLastScheduledTrigger(quartzScheduler);
        Instant secondAt = second.getStartTime().toInstant();

        long gapMs = Duration.between(firstAt, secondAt).toMillis();
        assertTrue(gapMs >= 2500, "fixed-delay should apply backoff/min-interval, actual gapMs=" + gapMs);
    }

    private Trigger captureLastScheduledTrigger(Scheduler scheduler) throws Exception {
        var triggerCaptor = org.mockito.ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler, atLeastOnce()).scheduleJob(triggerCaptor.capture());
        return triggerCaptor.getAllValues().get(triggerCaptor.getAllValues().size() - 1);
    }
}
