package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskExecutionStateService {

    private final TaskExecutionStateRepository repository;

    public TaskExecutionStateService(TaskExecutionStateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskExecutionState upsert(
            String taskId,
            String batchDate,
            String rerunId,
            String status,
            Integer maxAttempts,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            String errorMessage,
            String errorCode,
            boolean increaseAttempt,
            LocalDateTime nextRetryAt) {
        TaskExecutionState state = repository
                .findByTaskIdAndBatchDateAndRerunId(taskId, batchDate, rerunId)
                .orElseGet(TaskExecutionState::new);
        state.setTaskId(taskId);
        state.setBatchDate(batchDate);
        state.setRerunId(rerunId == null ? "" : rerunId);
        state.setStatus(TaskExecutionStatus.normalize(status));
        state.setMaxAttempts(maxAttempts == null ? 1 : Math.max(1, maxAttempts));
        state.setWindowStart(windowStart);
        state.setWindowEnd(windowEnd);
        state.setLastError(errorMessage);
        state.setErrorCode(errorCode);
        state.setNextRetryAt(nextRetryAt);
        state.setUpdatedAt(LocalDateTime.now());
        if (increaseAttempt) {
            state.setAttempt((state.getAttempt() == null ? 0 : state.getAttempt()) + 1);
        } else if (state.getAttempt() == null) {
            state.setAttempt(0);
        }
        return repository.save(state);
    }
}
