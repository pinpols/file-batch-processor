package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class DependencyResolver {

    public enum DependencyState {
        READY,
        WAITING,
        FAILED,
        SKIPPED
    }

    private final TaskExecutionStateRepository taskExecutionStateRepository;

    public DependencyResolver(TaskExecutionStateRepository taskExecutionStateRepository) {
        this.taskExecutionStateRepository = taskExecutionStateRepository;
    }

    public DependencyState resolve(
            OrchestrationTaskDefinition task, String batchDate, long defaultDependencyTimeoutMs, long waitedMs) {
        String rerunId = task.getParameters().getOrDefault("rerunId", "");
        for (String dep : task.getDependencies()) {
            String dependencyBatchDate = resolveDependencyBatchDate(task, dep, batchDate);
            TaskExecutionState state = taskExecutionStateRepository
                    .findByTaskIdAndBatchDateAndRerunId(dep, dependencyBatchDate, rerunId)
                    .orElse(null);
            Long configuredTimeout = task.getDependencyTimeoutByTask() == null
                    ? null
                    : task.getDependencyTimeoutByTask().get(dep);
            long depTimeout = configuredTimeout == null ? defaultDependencyTimeoutMs : configuredTimeout;
            if (waitedMs > Math.max(1000L, depTimeout)) {
                return DependencyState.FAILED;
            }
            if (state == null || state.getStatus() == null) {
                return DependencyState.WAITING;
            }
            String depStatus = TaskExecutionStatus.normalize(state.getStatus());
            String configuredAction = task.getDependencyFailureActionByTask() == null
                    ? null
                    : task.getDependencyFailureActionByTask().get(dep);
            String action = (configuredAction == null || configuredAction.isBlank() ? "FAIL" : configuredAction)
                    .toUpperCase(Locale.ROOT);
            if (TaskExecutionStatus.FAILED.name().equals(depStatus)
                    || TaskExecutionStatus.PARTIAL.name().equals(depStatus)) {
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

    private String resolveDependencyBatchDate(
            OrchestrationTaskDefinition task, String dependencyTaskId, String batchDate) {
        Integer offset = task.getDependencyBatchDateOffsetDaysByTask() == null
                ? 0
                : task.getDependencyBatchDateOffsetDaysByTask().getOrDefault(dependencyTaskId, 0);
        if (offset == null || offset == 0) {
            return batchDate;
        }
        try {
            return LocalDate.parse(batchDate).plusDays(offset).toString();
        } catch (DateTimeParseException ex) {
            return batchDate;
        }
    }
}
