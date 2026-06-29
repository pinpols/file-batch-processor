package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.config.BatchTimezoneProvider;
import com.example.filebatchprocessor.exception.ErrorCodeClassifier;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.observability.MdcContext;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.scheduler.LocalCacheService;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.BatchJobResolver;
import com.example.filebatchprocessor.service.ExecutionDedupService;
import com.example.filebatchprocessor.service.JobInstanceService;
import com.example.filebatchprocessor.service.SchedulerLeaderService;
import com.example.filebatchprocessor.service.SchedulerQueueService;
import com.example.filebatchprocessor.service.TaskExecutionAuditService;
import com.example.filebatchprocessor.service.TaskExecutionStateService;
import com.example.filebatchprocessor.util.IdempotencyKeyBuilder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskSchedulerService {

    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String ORCHESTRATION_GROUP = "orchestration";

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
    private final ZoneId zoneId;

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

    public TaskSchedulerService(
            JobOperator jobOperator,
            BatchJobResolver batchJobResolver,
            TaskGraphManager taskGraphManager,
            LocalCacheService localCacheService,
            TaskMergeService taskMergeService,
            ExecutionDedupService executionDedupService,
            TaskExecutionStateService taskExecutionStateService,
            TaskExecutionStateRepository taskExecutionStateRepository,
            SchedulerLeaderService schedulerLeaderService,
            SchedulerQueueService schedulerQueueService,
            TaskExecutionAuditService taskExecutionAuditService,
            JobInstanceService jobInstanceService,
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
            @Value(
                            "${orchestration.scheduler.default-warn-threshold-ms:${orchestration.scheduler.default-timeout-ms:1800000}}")
                    long defaultLaunchWarnThresholdMs,
            @Value("${orchestration.scheduler.default-dynamic-shard-max:1}") int defaultDynamicShardMax,
            @Value("${orchestration.scheduler.default-dependency-timeout-ms:600000}") long defaultDependencyTimeoutMs,
            @Value("${orchestration.scheduler.default-rerun-window-ms:86400000}") long defaultRerunWindowMs,
            @Value("${orchestration.scheduler.default-retry-backoff-ms:60000}") long defaultRetryBackoffMs,
            @Value("${orchestration.scheduler.default-retry-jitter-ratio:0}") double defaultRetryJitterRatio,
            @Value("${orchestration.scheduler.default-max-attempts:3}") int defaultMaxAttempts,
            @Value("${orchestration.scheduler.backpressure-threshold:1500}") int queueBackpressureThreshold,
            @Value("${orchestration.scheduler.backpressure-delay-ms:5000}") long queueBackpressureDelayMs,
            @Value("${orchestration.scheduler.fixed-delay.min-requeue-interval-ms:2000}") long fixedDelayMinIntervalMs,
            @Value("${orchestration.scheduler.fixed-delay.max-requeue-per-minute:60}")
                    int fixedDelayMaxRequeuePerMinute,
            @Value("${orchestration.scheduler.fixed-delay.failure-backoff-multiplier:2.0}")
                    double fixedDelayFailureBackoffMultiplier,
            @Value("${orchestration.scheduler.fixed-delay.max-backoff-ms:300000}") long fixedDelayMaxBackoffMs,
            @Value("${batch.dedup.window.seconds:60}") long dedupWindowSeconds,
            BatchTimezoneProvider timezoneProvider) {
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
                jobOperator,
                batchJobResolver,
                jobInstanceService,
                launchPermits,
                defaultDynamicShardMax,
                defaultLaunchWarnThresholdMs);
        this.zoneId = timezoneProvider.zoneId();
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

    public ManualEnqueueResult enqueueManualRerun(String taskId, String bizDate, String reason, String operator) {
        OrchestrationTaskDefinition registered = taskGraphManager.get(taskId);
        if (registered == null) {
            throw new IllegalArgumentException("Task is not registered: " + taskId);
        }
        OrchestrationTaskDefinition definition = copyForManualRun(registered);
        Map<String, String> parameters =
                new HashMap<>(registered.getParameters() == null ? Map.of() : registered.getParameters());
        if (bizDate != null && !bizDate.isBlank()) {
            parameters.put("batchDate", bizDate);
        }
        String rerunId = "manual-" + System.currentTimeMillis();
        parameters.put("rerunId", rerunId);
        if (reason != null && !reason.isBlank()) {
            parameters.put("rerunReason", reason);
        }
        if (operator != null && !operator.isBlank()) {
            parameters.put("triggeredBy", operator);
        }
        definition.setParameters(parameters);
        definition.setEnabled(Boolean.TRUE);
        String runKey = taskRunKey(definition);
        enqueue(definition);
        return new ManualEnqueueResult(
                definition.getId(), definition.getJobName(), resolveBatchDate(definition), rerunId, runKey);
    }

    public void resetPersistedSchedules() {
        if (!schedulerLeaderService.isLeader()) {
            log.info("Skip resetting Quartz orchestration rows because current instance is not leader");
            return;
        }
        try {
            if (!quartzScheduler.getMetaData().isJobStoreSupportsPersistence()) {
                return;
            }
            String schedName = quartzScheduler.getSchedulerName();
            resetPersistedSchedules(schedName);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Failed to reset persisted Quartz orchestration schedules", ex);
        }
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
                    Trigger fixedRateTrigger = buildFixedRateTrigger(definition, jobDetail, fixedRateMs);
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
                            .startAt(Date.from(at))
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
                    quartzScheduler.rescheduleJob(key, trigger);
                } else {
                    quartzScheduler.scheduleJob(trigger);
                }
            } catch (Exception e) {
                log.warn("Failed to upsert trigger {}, retrying with force replace: {}", key, e.getMessage());
                try {
                    quartzScheduler.unscheduleJob(key);
                } catch (Exception unscheduleEx) {
                    // best-effort:触发器可能本就不存在,清不掉也无妨,后面直接 scheduleJob 重建
                    log.trace(
                            "Ignore unschedule failure for {} before force reschedule: {}",
                            key,
                            unscheduleEx.getMessage());
                }
                quartzScheduler.scheduleJob(trigger);
            }
        }
    }

    private Trigger buildFixedRateTrigger(
            OrchestrationTaskDefinition definition, JobDetail jobDetail, long fixedRateMs) {
        Date firstFireAt = Date.from(Instant.now().plusMillis(2000L));
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(definition, "fixed-rate"))
                .forJob(jobDetail)
                .startAt(firstFireAt)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(Math.max(1000L, fixedRateMs))
                        .repeatForever())
                .build();
    }

    private void enqueue(OrchestrationTaskDefinition definition) {
        if (!schedulerLeaderService.isLeader()) {
            return;
        }

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
                long delayMs = definition.getTrigger().getFixedDelayMs() == null
                        ? 1000L
                        : Math.max(1000L, definition.getTrigger().getFixedDelayMs());
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
                definition.getParameters());
        batchTaskExecutor.submit(this::drainQueue);
    }

    // 周期性 drain tick:drainQueue 原本只在 enqueue 时触发,WAITING 任务靠忙旋自我重查。
    // 改为:WAITING 任务暂存出队、轮末一次性重入队(本 pass 不再自旋),由本 tick 每隔几秒重新 drain
    // 触发重查,把"CPU+DB 忙旋最长 10 分钟"降为"每 tick 重查一次"。
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${orchestration.scheduler.drain-tick-ms:3000}")
    public void scheduledDrainTick() {
        if (schedulerLeaderService.isLeader() && queueManager.size() > 0) {
            batchTaskExecutor.submit(this::drainQueue);
        }
    }

    private void drainQueue() {
        if (!schedulerLeaderService.isLeader()) {
            return;
        }
        // 本轮被判定为依赖未就绪(WAITING)的任务暂存于此,轮末统一重入队,避免在同一 pass 内立即重 poll 自旋。
        List<OrchestrationTaskDefinition> heldWaiting = new ArrayList<>();
        List<OrchestrationTaskDefinition> heldThrottled = new ArrayList<>();
        Set<String> heldRunKeys = new HashSet<>();
        OrchestrationTaskDefinition def;
        while ((def = queueManager.poll()) != null) {
            String runKey = taskRunKey(def);
            long waitedMs = queueManager.waitedMs(runKey);
            if (def.getSlaMaxQueueDelayMs() != null
                    && def.getSlaMaxQueueDelayMs() > 0
                    && waitedMs > def.getSlaMaxQueueDelayMs()
                    && queueSlaBreached.putIfAbsent(runKey, Boolean.TRUE) == null) {
                batchMetrics.counter("scheduler_queue_sla_breach_total", "job", String.valueOf(def.getJobName()));
                taskExecutionAuditService.log(
                        def.getId(),
                        def.getJobName(),
                        resolveBatchDate(def),
                        runKey,
                        "QUEUE_SLA_BREACH",
                        TaskExecutionStatus.BLOCKED.name(),
                        "waitedMs=" + waitedMs,
                        def.getParameters());
            }
            if (queueManager.isQueueWaitTimeout(runKey, resolveMaxQueueWaitMs(def))) {
                markFailed(def, "Queue wait timeout exceeded");
                completeRun(def, runKey);
                continue;
            }

            String batchDate = resolveBatchDate(def);
            DependencyResolver.DependencyState dependencyState =
                    dependencyResolver.resolve(def, batchDate, resolveDependencyTimeoutMs(def), waitedMs);
            if (dependencyState == DependencyResolver.DependencyState.FAILED) {
                markFailed(def, "Blocked by failed dependency");
                completeRun(def, runKey);
                continue;
            }
            if (dependencyState == DependencyResolver.DependencyState.SKIPPED) {
                upsertState(
                        def,
                        TaskExecutionStatus.SKIPPED.name(),
                        "Dependency failed and on_failure_action=SKIP",
                        false,
                        null);
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
                // 暂存出队,本轮不再立即重 poll;轮末统一重入队,靠周期 drain tick 重查依赖。
                heldWaiting.add(def);
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
                    heldThrottled.add(task);
                    heldRunKeys.add(taskRunKey(task));
                    batchMetrics.counter("scheduler_concurrency_limited_total", "key", limitKey);
                    continue;
                }

                String targetSystem = task.getParameters().get("targetSystem");
                if (targetSystem != null && !circuitBreaker.tryAcquire(targetSystem)) {
                    heldThrottled.add(task);
                    heldRunKeys.add(taskRunKey(task));
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
                        LaunchExecutor.LaunchResult launchResult =
                                launchExecutor.launch(task, resolveBatchDate(task), queueManager.size());
                        if (launchResult.isShouldReschedule()) {
                            queueManager.requeue(task);
                            heldRunKeys.add(taskRunKey(task));
                            batchMetrics.counter(
                                    "scheduler_launch_reschedule_total", "job", String.valueOf(task.getJobName()));
                            return;
                        }
                        if (!launchResult.isSuccess()) {
                            batchMetrics.counter(
                                    "scheduler_launch_failed_total", "job", String.valueOf(task.getJobName()));
                            int attempt = getCurrentAttempt(task);
                            RetryPolicy.FailureClass failureClass = launchResult.getFailureCause() != null
                                    ? retryPolicy.classify(launchResult.getFailureCause())
                                    : retryPolicy.classify(launchResult.getReason());
                            recordFixedDelayOutcome(task, false);
                            if (failureClass == RetryPolicy.FailureClass.SKIPPABLE) {
                                upsertState(
                                        task, TaskExecutionStatus.SKIPPED.name(), launchResult.getReason(), true, null);
                                batchMetrics.counter(
                                        "scheduler_launch_skipped_total", "job", String.valueOf(task.getJobName()));
                                taskExecutionAuditService.log(
                                        task.getId(),
                                        task.getJobName(),
                                        resolveBatchDate(task),
                                        runKey,
                                        "LAUNCH",
                                        TaskExecutionStatus.SKIPPED.name(),
                                        launchResult.getReason(),
                                        task.getParameters());
                            } else if (retryPolicy.allowRetry(task, attempt, failureClass)) {
                                scheduleRetry(task, launchResult.getReason());
                                taskExecutionAuditService.log(
                                        task.getId(),
                                        task.getJobName(),
                                        resolveBatchDate(task),
                                        runKey,
                                        "RETRY_SCHEDULED",
                                        TaskExecutionStatus.READY.name(),
                                        launchResult.getReason(),
                                        task.getParameters());
                            } else {
                                markFailed(task, launchResult.getReason());
                                taskExecutionAuditService.log(
                                        task.getId(),
                                        task.getJobName(),
                                        resolveBatchDate(task),
                                        runKey,
                                        "LAUNCH",
                                        TaskExecutionStatus.FAILED.name(),
                                        launchResult.getReason(),
                                        task.getParameters());
                            }
                            if (targetSystem != null) {
                                circuitBreaker.recordResult(targetSystem, false);
                            }
                            return;
                        }
                        if (launchResult.isPartial()) {
                            upsertState(
                                    task, TaskExecutionStatus.PARTIAL.name(), launchResult.getReason(), false, null);
                            batchMetrics.counter(
                                    "scheduler_launch_partial_total", "job", String.valueOf(task.getJobName()));
                            recordFixedDelayOutcome(task, false);
                            taskExecutionAuditService.log(
                                    task.getId(),
                                    task.getJobName(),
                                    resolveBatchDate(task),
                                    runKey,
                                    "LAUNCH",
                                    TaskExecutionStatus.PARTIAL.name(),
                                    launchResult.getReason(),
                                    task.getParameters());
                        } else {
                            upsertState(task, TaskExecutionStatus.SUCCESS.name(), null, false, null);
                            batchMetrics.counter(
                                    "scheduler_launch_success_total", "job", String.valueOf(task.getJobName()));
                            recordFixedDelayOutcome(task, true);
                            taskExecutionAuditService.log(
                                    task.getId(),
                                    task.getJobName(),
                                    resolveBatchDate(task),
                                    runKey,
                                    "LAUNCH",
                                    TaskExecutionStatus.SUCCESS.name(),
                                    null,
                                    task.getParameters());
                        }
                        if (targetSystem != null) {
                            circuitBreaker.recordResult(targetSystem, true);
                        }
                    });
                } finally {
                    permit.release();
                }
            }
            // 合并出的每个 sibling 有各自的 runKey,需逐个清理其队列行/enqueuedAt,
            // 否则只清 def 的 runKey 会让 sibling 的 DB queue 行残留(后续被错误复用或永不出队)。
            Set<String> completedKeys = new HashSet<>();
            for (OrchestrationTaskDefinition mergedTask : merged) {
                String mergedKey = taskRunKey(mergedTask);
                if (!heldRunKeys.contains(mergedKey) && completedKeys.add(mergedKey)) {
                    completeRun(mergedTask, mergedKey);
                }
            }
            if (!heldRunKeys.contains(runKey) && completedKeys.add(runKey)) {
                completeRun(def, runKey);
            }
        }
        // 本轮暂存的 WAITING 任务统一重入队(enqueuedAt 由 putIfAbsent 保留,依赖超时仍正确计时),
        // 由周期 drain tick 在下一轮重查依赖,避免在本 pass 内忙旋。
        for (OrchestrationTaskDefinition held : heldWaiting) {
            queueManager.requeue(held);
        }
        for (OrchestrationTaskDefinition held : heldThrottled) {
            queueManager.requeue(held);
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
        pruneDedupMap(now);
        Instant last = dedupMap.get(key);
        if (last != null && Duration.between(last, now).getSeconds() < dedupWindowSeconds) {
            return true;
        }
        dedupMap.put(key, now);
        return false;
    }

    private void pruneDedupMap(Instant now) {
        if (dedupMap.size() < 10_000) {
            return;
        }
        dedupMap.entrySet().removeIf(e -> Duration.between(e.getValue(), now).getSeconds() >= dedupWindowSeconds);
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
        // 按已发生的重试次数计算指数退避时间。
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        int attempt = taskExecutionStateRepository
                .findByTaskIdAndBatchDateAndRerunId(def.getId(), resolveBatchDate(def), rerunId)
                .map(s -> s.getAttempt() == null ? 0 : s.getAttempt())
                .orElse(0);
        Instant when = retryPolicy.nextRetryAt(def, attempt);
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
                    .startAt(Date.from(when))
                    .build();
            quartzScheduler.scheduleJob(trigger);
            return true;
        } catch (Exception e) {
            log.error("Failed to schedule one-shot enqueue for task={}, reason={}", def.getId(), reason, e);
            return false;
        }
    }

    public void scheduleFixedDelayOnce(OrchestrationTaskDefinition def, Instant when) {
        Instant baseAt = when;
        long baseDelayMs = def.getTrigger() == null || def.getTrigger().getFixedDelayMs() == null
                ? 1000L
                : Math.max(1000L, def.getTrigger().getFixedDelayMs());
        if (baseAt == null) {
            baseAt = Instant.now().plusMillis(baseDelayMs);
        }

        Instant now = Instant.now();
        pruneFixedDelayState(now);
        FixedDelayBackoffState state = fixedDelayState.computeIfAbsent(def.getId(), this::loadFixedDelayState);
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
            persistFixedDelayState(def.getId(), state);
        }
    }

    private JobKey jobKey(OrchestrationTaskDefinition definition) {
        return JobKey.jobKey("task-" + sanitize(definition.getId()), ORCHESTRATION_GROUP);
    }

    private TriggerKey triggerKey(OrchestrationTaskDefinition definition, String suffix) {
        return TriggerKey.triggerKey(
                "tr-" + sanitize(definition.getId()) + "-" + sanitize(suffix), ORCHESTRATION_GROUP);
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
        taskExecutionAuditService.log(
                def.getId(),
                def.getJobName(),
                resolveBatchDate(def),
                taskRunKey(def),
                "FAILED",
                TaskExecutionStatus.FAILED.name(),
                reason,
                def.getParameters());
        log.error("Task {} failed: {}", def.getId(), reason);
    }

    private void recordFixedDelayOutcome(OrchestrationTaskDefinition def, boolean success) {
        if (def.getTrigger() == null || def.getTrigger().getType() != TriggerType.FIXED_DELAY) {
            return;
        }
        pruneFixedDelayState(Instant.now());
        FixedDelayBackoffState state = fixedDelayState.computeIfAbsent(def.getId(), this::loadFixedDelayState);
        if (success) {
            state.failureStreak = 0;
        } else {
            state.failureStreak = Math.min(state.failureStreak + 1, 10);
        }
        persistFixedDelayState(def.getId(), state);
    }

    private void pruneFixedDelayState(Instant now) {
        if (fixedDelayState.size() < 10_000) {
            return;
        }
        fixedDelayState.entrySet().removeIf(e -> {
            Instant last = e.getValue().lastScheduledAt;
            return last != null && Duration.between(last, now).toHours() >= 24;
        });
    }

    private static class FixedDelayBackoffState {
        private int failureStreak;
        private Instant lastScheduledAt;
    }

    /**
     * 从 scheduler_fixed_delay_state 表回填退避状态（应用重启后首次访问该任务时触发）， 使 fixedDelayState 内存 map
     * 成为「写穿缓存」而非易失内存。读失败时退回全新状态， 保证调度可用性优先于退避精度（亦兼容单测中 mock 的 JdbcTemplate）。
     */
    private FixedDelayBackoffState loadFixedDelayState(String taskId) {
        FixedDelayBackoffState state = new FixedDelayBackoffState();
        try {
            jdbcTemplate.query(
                    "SELECT failure_streak, last_scheduled_at FROM scheduler_fixed_delay_state WHERE task_id" + " = ?",
                    rs -> {
                        state.failureStreak = Math.max(0, Math.min(rs.getInt("failure_streak"), 10));
                        java.sql.Timestamp ts = rs.getTimestamp("last_scheduled_at");
                        state.lastScheduledAt = ts == null ? null : ts.toInstant();
                    },
                    taskId);
        } catch (Exception e) {
            log.warn("Failed to load fixed-delay backoff state for task={}, starting fresh", taskId, e);
        }
        return state;
    }

    private void persistFixedDelayState(String taskId, FixedDelayBackoffState state) {
        try {
            java.sql.Timestamp lastScheduled =
                    state.lastScheduledAt == null ? null : java.sql.Timestamp.from(state.lastScheduledAt);
            jdbcTemplate.update("""
          INSERT INTO scheduler_fixed_delay_state(task_id, failure_streak, last_scheduled_at, updated_at)
          VALUES (?, ?, ?, CURRENT_TIMESTAMP)
          ON CONFLICT (task_id) DO UPDATE
          SET failure_streak = EXCLUDED.failure_streak,
              last_scheduled_at = EXCLUDED.last_scheduled_at,
              updated_at = CURRENT_TIMESTAMP
          """, taskId, state.failureStreak, lastScheduled);
        } catch (Exception e) {
            log.warn("Failed to persist fixed-delay backoff state for task={}", taskId, e);
        }
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

    private void upsertState(
            OrchestrationTaskDefinition def,
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
            errorCode =
                    ErrorCodeClassifier.classify(new RuntimeException(error)).name();
        }

        taskExecutionStateService.upsert(
                def.getId(),
                batchDate,
                rerunId,
                status,
                maxAttempts,
                LocalDateTime.ofInstant(start, zoneId),
                LocalDateTime.ofInstant(start.plusMillis(rerunWindow), zoneId),
                error,
                errorCode,
                increaseAttempt,
                nextRetryAt == null ? null : LocalDateTime.ofInstant(nextRetryAt, zoneId));
    }

    private void resetPersistedSchedules(String schedName) {
        synchronized (quartzTriggerWriteLock) {
            int firedRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_fired_triggers WHERE sched_name = ? AND (trigger_group = ? OR"
                            + " job_group = ?)",
                    schedName,
                    ORCHESTRATION_GROUP,
                    ORCHESTRATION_GROUP);
            int pausedRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_paused_trigger_grps WHERE sched_name = ? AND trigger_group = ?",
                    schedName,
                    ORCHESTRATION_GROUP);
            int simpleRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_simple_triggers WHERE sched_name = ? AND trigger_group = ?",
                    schedName,
                    ORCHESTRATION_GROUP);
            int cronRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_cron_triggers WHERE sched_name = ? AND trigger_group = ?",
                    schedName,
                    ORCHESTRATION_GROUP);
            int simpropRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_simprop_triggers WHERE sched_name = ? AND trigger_group = ?",
                    schedName,
                    ORCHESTRATION_GROUP);
            int blobRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_blob_triggers WHERE sched_name = ? AND trigger_group = ?",
                    schedName,
                    ORCHESTRATION_GROUP);
            int triggerRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_triggers WHERE sched_name = ? AND trigger_group = ?",
                    schedName,
                    ORCHESTRATION_GROUP);
            int jobRows = jdbcTemplate.update(
                    "DELETE FROM qrtz_job_details WHERE sched_name = ? AND job_group = ?",
                    schedName,
                    ORCHESTRATION_GROUP);
            log.info(
                    "Reset persisted Quartz orchestration rows: schedName={}, jobs={}, triggers={},"
                            + " simple={}, cron={}, simprop={}, blob={}, fired={}, paused={}",
                    schedName,
                    jobRows,
                    triggerRows,
                    simpleRows,
                    cronRows,
                    simpropRows,
                    blobRows,
                    firedRows,
                    pausedRows);
        }
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

    private OrchestrationTaskDefinition copyForManualRun(OrchestrationTaskDefinition source) {
        OrchestrationTaskDefinition copy = new OrchestrationTaskDefinition();
        copy.setId(source.getId());
        copy.setJobName(source.getJobName());
        copy.setTenantId(source.getTenantId());
        copy.setBizDomain(source.getBizDomain());
        copy.setEnv(source.getEnv());
        copy.setTrigger(null);
        copy.setPriority(source.getPriority());
        copy.setParameters(new HashMap<>(source.getParameters() == null ? Map.of() : source.getParameters()));
        copy.setDependencies(new ArrayList<>(source.getDependencies() == null ? List.of() : source.getDependencies()));
        copy.setDependencyTimeoutByTask(new HashMap<>(
                source.getDependencyTimeoutByTask() == null ? Map.of() : source.getDependencyTimeoutByTask()));
        copy.setDependencyFailureActionByTask(new HashMap<>(
                source.getDependencyFailureActionByTask() == null
                        ? Map.of()
                        : source.getDependencyFailureActionByTask()));
        copy.setDedupKey(source.getDedupKey());
        copy.setAllowParallel(source.isAllowParallel());
        copy.setAllowMerge(false);
        copy.setEnabled(Boolean.TRUE);
        copy.setSlaMaxDurationMs(source.getSlaMaxDurationMs());
        copy.setSlaMaxQueueDelayMs(source.getSlaMaxQueueDelayMs());
        copy.setRateLimitPerMinute(source.getRateLimitPerMinute());
        copy.setShardIndex(source.getShardIndex());
        copy.setShardTotal(source.getShardTotal());
        copy.setTimeoutMs(source.getTimeoutMs());
        copy.setMaxQueueWaitMs(source.getMaxQueueWaitMs());
        copy.setDynamicShardMax(source.getDynamicShardMax());
        copy.setDependencyTimeoutMs(source.getDependencyTimeoutMs());
        copy.setRerunWindowMs(source.getRerunWindowMs());
        copy.setMaxAttempts(source.getMaxAttempts());
        copy.setRetryBackoffMs(source.getRetryBackoffMs());
        return copy;
    }

    public record ManualEnqueueResult(String taskId, String jobName, String batchDate, String rerunId, String runKey) {}

    private int getCurrentAttempt(OrchestrationTaskDefinition def) {
        String batchDate = resolveBatchDate(def);
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        Optional<TaskExecutionState> state =
                taskExecutionStateRepository.findByTaskIdAndBatchDateAndRerunId(def.getId(), batchDate, rerunId);
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
