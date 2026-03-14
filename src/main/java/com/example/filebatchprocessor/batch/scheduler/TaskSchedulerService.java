package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.observability.MdcContext;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.scheduler.LocalCacheService;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.ExecutionDedupService;
import com.example.filebatchprocessor.service.SchedulerLeaderService;
import com.example.filebatchprocessor.service.SchedulerQueueService;
import com.example.filebatchprocessor.service.TaskExecutionAuditService;
import com.example.filebatchprocessor.service.TaskExecutionStateService;
import com.example.filebatchprocessor.exception.ErrorCodeClassifier;
import com.example.filebatchprocessor.util.IdempotencyKeyBuilder;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskSchedulerService {

    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TaskGraphManager taskGraphManager;
    private final TaskMergeService taskMergeService;
    private final ExecutionDedupService executionDedupService;
    private final TaskExecutionStateService taskExecutionStateService;
    private final TaskExecutionStateRepository taskExecutionStateRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final ThreadPoolTaskExecutor batchTaskExecutor;
    private final Scheduler quartzScheduler;

    private final BatchMetrics batchMetrics;

    private final SchedulerLeaderService schedulerLeaderService;
    private final SchedulerQueueService schedulerQueueService;
    private final TaskExecutionAuditService taskExecutionAuditService;

    private final QueueManager queueManager;
    private final DependencyResolver dependencyResolver;
    private final RetryPolicy retryPolicy;
    private final LaunchExecutor launchExecutor;
    private final JdbcTemplate jdbcTemplate;

    private final SchedulerConcurrencyLimiter schedulerConcurrencyLimiter;
    private final TargetSystemCircuitBreaker circuitBreaker;

    private final long defaultMaxQueueWaitMs;
    private final long defaultDependencyTimeoutMs;
    private final long defaultRerunWindowMs;
    private final int defaultMaxAttempts;
    private final long dedupWindowSeconds;
    private final int queueBackpressureThreshold;
    private final long queueBackpressureDelayMs;
    private final long fixedDelayMinIntervalMs;
    private final int fixedDelayMaxRequeuePerMinute;
    private final double fixedDelayFailureBackoffMultiplier;
    private final long fixedDelayMaxBackoffMs;

    private final ConcurrentMap<String, Instant> dedupMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FixedDelayBackoffState> fixedDelayState = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> queueSlaBreached = new ConcurrentHashMap<>();
    private final Object quartzTriggerWriteLock = new Object();

    public TaskSchedulerService(@Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
                                ObjectProvider<Map<String, Job>> jobsProvider,
                                TaskGraphManager taskGraphManager,
                                LocalCacheService localCacheService,
                                TaskMergeService taskMergeService,
                                ExecutionDedupService executionDedupService,
                                TaskExecutionStateService taskExecutionStateService,
                                TaskExecutionStateRepository taskExecutionStateRepository,
                                SchedulerLeaderService schedulerLeaderService,
                                SchedulerQueueService schedulerQueueService,
                                TaskExecutionAuditService taskExecutionAuditService,
                                DlqRecordRepository dlqRecordRepository,
                                Scheduler quartzScheduler,
                                ThreadPoolTaskExecutor batchTaskExecutor,
                                JdbcTemplate jdbcTemplate,
                                SchedulerConcurrencyLimiter schedulerConcurrencyLimiter,
                                TargetSystemCircuitBreaker circuitBreaker,
                                BatchMetrics batchMetrics,
                                @Value("${orchestration.scheduler.max-queue-size:2000}") int maxQueueSize,
                                @Value("${orchestration.scheduler.max-concurrent-launches:4}") int maxConcurrentLaunches,
                                @Value("${orchestration.scheduler.default-max-queue-wait-ms:300000}") long defaultMaxQueueWaitMs,
                                @Value("${orchestration.scheduler.default-timeout-ms:1800000}") long defaultTimeoutMs,
                                @Value("${orchestration.scheduler.default-dynamic-shard-max:1}") int defaultDynamicShardMax,
                                @Value("${orchestration.scheduler.default-dependency-timeout-ms:600000}") long defaultDependencyTimeoutMs,
                                @Value("${orchestration.scheduler.default-rerun-window-ms:86400000}") long defaultRerunWindowMs,
                                @Value("${orchestration.scheduler.default-retry-backoff-ms:60000}") long defaultRetryBackoffMs,
                                @Value("${orchestration.scheduler.default-retry-jitter-ratio:0}") double defaultRetryJitterRatio,
                                @Value("${orchestration.scheduler.default-max-attempts:3}") int defaultMaxAttempts,
                                @Value("${orchestration.scheduler.backpressure-threshold:1500}") int queueBackpressureThreshold,
                                @Value("${orchestration.scheduler.backpressure-delay-ms:5000}") long queueBackpressureDelayMs,
                                @Value("${orchestration.scheduler.fixed-delay.min-requeue-interval-ms:2000}") long fixedDelayMinIntervalMs,
                                @Value("${orchestration.scheduler.fixed-delay.max-requeue-per-minute:60}") int fixedDelayMaxRequeuePerMinute,
                                @Value("${orchestration.scheduler.fixed-delay.failure-backoff-multiplier:2.0}") double fixedDelayFailureBackoffMultiplier,
                                @Value("${orchestration.scheduler.fixed-delay.max-backoff-ms:300000}") long fixedDelayMaxBackoffMs,
                                @Value("${batch.dedup.window.seconds:60}") long dedupWindowSeconds) {
        this.taskGraphManager = taskGraphManager;
        this.taskMergeService = taskMergeService;
        this.executionDedupService = executionDedupService;
        this.taskExecutionStateService = taskExecutionStateService;
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.schedulerLeaderService = schedulerLeaderService;
        this.schedulerQueueService = schedulerQueueService;
        this.taskExecutionAuditService = taskExecutionAuditService;
        this.dlqRecordRepository = dlqRecordRepository;
        this.quartzScheduler = quartzScheduler;
        this.batchTaskExecutor = batchTaskExecutor;
        this.jdbcTemplate = jdbcTemplate;
        this.schedulerConcurrencyLimiter = schedulerConcurrencyLimiter;
        this.circuitBreaker = circuitBreaker;
        this.batchMetrics = batchMetrics;

        this.defaultMaxQueueWaitMs = Math.max(1000, defaultMaxQueueWaitMs);
        this.defaultDependencyTimeoutMs = Math.max(1000, defaultDependencyTimeoutMs);
        this.defaultRerunWindowMs = Math.max(1000, defaultRerunWindowMs);
        this.defaultMaxAttempts = Math.max(1, defaultMaxAttempts);
        this.dedupWindowSeconds = Math.max(1, dedupWindowSeconds);
        this.queueBackpressureThreshold = Math.max(10, queueBackpressureThreshold);
        this.queueBackpressureDelayMs = Math.max(1000L, queueBackpressureDelayMs);
        this.fixedDelayMinIntervalMs = Math.max(500L, fixedDelayMinIntervalMs);
        this.fixedDelayMaxRequeuePerMinute = Math.max(1, fixedDelayMaxRequeuePerMinute);
        this.fixedDelayFailureBackoffMultiplier = Math.max(1.0, fixedDelayFailureBackoffMultiplier);
        this.fixedDelayMaxBackoffMs = Math.max(1000L, fixedDelayMaxBackoffMs);

        Semaphore launchPermits = new Semaphore(Math.max(1, maxConcurrentLaunches));
        this.queueManager = new QueueManager(maxQueueSize);
        this.dependencyResolver = new DependencyResolver(taskExecutionStateRepository);
        this.retryPolicy = new RetryPolicy(defaultMaxAttempts, defaultRetryBackoffMs, defaultRetryJitterRatio);
        this.launchExecutor = new LaunchExecutor(
                jobLauncher,
                jobsProvider,
                launchPermits,
                defaultDynamicShardMax,
                defaultTimeoutMs
        );
    }

    public void register(OrchestrationTaskDefinition definition) {
        taskGraphManager.register(definition);
        if (!schedulerLeaderService.isLeader()) {
            log.info("Skip scheduling task because current instance is not leader: taskId={}", definition.getId());
            return;
        }
        schedule(definition);
    }

    public void enqueueByTaskId(String taskId) {
        OrchestrationTaskDefinition definition = taskGraphManager.get(taskId);
        if (definition == null) {
            log.warn("Skip enqueue because task is not registered: taskId={}", taskId);
            return;
        }
        enqueue(definition);
    }

    private void schedule(OrchestrationTaskDefinition definition) {
        if (!schedulerLeaderService.isLeader()) {
            return;
        }
        if (definition.getTrigger() == null) {
            enqueue(definition);
            return;
        }
        try {
            JobDetail jobDetail = ensureQuartzJob(definition);
            switch (definition.getTrigger().getType()) {
                case CRON:
                    String cron = definition.getTrigger().getCron();
                    Trigger cronTrigger = TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey(definition, "cron"))
                            .forJob(jobDetail)
                            .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                                    .withMisfireHandlingInstructionFireAndProceed())
                            .build();
                    upsertTrigger(cronTrigger);
                    break;
                case FIXED_RATE:
                    long fixedRateMs = definition.getTrigger().getFixedRateMs() == null
                            ? 1000L
                            : definition.getTrigger().getFixedRateMs();
                    // Delay the first fire slightly to avoid startup-time race between trigger scan and DB writes.
                    java.util.Date firstFireAt = java.util.Date.from(Instant.now().plusMillis(2000L));
                    Trigger fixedRateTrigger = TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey(definition, "fixed-rate"))
                            .forJob(jobDetail)
                            .startAt(firstFireAt)
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                    .withIntervalInMilliseconds(Math.max(1000L, fixedRateMs))
                                    .repeatForever())
                            .build();
                    upsertTrigger(fixedRateTrigger);
                    break;
                case FIXED_DELAY:
                    scheduleFixedDelayOnce(definition, Instant.now());
                    break;
                case ONE_TIME:
                    Instant at = definition.getTrigger().getOneTimeAt();
                    if (at == null) {
                        enqueue(definition);
                        return;
                    }
                    Trigger oneTimeTrigger = TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey(definition, "one-time"))
                            .forJob(jobDetail)
                            .startAt(java.util.Date.from(at))
                            .build();
                    upsertTrigger(oneTimeTrigger);
                    break;
                default:
                    enqueue(definition);
            }
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to schedule task by Quartz: " + definition.getId(), ex);
        }
    }

    private JobDetail ensureQuartzJob(OrchestrationTaskDefinition definition) throws SchedulerException {
        JobKey key = jobKey(definition);
        if (quartzScheduler.checkExists(key)) {
            return quartzScheduler.getJobDetail(key);
        }
        JobDetail detail = JobBuilder.newJob(QuartzTaskDispatchJob.class)
                .withIdentity(key)
                .usingJobData(QuartzTaskDispatchJob.TASK_ID, definition.getId())
                .storeDurably(true)
                .build();
        quartzScheduler.addJob(detail, true);
        return detail;
    }

    private void upsertTrigger(Trigger trigger) throws SchedulerException {
        TriggerKey key = trigger.getKey();
        synchronized (quartzTriggerWriteLock) {
            try {
                if (quartzScheduler.checkExists(key)) {
                    quartzScheduler.unscheduleJob(key);
                }
                quartzScheduler.scheduleJob(trigger);
                ensureTriggerSubtypeRow(trigger);
            } catch (Exception e) {
                log.warn("Failed to upsert trigger {}, retrying with force replace: {}", key, e.getMessage());
                try {
                    quartzScheduler.unscheduleJob(key);
                } catch (Exception ignored) {}
                quartzScheduler.scheduleJob(trigger);
                ensureTriggerSubtypeRow(trigger);
            }
        }
    }

    private void ensureTriggerSubtypeRow(Trigger trigger) throws SchedulerException {
        String schedName = quartzScheduler.getSchedulerName();
        String triggerName = trigger.getKey().getName();
        String triggerGroup = trigger.getKey().getGroup();
        Integer triggerRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM qrtz_triggers WHERE sched_name=? AND trigger_name=? AND trigger_group=?",
                Integer.class,
                schedName,
                triggerName,
                triggerGroup
        );
        if (triggerRowCount == null || triggerRowCount == 0) {
            log.debug("Skip subtype-row repair because base qrtz_triggers row is missing: {}", trigger.getKey());
            return;
        }

        if (trigger instanceof org.quartz.SimpleTrigger simple) {
            jdbcTemplate.update(
                    "DELETE FROM qrtz_cron_triggers WHERE sched_name=? AND trigger_name=? AND trigger_group=?",
                    schedName, triggerName, triggerGroup
            );
            int inserted = jdbcTemplate.update(
                    "INSERT INTO qrtz_simple_triggers " +
                            "(sched_name, trigger_name, trigger_group, repeat_count, repeat_interval, times_triggered) " +
                            "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    schedName,
                    triggerName,
                    triggerGroup,
                    simple.getRepeatCount(),
                    simple.getRepeatInterval(),
                    Math.max(0, simple.getTimesTriggered())
            );
            if (inserted > 0) {
                log.warn("Repaired missing qrtz_simple_triggers row for trigger {}", trigger.getKey());
            }
            return;
        }

        if (trigger instanceof org.quartz.CronTrigger cron) {
            jdbcTemplate.update(
                    "DELETE FROM qrtz_simple_triggers WHERE sched_name=? AND trigger_name=? AND trigger_group=?",
                    schedName, triggerName, triggerGroup
            );
            int inserted = jdbcTemplate.update(
                    "INSERT INTO qrtz_cron_triggers " +
                            "(sched_name, trigger_name, trigger_group, cron_expression, time_zone_id) " +
                            "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    schedName,
                    triggerName,
                    triggerGroup,
                    cron.getCronExpression(),
                    cron.getTimeZone() == null ? null : cron.getTimeZone().getID()
            );
            if (inserted > 0) {
                log.warn("Repaired missing qrtz_cron_triggers row for trigger {}", trigger.getKey());
            }
        }
    }

    private void enqueue(OrchestrationTaskDefinition definition) {
        if (!schedulerLeaderService.isLeader()) {
            return;
        }

        // Check if task is enabled
        if (!Boolean.TRUE.equals(definition.getEnabled())) {
            log.info("Task is disabled, skip enqueue: taskId={}", definition.getId());
            return;
        }

        batchMetrics.counter("scheduler_enqueue_total", "job", String.valueOf(definition.getJobName()));
        if (shouldBackpressure()) {
            batchMetrics.counter("scheduler_backpressure_total", "job", String.valueOf(definition.getJobName()));
            scheduleOneShotEnqueue(definition, Instant.now().plusMillis(queueBackpressureDelayMs), "backpressure");
            return;
        }
        String runKey = taskRunKey(definition);

        String batchDate = resolveBatchDate(definition);
        String rerunId = definition.getParameters().getOrDefault("rerunId", "");
        boolean enqueued = schedulerQueueService.tryEnqueue(runKey, definition.getId(), batchDate, rerunId);
        if (!enqueued) {
            log.info("Skip enqueue because runKey already enqueued: runKey={} taskId={}", runKey, definition.getId());
            batchMetrics.counter("scheduler_enqueue_dedup_total", "job", String.valueOf(definition.getJobName()));
            return;
        }

        if (!queueManager.offer(definition, runKey)) {
            schedulerQueueService.dequeue(runKey);
            persistSchedulerDlq(definition, "Queue is full, task dropped");
            if (definition.getTrigger() != null && definition.getTrigger().getType() == TriggerType.FIXED_DELAY) {
                long delayMs = definition.getTrigger().getFixedDelayMs() == null ? 1000L : Math.max(1000L, definition.getTrigger().getFixedDelayMs());
                scheduleFixedDelayOnce(definition, Instant.now().plusMillis(delayMs));
            }
            return;
        }
        upsertState(definition, TaskExecutionStatus.READY.name(), null, false, null);
        taskExecutionAuditService.log(
                definition.getId(),
                definition.getJobName(),
                resolveBatchDate(definition),
                runKey,
                "ENQUEUE",
                TaskExecutionStatus.READY.name(),
                null,
                definition.getParameters()
        );
        batchTaskExecutor.submit(this::drainQueue);
    }

    private void drainQueue() {
        if (!schedulerLeaderService.isLeader()) {
            return;
        }
        OrchestrationTaskDefinition def;
        while ((def = queueManager.poll()) != null) {
            String runKey = taskRunKey(def);
            long waitedMs = queueManager.waitedMs(runKey);
            if (def.getSlaMaxQueueDelayMs() != null
                    && def.getSlaMaxQueueDelayMs() > 0
                    && waitedMs > def.getSlaMaxQueueDelayMs()
                    && queueSlaBreached.putIfAbsent(runKey, Boolean.TRUE) == null) {
                batchMetrics.counter("scheduler_queue_sla_breach_total", "job", String.valueOf(def.getJobName()));
                taskExecutionAuditService.log(def.getId(), def.getJobName(), resolveBatchDate(def), runKey,
                        "QUEUE_SLA_BREACH", TaskExecutionStatus.BLOCKED.name(),
                        "waitedMs=" + waitedMs, def.getParameters());
            }
            if (queueManager.isQueueWaitTimeout(runKey, resolveMaxQueueWaitMs(def))) {
                markFailed(def, "Queue wait timeout exceeded");
                completeRun(def, runKey);
                continue;
            }

            String batchDate = resolveBatchDate(def);
            DependencyResolver.DependencyState dependencyState = dependencyResolver.resolve(def, batchDate, resolveDependencyTimeoutMs(def), waitedMs);
            if (dependencyState == DependencyResolver.DependencyState.FAILED) {
                markFailed(def, "Blocked by failed dependency");
                completeRun(def, runKey);
                continue;
            }
            if (dependencyState == DependencyResolver.DependencyState.SKIPPED) {
                upsertState(def, TaskExecutionStatus.SKIPPED.name(), "Dependency failed and on_failure_action=SKIP", false, null);
                completeRun(def, runKey);
                continue;
            }
            if (dependencyState == DependencyResolver.DependencyState.WAITING) {
                if (queueManager.isElapsed(runKey, resolveDependencyTimeoutMs(def))) {
                    markFailed(def, "Dependency wait timeout exceeded");
                    completeRun(def, runKey);
                    continue;
                }
                upsertState(def, TaskExecutionStatus.BLOCKED.name(), null, false, null);
                queueManager.requeue(def);
                continue;
            }

            if (isDuplicate(def, batchDate)) {
                log.info("Skip duplicate task {}", def.getId());
                completeRun(def, runKey);
                continue;
            }

            if (queueManager.isElapsed(runKey, resolveRerunWindowMs(def))) {
                markFailed(def, "Rerun window expired");
                completeRun(def, runKey);
                continue;
            }

            String mergeKey = buildMergeKey(def, batchDate);
            List<OrchestrationTaskDefinition> merged = taskMergeService.merge(mergeKey, def, Duration.ofSeconds(10));
            for (OrchestrationTaskDefinition task : merged) {
                String limitKey = buildConcurrencyKey(task);
                SchedulerConcurrencyLimiter.Permit permit = schedulerConcurrencyLimiter.tryAcquire(limitKey);
                if (permit == null) {
                    queueManager.requeue(task);
                    batchMetrics.counter("scheduler_concurrency_limited_total", "key", limitKey);
                    continue;
                }

                String targetSystem = task.getParameters().get("targetSystem");
                if (targetSystem != null && !circuitBreaker.tryAcquire(targetSystem)) {
                    queueManager.requeue(task);
                    batchMetrics.counter("scheduler_circuit_rejected_total", "target", targetSystem);
                    permit.release();
                    continue;
                }

                try {
                    Map<String, String> mdcCtx = new HashMap<>();
                    mdcCtx.put("task_id", String.valueOf(task.getId()));
                    mdcCtx.put("job_name", task.getJobName());
                    String batchDateParam = task.getParameters().get("batchDate");
                    mdcCtx.put("batch_date", batchDateParam != null ? batchDateParam : "");
                    String executionIdParam = task.getParameters().get("executionId");
                        mdcCtx.put("execution_id", executionIdParam != null ? executionIdParam : "");
                    if (targetSystem != null) {
                        mdcCtx.put("target_system", targetSystem);
                    }
                    MdcContext.withContext(mdcCtx, () -> {
                        upsertState(task, TaskExecutionStatus.RUNNING.name(), null, false, null);
                        LaunchExecutor.LaunchResult launchResult = launchExecutor.launch(task, resolveBatchDate(task), queueManager.size());
                        if (launchResult.isShouldReschedule()) {
                            queueManager.requeue(task);
                            batchMetrics.counter("scheduler_launch_reschedule_total", "job", String.valueOf(task.getJobName()));
                            return;
                        }
                        if (!launchResult.isSuccess()) {
                            batchMetrics.counter("scheduler_launch_failed_total", "job", String.valueOf(task.getJobName()));
                            int attempt = getCurrentAttempt(task);
                            RetryPolicy.FailureClass failureClass = retryPolicy.classify(launchResult.getReason());
                            recordFixedDelayOutcome(task, false);
                            if (failureClass == RetryPolicy.FailureClass.SKIPPABLE) {
                                upsertState(task, TaskExecutionStatus.SKIPPED.name(), launchResult.getReason(), true, null);
                                batchMetrics.counter("scheduler_launch_skipped_total", "job", String.valueOf(task.getJobName()));
                                taskExecutionAuditService.log(task.getId(), task.getJobName(), resolveBatchDate(task), runKey,
                                        "LAUNCH", TaskExecutionStatus.SKIPPED.name(), launchResult.getReason(), task.getParameters());
                            } else if (retryPolicy.allowRetry(task, attempt, launchResult.getReason())) {
                                scheduleRetry(task, launchResult.getReason());
                                taskExecutionAuditService.log(task.getId(), task.getJobName(), resolveBatchDate(task), runKey,
                                        "RETRY_SCHEDULED", TaskExecutionStatus.READY.name(), launchResult.getReason(), task.getParameters());
                            } else {
                                markFailed(task, launchResult.getReason());
                                taskExecutionAuditService.log(task.getId(), task.getJobName(), resolveBatchDate(task), runKey,
                                        "LAUNCH", TaskExecutionStatus.FAILED.name(), launchResult.getReason(), task.getParameters());
                            }
                            if (targetSystem != null) {
                                circuitBreaker.recordResult(targetSystem, false);
                            }
                            return;
                        }
                        if (launchResult.isPartial()) {
                            upsertState(task, TaskExecutionStatus.PARTIAL.name(), launchResult.getReason(), false, null);
                            batchMetrics.counter("scheduler_launch_partial_total", "job", String.valueOf(task.getJobName()));
                            recordFixedDelayOutcome(task, false);
                            taskExecutionAuditService.log(task.getId(), task.getJobName(), resolveBatchDate(task), runKey,
                                    "LAUNCH", TaskExecutionStatus.PARTIAL.name(), launchResult.getReason(), task.getParameters());
                        } else {
                            upsertState(task, TaskExecutionStatus.SUCCESS.name(), null, false, null);
                            batchMetrics.counter("scheduler_launch_success_total", "job", String.valueOf(task.getJobName()));
                            recordFixedDelayOutcome(task, true);
                            taskExecutionAuditService.log(task.getId(), task.getJobName(), resolveBatchDate(task), runKey,
                                    "LAUNCH", TaskExecutionStatus.SUCCESS.name(), null, task.getParameters());
                        }
                        if (targetSystem != null) {
                            circuitBreaker.recordResult(targetSystem, true);
                        }
                    });
                } finally {
                    permit.release();
                }
            }
            completeRun(def, runKey);
        }
    }

    private String buildConcurrencyKey(OrchestrationTaskDefinition def) {
        String targetSystem = def.getParameters().get("targetSystem");
        if (targetSystem != null && !targetSystem.isBlank()) {
            return def.getJobName() + ":" + targetSystem;
        }
        return def.getJobName();
    }

    private boolean isDuplicate(OrchestrationTaskDefinition def, String batchDate) {
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        String dedupValue = IdempotencyKeyBuilder.forTask(def, batchDate, rerunId);
        String key = dedupValue;
        boolean acquired = executionDedupService.tryAcquire(dedupValue, batchDate, rerunId, dedupWindowSeconds);
        if (!acquired) {
            return true;
        }
        Instant now = Instant.now();
        Instant last = dedupMap.get(key);
        if (last != null && Duration.between(last, now).getSeconds() < dedupWindowSeconds) {
            return true;
        }
        dedupMap.put(key, now);
        return false;
    }

    public Map<String, Object> schedulerSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        Map<String, Instant> enqueued = queueManager.snapshotEnqueuedMap();
        Instant now = Instant.now();
        long oldestMs = 0L;
        long newestMs = 0L;
        long totalMs = 0L;
        int count = 0;
        for (Instant t : enqueued.values()) {
            long age = Math.max(0L, Duration.between(t, now).toMillis());
            if (age > oldestMs) {
                oldestMs = age;
            }
            if (count == 0 || age < newestMs) {
                newestMs = age;
            }
            totalMs += age;
            count++;
        }
        snapshot.put("queueSize", queueManager.size());
        snapshot.put("enqueuedCount", enqueued.size());
        snapshot.put("oldestWaitMs", oldestMs);
        snapshot.put("newestWaitMs", count == 0 ? 0L : newestMs);
        snapshot.put("avgWaitMs", count == 0 ? 0L : totalMs / count);
        snapshot.put("dedupWindowSeconds", dedupWindowSeconds);
        return snapshot;
    }

    private void scheduleRetry(OrchestrationTaskDefinition def, String reason) {
        Instant when = retryPolicy.nextRetryAt(def);
        upsertState(def, TaskExecutionStatus.READY.name(), reason, true, when);
        scheduleOneShotEnqueue(def, when, "retry");
    }

    private void completeRun(OrchestrationTaskDefinition def, String runKey) {
        queueManager.removeRunKey(runKey);
        schedulerQueueService.dequeue(runKey);
        queueSlaBreached.remove(runKey);
        if (def.getTrigger() != null && def.getTrigger().getType() == TriggerType.FIXED_DELAY) {
            scheduleFixedDelayOnce(def, null);
        }
    }

    private boolean scheduleOneShotEnqueue(OrchestrationTaskDefinition def, Instant when, String reason) {
        try {
            JobDetail detail = ensureQuartzJob(def);
            String suffix = reason == null ? "adhoc" : reason.replaceAll("[^a-zA-Z0-9\\-]", "_");
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(def, suffix + "-" + System.nanoTime()))
                    .forJob(detail)
                    .startAt(java.util.Date.from(when))
                    .build();
            quartzScheduler.scheduleJob(trigger);
            return true;
        } catch (Exception e) {
            log.error("Failed to schedule one-shot enqueue for task={}, reason={}", def.getId(), reason, e);
            return false;
        }
    }

    void scheduleFixedDelayOnce(OrchestrationTaskDefinition def, Instant when) {
        Instant baseAt = when;
        long baseDelayMs = def.getTrigger() == null || def.getTrigger().getFixedDelayMs() == null
                ? 1000L
                : Math.max(1000L, def.getTrigger().getFixedDelayMs());
        if (baseAt == null) {
            baseAt = Instant.now().plusMillis(baseDelayMs);
        }

        FixedDelayBackoffState state = fixedDelayState.computeIfAbsent(def.getId(), _k -> new FixedDelayBackoffState());
        long minIntervalMs = fixedDelayMinIntervalMs;
        if (fixedDelayMaxRequeuePerMinute > 0) {
            long perMinuteInterval = Math.max(1000L, 60000L / fixedDelayMaxRequeuePerMinute);
            minIntervalMs = Math.max(minIntervalMs, perMinuteInterval);
        }

        long backoffDelayMs = baseDelayMs;
        if (state.failureStreak > 0) {
            double factor = Math.pow(fixedDelayFailureBackoffMultiplier, state.failureStreak);
            backoffDelayMs = Math.min(fixedDelayMaxBackoffMs, (long) (baseDelayMs * factor));
        }

        Instant candidate = baseAt;
        Instant now = Instant.now();
        Instant backoffAt = now.plusMillis(backoffDelayMs);
        if (backoffAt.isAfter(candidate)) {
            candidate = backoffAt;
        }
        if (state.lastScheduledAt != null) {
            Instant minNext = state.lastScheduledAt.plusMillis(minIntervalMs);
            if (minNext.isAfter(candidate)) {
                candidate = minNext;
            }
        }

        if (scheduleOneShotEnqueue(def, candidate, "fixed-delay")) {
            state.lastScheduledAt = candidate;
        }
    }

    private JobKey jobKey(OrchestrationTaskDefinition definition) {
        return JobKey.jobKey("task-" + sanitize(definition.getId()), "orchestration");
    }

    private TriggerKey triggerKey(OrchestrationTaskDefinition definition, String suffix) {
        return TriggerKey.triggerKey("tr-" + sanitize(definition.getId()) + "-" + sanitize(suffix), "orchestration");
    }

    private String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "na";
        }
        return text.replaceAll("[^a-zA-Z0-9\\-_]", "_");
    }

    private void markFailed(OrchestrationTaskDefinition def, String reason) {
        upsertState(def, TaskExecutionStatus.FAILED.name(), reason, true, null);
        persistSchedulerDlq(def, reason);
        recordFixedDelayOutcome(def, false);
        taskExecutionAuditService.log(def.getId(), def.getJobName(), resolveBatchDate(def), taskRunKey(def),
                "FAILED", TaskExecutionStatus.FAILED.name(), reason, def.getParameters());
        log.error("Task {} failed: {}", def.getId(), reason);
    }

    private void recordFixedDelayOutcome(OrchestrationTaskDefinition def, boolean success) {
        if (def.getTrigger() == null || def.getTrigger().getType() != TriggerType.FIXED_DELAY) {
            return;
        }
        FixedDelayBackoffState state = fixedDelayState.computeIfAbsent(def.getId(), _k -> new FixedDelayBackoffState());
        if (success) {
            state.failureStreak = 0;
            state.lastSuccessAt = Instant.now();
            return;
        }
        state.failureStreak = Math.min(state.failureStreak + 1, 10);
        state.lastFailureAt = Instant.now();
    }

    private static class FixedDelayBackoffState {
        private int failureStreak;
        private Instant lastScheduledAt;
        private Instant lastSuccessAt;
        private Instant lastFailureAt;
    }

    private void persistSchedulerDlq(OrchestrationTaskDefinition def, String reason) {
        try {
            DlqRecord record = new DlqRecord();
            record.setJobName(def.getJobName());
            record.setParams("taskId=" + def.getId() + "&batchDate=" + resolveBatchDate(def) + "&source=scheduler");
            record.setErrorMessage(reason);
            record.setErrorCode(com.example.filebatchprocessor.exception.ErrorCode.INTERNAL_ERROR.name());
            record.setHandled(false);
            record.setRetryable(retryPolicy.classify(reason) == RetryPolicy.FailureClass.RETRYABLE);
            record.setManualRequired(false);
            record.setCompensationStatus("PENDING");
            record.setNextRetryAt(LocalDateTime.now());
            dlqRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to persist scheduler DLQ for task {}", def.getId(), e);
        }
    }

    private void upsertState(OrchestrationTaskDefinition def,
                             String status,
                             String error,
                             boolean increaseAttempt,
                             Instant nextRetryAt) {
        String batchDate = resolveBatchDate(def);
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        Integer maxAttempts = def.getMaxAttempts() == null ? defaultMaxAttempts : Math.max(1, def.getMaxAttempts());
        Instant start = queueManager.enqueuedAtOrNow(taskRunKey(def));
        long rerunWindow = resolveRerunWindowMs(def);

        String errorCode = null;
        if (error != null && !error.isBlank() && ("FAILED".equals(status) || "READY".equals(status))) {
            errorCode = ErrorCodeClassifier.classify(new RuntimeException(error)).name();
        }

        taskExecutionStateService.upsert(
                def.getId(),
                batchDate,
                rerunId,
                status,
                maxAttempts,
                LocalDateTime.ofInstant(start, ZoneId.systemDefault()),
                LocalDateTime.ofInstant(start.plusMillis(rerunWindow), ZoneId.systemDefault()),
                error,
                errorCode,
                increaseAttempt,
                nextRetryAt == null ? null : LocalDateTime.ofInstant(nextRetryAt, ZoneId.systemDefault())
        );
    }

    private String buildMergeKey(OrchestrationTaskDefinition def, String batchDate) {
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        return def.getJobName() + ":" + batchDate + ":" + rerunId;
    }

    private String resolveBatchDate(OrchestrationTaskDefinition def) {
        String raw = def.getParameters().get("batchDate");
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        return LocalDate.now().format(BATCH_DATE_FORMATTER);
    }

    private String taskRunKey(OrchestrationTaskDefinition def) {
        String batchDate = resolveBatchDate(def);
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        return def.getId() + ":" + batchDate + ":" + rerunId;
    }

    private int getCurrentAttempt(OrchestrationTaskDefinition def) {
        String batchDate = resolveBatchDate(def);
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        Optional<TaskExecutionState> state = taskExecutionStateRepository
                .findByTaskIdAndBatchDateAndRerunId(def.getId(), batchDate, rerunId);
        return state.map(s -> s.getAttempt() == null ? 0 : s.getAttempt()).orElse(0);
    }

    private long resolveMaxQueueWaitMs(OrchestrationTaskDefinition def) {
        return def.getMaxQueueWaitMs() != null && def.getMaxQueueWaitMs() > 0
                ? def.getMaxQueueWaitMs()
                : defaultMaxQueueWaitMs;
    }

    private boolean shouldBackpressure() {
        return queueManager.size() >= queueBackpressureThreshold;
    }

    private long resolveDependencyTimeoutMs(OrchestrationTaskDefinition def) {
        return def.getDependencyTimeoutMs() != null && def.getDependencyTimeoutMs() > 0
                ? def.getDependencyTimeoutMs()
                : defaultDependencyTimeoutMs;
    }

    private long resolveRerunWindowMs(OrchestrationTaskDefinition def) {
        return def.getRerunWindowMs() != null && def.getRerunWindowMs() > 0
                ? def.getRerunWindowMs()
                : defaultRerunWindowMs;
    }
}
