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

    private final QueueManager queueManager;
    private final DependencyResolver dependencyResolver;
    private final RetryPolicy retryPolicy;
    private final LaunchExecutor launchExecutor;

    private final SchedulerConcurrencyLimiter schedulerConcurrencyLimiter;
    private final TargetSystemCircuitBreaker circuitBreaker;
    private final FixedDelayScheduler fixedDelayScheduler;

    private final long defaultMaxQueueWaitMs;
    private final long defaultDependencyTimeoutMs;
    private final long defaultRerunWindowMs;
    private final int defaultMaxAttempts;
    private final long dedupWindowSeconds;
    private final int queueBackpressureThreshold;
    private final long queueBackpressureDelayMs;

    private final ConcurrentMap<String, Instant> dedupMap = new ConcurrentHashMap<>();

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
                                DlqRecordRepository dlqRecordRepository,
                                Scheduler quartzScheduler,
                                ThreadPoolTaskExecutor batchTaskExecutor,
                                SchedulerConcurrencyLimiter schedulerConcurrencyLimiter,
                                TargetSystemCircuitBreaker circuitBreaker,
                                FixedDelayScheduler fixedDelayScheduler,
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
                                @Value("${batch.dedup.window.seconds:60}") long dedupWindowSeconds) {
        this.taskGraphManager = taskGraphManager;
        this.taskMergeService = taskMergeService;
        this.executionDedupService = executionDedupService;
        this.taskExecutionStateService = taskExecutionStateService;
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.schedulerLeaderService = schedulerLeaderService;
        this.schedulerQueueService = schedulerQueueService;
        this.dlqRecordRepository = dlqRecordRepository;
        this.quartzScheduler = quartzScheduler;
        this.batchTaskExecutor = batchTaskExecutor;
        this.schedulerConcurrencyLimiter = schedulerConcurrencyLimiter;
        this.circuitBreaker = circuitBreaker;
        this.fixedDelayScheduler = fixedDelayScheduler;
        this.batchMetrics = batchMetrics;

        this.defaultMaxQueueWaitMs = Math.max(1000, defaultMaxQueueWaitMs);
        this.defaultDependencyTimeoutMs = Math.max(1000, defaultDependencyTimeoutMs);
        this.defaultRerunWindowMs = Math.max(1000, defaultRerunWindowMs);
        this.defaultMaxAttempts = Math.max(1, defaultMaxAttempts);
        this.dedupWindowSeconds = Math.max(1, dedupWindowSeconds);
        this.queueBackpressureThreshold = Math.max(10, queueBackpressureThreshold);
        this.queueBackpressureDelayMs = Math.max(1000L, queueBackpressureDelayMs);

        Semaphore launchPermits = new Semaphore(Math.max(1, maxConcurrentLaunches));
        this.queueManager = new QueueManager(maxQueueSize);
        this.dependencyResolver = new DependencyResolver(taskExecutionStateRepository);
        this.retryPolicy = new RetryPolicy(defaultMaxAttempts, defaultRetryBackoffMs, defaultRetryJitterRatio);
        this.launchExecutor = new LaunchExecutor(
                jobLauncher,
                jobsProvider,
                batchTaskExecutor,
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
                            .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                            .build();
                    upsertTrigger(cronTrigger);
                    break;
                case FIXED_RATE:
                    long fixedRateMs = definition.getTrigger().getFixedRateMs() == null
                            ? 1000L
                            : definition.getTrigger().getFixedRateMs();
                    Trigger fixedRateTrigger = TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey(definition, "fixed-rate"))
                            .forJob(jobDetail)
                            .startNow()
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
        if (quartzScheduler.checkExists(key)) {
            quartzScheduler.rescheduleJob(key, trigger);
        } else {
            quartzScheduler.scheduleJob(trigger);
        }
    }

    private void enqueue(OrchestrationTaskDefinition definition) {
        if (!schedulerLeaderService.isLeader()) {
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
        batchTaskExecutor.submit(this::drainQueue);
    }

    private void drainQueue() {
        if (!schedulerLeaderService.isLeader()) {
            return;
        }
        OrchestrationTaskDefinition def;
        while ((def = queueManager.poll()) != null) {
            String runKey = taskRunKey(def);
            if (queueManager.isQueueWaitTimeout(runKey, resolveMaxQueueWaitMs(def))) {
                markFailed(def, "Queue wait timeout exceeded");
                completeRun(def, runKey);
                continue;
            }

            String batchDate = resolveBatchDate(def);
            long waitedMs = queueManager.waitedMs(runKey);
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
                            if (failureClass == RetryPolicy.FailureClass.SKIPPABLE) {
                                upsertState(task, TaskExecutionStatus.SKIPPED.name(), launchResult.getReason(), true, null);
                                batchMetrics.counter("scheduler_launch_skipped_total", "job", String.valueOf(task.getJobName()));
                            } else if (retryPolicy.allowRetry(task, attempt, launchResult.getReason())) {
                                scheduleRetry(task, launchResult.getReason());
                            } else {
                                markFailed(task, launchResult.getReason());
                            }
                            if (targetSystem != null) {
                                circuitBreaker.recordResult(targetSystem, false);
                            }
                            return;
                        }
                        if (launchResult.isPartial()) {
                            upsertState(task, TaskExecutionStatus.PARTIAL.name(), launchResult.getReason(), false, null);
                            batchMetrics.counter("scheduler_launch_partial_total", "job", String.valueOf(task.getJobName()));
                        } else {
                            upsertState(task, TaskExecutionStatus.SUCCESS.name(), null, false, null);
                            batchMetrics.counter("scheduler_launch_success_total", "job", String.valueOf(task.getJobName()));
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

    private void scheduleRetry(OrchestrationTaskDefinition def, String reason) {
        Instant when = retryPolicy.nextRetryAt(def);
        upsertState(def, TaskExecutionStatus.READY.name(), reason, true, when);
        scheduleOneShotEnqueue(def, when, "retry");
    }

    private void completeRun(OrchestrationTaskDefinition def, String runKey) {
        queueManager.removeRunKey(runKey);
        schedulerQueueService.dequeue(runKey);
        if (def.getTrigger() != null && def.getTrigger().getType() == TriggerType.FIXED_DELAY) {
            long delayMs = def.getTrigger().getFixedDelayMs() == null ? 1000L : Math.max(1000L, def.getTrigger().getFixedDelayMs());
            
            // 使用增强的 FixedDelayScheduler
            Runnable task = () -> {
                try {
                    enqueue(def);
                } catch (Exception e) {
                    log.error("Error in FIXED_DELAY task scheduling: {}", def.getId(), e);
                }
            };
            
            fixedDelayScheduler.scheduleFixedDelay(def.getId(), task, delayMs);
            log.info("Scheduled FIXED_DELAY task: {} with delay: {}ms using enhanced scheduler", def.getId(), delayMs);
        }
    }

    private void scheduleOneShotEnqueue(OrchestrationTaskDefinition def, Instant when, String reason) {
        try {
            JobDetail detail = ensureQuartzJob(def);
            String suffix = reason == null ? "adhoc" : reason.replaceAll("[^a-zA-Z0-9\\-]", "_");
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(def, suffix + "-" + System.nanoTime()))
                    .forJob(detail)
                    .startAt(java.util.Date.from(when))
                    .build();
            quartzScheduler.scheduleJob(trigger);
        } catch (Exception e) {
            log.error("Failed to schedule one-shot enqueue for task={}, reason={}", def.getId(), reason, e);
        }
    }

    private void scheduleFixedDelayOnce(OrchestrationTaskDefinition def, Instant when) {
        scheduleOneShotEnqueue(def, when, "fixed-delay");
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
        log.error("Task {} failed: {}", def.getId(), reason);
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
