package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SchedulerStateReconciler {

    private static final Logger log = LoggerFactory.getLogger(SchedulerStateReconciler.class);

    private final TaskExecutionStateRepository taskExecutionStateRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final SchedulerLeaderService schedulerLeaderService;

    private final long staleRunningMs;

    public SchedulerStateReconciler(
            TaskExecutionStateRepository taskExecutionStateRepository,
            DlqRecordRepository dlqRecordRepository,
            SchedulerLeaderService schedulerLeaderService,
            @Value("${orchestration.scheduler.reconcile-stale-running-ms:3600000}") long staleRunningMs) {
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.schedulerLeaderService = schedulerLeaderService;
        this.staleRunningMs = Math.max(60000L, staleRunningMs);
    }

    @Scheduled(fixedDelayString = "${orchestration.scheduler.reconcile-interval-ms:60000}")
    public void reconcileStaleStates() {
        if (!schedulerLeaderService.isLeader()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusNanos(staleRunningMs * 1_000_000);
        List<TaskExecutionState> stale = taskExecutionStateRepository.findTop200ByStatusInAndUpdatedAtBefore(
                List.of(TaskExecutionStatus.RUNNING.name(), TaskExecutionStatus.BLOCKED.name()), cutoff);
        if (stale.isEmpty()) {
            return;
        }
        for (TaskExecutionState state : stale) {
            try {
                if (TaskExecutionStatus.RUNNING.name().equals(state.getStatus())
                        && (state.getWindowEnd() == null || state.getWindowEnd().isAfter(LocalDateTime.now()))) {
                    log.info(
                            "Skip stale RUNNING state before execution window ends: taskId={} batchDate={} windowEnd={}",
                            state.getTaskId(),
                            state.getBatchDate(),
                            state.getWindowEnd());
                    continue;
                }
                String reason = "Stale state beyond reconcile window";
                state.setStatus(TaskExecutionStatus.FAILED.name());
                state.setLastError(reason);
                state.setErrorCode(ErrorCode.INTERNAL_ERROR.name());
                state.setUpdatedAt(LocalDateTime.now());
                taskExecutionStateRepository.save(state);

                DlqRecord record = new DlqRecord();
                record.setJobName(state.getTaskId());
                record.setParams(
                        "taskId=" + state.getTaskId() + "&batchDate=" + state.getBatchDate() + "&source=reconciler");
                record.setErrorMessage(reason);
                record.setErrorCode(ErrorCode.INTERNAL_ERROR.name());
                record.setHandled(false);
                record.setRetryable(true);
                record.setManualRequired(false);
                record.setCompensationStatus("PENDING");
                record.setNextRetryAt(LocalDateTime.now());
                dlqRecordRepository.save(record);
            } catch (Exception ex) {
                log.warn(
                        "Failed to reconcile stale state taskId={} batchDate={}",
                        state.getTaskId(),
                        state.getBatchDate(),
                        ex);
            }
        }
    }
}
