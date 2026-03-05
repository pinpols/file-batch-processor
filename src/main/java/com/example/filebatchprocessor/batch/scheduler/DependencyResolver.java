package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;

import java.util.Locale;

class DependencyResolver {

    enum DependencyState {
        READY,
        WAITING,
        FAILED,
        SKIPPED
    }

    private final TaskExecutionStateRepository taskExecutionStateRepository;

    DependencyResolver(TaskExecutionStateRepository taskExecutionStateRepository) {
        this.taskExecutionStateRepository = taskExecutionStateRepository;
    }

    DependencyState resolve(OrchestrationTaskDefinition task, String batchDate, long defaultDependencyTimeoutMs, long waitedMs) {
        String rerunId = task.getParameters().getOrDefault("rerunId", "");
        for (String dep : task.getDependencies()) {
            TaskExecutionState state = taskExecutionStateRepository
                    .findByTaskIdAndBatchDateAndRerunId(dep, batchDate, rerunId)
                    .orElse(null);
            long depTimeout = task.getDependencyTimeoutByTask().getOrDefault(dep, defaultDependencyTimeoutMs);
            if (waitedMs > Math.max(1000L, depTimeout)) {
                return DependencyState.FAILED;
            }
            if (state == null || state.getStatus() == null) {
                return DependencyState.WAITING;
            }
            String depStatus = TaskExecutionStatus.normalize(state.getStatus());
            String action = task.getDependencyFailureActionByTask()
                    .getOrDefault(dep, "FAIL")
                    .toUpperCase(Locale.ROOT);
            if (TaskExecutionStatus.FAILED.name().equals(depStatus) || TaskExecutionStatus.PARTIAL.name().equals(depStatus)) {
                if ("SKIP".equals(action)) {
                    return DependencyState.SKIPPED;
                }
                if ("IGNORE".equals(action)) {
                    continue;
                }
                return DependencyState.FAILED;
            }
            if (!TaskExecutionStatus.SUCCESS.name().equals(depStatus)) {
                return DependencyState.WAITING;
            }
        }
        return DependencyState.READY;
    }
}
