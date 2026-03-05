package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Misfire 策略服务
 * 处理错失任务的补偿和恢复机制
 */
@Slf4j
@Service
public class MisfirePolicyService {

    private final TaskExecutionStateRepository taskExecutionStateRepository;
    private final TaskSchedulerService taskSchedulerService;
    private final SchedulerLeaderService schedulerLeaderService;

    // 统计信息
    private final AtomicLong totalMisfiresDetected = new AtomicLong(0);
    private final AtomicLong totalMisfiresRecovered = new AtomicLong(0);
    private final AtomicLong totalMisfiresAbandoned = new AtomicLong(0);

    // 配置参数
    private final long misfireDetectionWindowMs;
    private final long misfireRecoveryDelayMs;
    private final int maxMisfireRecoveryAttempts;
    private final boolean misfireRecoveryEnabled;

    public MisfirePolicyService(
            TaskExecutionStateRepository taskExecutionStateRepository,
            TaskSchedulerService taskSchedulerService,
            SchedulerLeaderService schedulerLeaderService,
            MisfirePolicyProperties properties) {
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.taskSchedulerService = taskSchedulerService;
        this.schedulerLeaderService = schedulerLeaderService;
        this.misfireDetectionWindowMs = properties.getDetectionWindowMs();
        this.misfireRecoveryDelayMs = properties.getRecoveryDelayMs();
        this.maxMisfireRecoveryAttempts = properties.getMaxRecoveryAttempts();
        this.misfireRecoveryEnabled = properties.isEnabled();
    }

    /**
     * 定期检测和处理 misfire 任务
     */
    @Scheduled(fixedDelayString = "${orchestration.scheduler.misfire.check-interval-ms:60000}")
    public void detectAndHandleMisfires() {
        if (!misfireRecoveryEnabled || !schedulerLeaderService.isLeader()) {
            return;
        }

        try {
            Instant detectionThreshold = Instant.now().minus(misfireDetectionWindowMs, ChronoUnit.MILLIS);
            
            // 查找可能 misfire 的任务 - 使用 nextRetryAt 字段
            List<TaskExecutionState> potentialMisfires = taskExecutionStateRepository
                .findAll().stream()
                .filter(state -> TaskExecutionStatus.READY.name().equals(state.getStatus()))
                .filter(state -> state.getNextRetryAt() != null)
                .filter(state -> state.getNextRetryAt().isBefore(LocalDateTime.ofInstant(detectionThreshold, java.time.ZoneId.systemDefault())))
                .limit(100) // 限制处理数量
                .toList();

            log.debug("Found {} potential misfire tasks", potentialMisfires.size());

            for (TaskExecutionState state : potentialMisfires) {
                handleMisfireTask(state);
            }

        } catch (Exception e) {
            log.error("Error during misfire detection and handling", e);
        }
    }

    /**
     * 处理单个 misfire 任务
     */
    private void handleMisfireTask(TaskExecutionState state) {
        try {
            totalMisfiresDetected.incrementAndGet();
            
            MisfireContext context = createMisfireContext(state);
            MisfireDecision decision = makeMisfireDecision(context);

            log.info("Processing misfire task: {}, decision: {}, attempts: {}", 
                    state.getTaskId(), decision.getAction(), context.getRecoveryAttempts());

            switch (decision.getAction()) {
                case RESCHEDULE:
                    rescheduleMisfireTask(state, context, decision);
                    break;
                case ABANDON:
                    abandonMisfireTask(state, context);
                    break;
                case RETRY_LATER:
                    scheduleRetryLater(state, context, decision);
                    break;
                default:
                    log.warn("Unknown misfire decision: {} for task: {}", 
                            decision.getAction(), state.getTaskId());
            }

        } catch (Exception e) {
            log.error("Error handling misfire task: {}", state.getTaskId(), e);
        }
    }

    /**
     * 创建 misfire 上下文
     */
    private MisfireContext createMisfireContext(TaskExecutionState state) {
        Instant now = Instant.now();
        Instant nextExecutionTime = state.getNextRetryAt() != null 
            ? state.getNextRetryAt().atZone(java.time.ZoneId.systemDefault()).toInstant() 
            : now;
        
        long misfireDuration = Duration.between(nextExecutionTime, now).toMillis();
        int recoveryAttempts = state.getAttempt() != null ? state.getAttempt() : 0;

        return MisfireContext.builder()
            .taskId(state.getTaskId())
            .scheduledExecutionTime(nextExecutionTime)
            .actualDetectionTime(now)
            .misfireDuration(misfireDuration)
            .recoveryAttempts(recoveryAttempts)
            .maxRecoveryAttempts(maxMisfireRecoveryAttempts)
            .build();
    }

    /**
     * 制定 misfire 决策
     */
    private MisfireDecision makeMisfireDecision(MisfireContext context) {
        // 如果恢复次数超过限制，放弃任务
        if (context.getRecoveryAttempts() >= context.getMaxRecoveryAttempts()) {
            return MisfireDecision.builder()
                .action(MisfireAction.ABANDON)
                .reason("Maximum recovery attempts exceeded")
                .build();
        }

        // 根据 misfire 持续时间决定策略
        long misfireDuration = context.getMisfireDuration();
        
        if (misfireDuration < misfireDetectionWindowMs / 4) {
            // 轻微 misfire，立即重新调度
            return MisfireDecision.builder()
                .action(MisfireAction.RESCHEDULE)
                .reason("Minor misfire, immediate reschedule")
                .delayMs(0)
                .build();
        } else if (misfireDuration < misfireDetectionWindowMs / 2) {
            // 中等 misfire，延迟重新调度
            return MisfireDecision.builder()
                .action(MisfireAction.RESCHEDULE)
                .reason("Moderate misfire, delayed reschedule")
                .delayMs(misfireRecoveryDelayMs)
                .build();
        } else if (misfireDuration < misfireDetectionWindowMs) {
            // 严重 misfire，稍后重试
            return MisfireDecision.builder()
                .action(MisfireAction.RETRY_LATER)
                .reason("Severe misfire, retry later")
                .delayMs(misfireRecoveryDelayMs * 2)
                .build();
        } else {
            // 极严重 misfire，放弃任务
            return MisfireDecision.builder()
                .action(MisfireAction.ABANDON)
                .reason("Severe misfire, task abandoned")
                .build();
        }
    }

    /**
     * 重新调度 misfire 任务
     */
    private void rescheduleMisfireTask(TaskExecutionState state, MisfireContext context, MisfireDecision decision) {
        try {
            // 增加尝试次数
            int newAttempt = context.getRecoveryAttempts() + 1;
            state.setAttempt(newAttempt);
            
            // 设置新的执行时间
            Instant newExecutionTime = Instant.now().plus(decision.getDelayMs(), ChronoUnit.MILLIS);
            state.setNextRetryAt(LocalDateTime.ofInstant(newExecutionTime, java.time.ZoneId.systemDefault()));
            
            // 更新状态
            state.setStatus(TaskExecutionStatus.READY.name());
            state.setLastError("Misfire recovered: " + decision.getReason());
            state.setUpdatedAt(LocalDateTime.now());
            
            taskExecutionStateRepository.save(state);
            
            // 重新调度任务
            taskSchedulerService.enqueueByTaskId(state.getTaskId());
            
            totalMisfiresRecovered.incrementAndGet();
            
            log.info("Rescheduled misfire task: {}, new execution time: {}, attempts: {}", 
                    state.getTaskId(), newExecutionTime, newAttempt);
            
        } catch (Exception e) {
            log.error("Failed to reschedule misfire task: {}", state.getTaskId(), e);
        }
    }

    /**
     * 放弃 misfire 任务
     */
    private void abandonMisfireTask(TaskExecutionState state, MisfireContext context) {
        try {
            state.setStatus(TaskExecutionStatus.FAILED.name());
            state.setLastError("Task abandoned due to misfire: " + context.getMisfireDuration() + "ms delay");
            state.setUpdatedAt(LocalDateTime.now());
            
            taskExecutionStateRepository.save(state);
            
            totalMisfiresAbandoned.incrementAndGet();
            
            log.warn("Abandoned misfire task: {} due to excessive delay: {}ms", 
                    state.getTaskId(), context.getMisfireDuration());
            
        } catch (Exception e) {
            log.error("Failed to abandon misfire task: {}", state.getTaskId(), e);
        }
    }

    /**
     * 稍后重试 misfire 任务
     */
    private void scheduleRetryLater(TaskExecutionState state, MisfireContext context, MisfireDecision decision) {
        try {
            // 增加尝试次数
            int newAttempt = context.getRecoveryAttempts() + 1;
            state.setAttempt(newAttempt);
            
            // 设置稍后的重试时间
            Instant retryTime = Instant.now().plus(decision.getDelayMs(), ChronoUnit.MILLIS);
            state.setNextRetryAt(LocalDateTime.ofInstant(retryTime, java.time.ZoneId.systemDefault()));
            
            // 保持 READY 状态
            state.setLastError("Misfire retry scheduled: " + decision.getReason());
            state.setUpdatedAt(LocalDateTime.now());
            
            taskExecutionStateRepository.save(state);
            
            log.info("Scheduled retry for misfire task: {}, retry time: {}, attempts: {}", 
                    state.getTaskId(), retryTime, newAttempt);
            
        } catch (Exception e) {
            log.error("Failed to schedule retry for misfire task: {}", state.getTaskId(), e);
        }
    }

    /**
     * 获取 misfire 统计信息
     */
    public MisfireStatistics getStatistics() {
        return MisfireStatistics.builder()
            .totalMisfiresDetected(totalMisfiresDetected.get())
            .totalMisfiresRecovered(totalMisfiresRecovered.get())
            .totalMisfiresAbandoned(totalMisfiresAbandoned.get())
            .recoveryRate(calculateRecoveryRate())
            .build();
    }

    private double calculateRecoveryRate() {
        long detected = totalMisfiresDetected.get();
        if (detected == 0) return 0.0;
        return (double) totalMisfiresRecovered.get() / detected * 100.0;
    }

    // 内部类定义
    @lombok.Data
    @lombok.Builder
    private static class MisfireContext {
        private String taskId;
        private Instant scheduledExecutionTime;
        private Instant actualDetectionTime;
        private long misfireDuration;
        private int recoveryAttempts;
        private int maxRecoveryAttempts;
    }

    @lombok.Data
    @lombok.Builder
    private static class MisfireDecision {
        private MisfireAction action;
        private String reason;
        private long delayMs;
    }

    private enum MisfireAction {
        RESCHEDULE,
        ABANDON,
        RETRY_LATER
    }

    @lombok.Data
    @lombok.Builder
    public static class MisfireStatistics {
        private long totalMisfiresDetected;
        private long totalMisfiresRecovered;
        private long totalMisfiresAbandoned;
        private double recoveryRate;
    }
}
