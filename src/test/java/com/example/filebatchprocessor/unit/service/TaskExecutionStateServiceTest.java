package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.service.TaskExecutionStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskExecutionStateServiceTest {

    @Mock
    private TaskExecutionStateRepository repository;

    private TaskExecutionStateService service;

    @BeforeEach
    void setUp() {
        service = new TaskExecutionStateService(repository);
    }

    @Test
    void shouldCreateNewStateAndIncreaseAttempt() {
        when(repository.findByTaskIdAndBatchDateAndRerunId("t1", "2026-03-14", "r1"))
                .thenReturn(Optional.empty());
        when(repository.save(any(TaskExecutionState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskExecutionState saved = service.upsert(
                "t1",
                "2026-03-14",
                "r1",
                TaskExecutionStatus.RUNNING.name(),
                3,
                LocalDateTime.now(),
                null,
                null,
                null,
                true,
                null
        );

        assertEquals("t1", saved.getTaskId());
        assertEquals(1, saved.getAttempt());
        assertEquals(3, saved.getMaxAttempts());
        assertEquals(TaskExecutionStatus.RUNNING.name(), saved.getStatus());
    }

    @Test
    void shouldUpdateExistingStateWithoutIncreasingAttempt() {
        TaskExecutionState existing = new TaskExecutionState();
        existing.setTaskId("t2");
        existing.setBatchDate("2026-03-14");
        existing.setRerunId("r2");
        existing.setAttempt(2);

        when(repository.findByTaskIdAndBatchDateAndRerunId("t2", "2026-03-14", "r2"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(TaskExecutionState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(5);
        TaskExecutionState saved = service.upsert(
                "t2",
                "2026-03-14",
                "r2",
                "success",
                5,
                null,
                null,
                "none",
                null,
                false,
                nextRetry
        );

        assertEquals(2, saved.getAttempt());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals(nextRetry, saved.getNextRetryAt());
    }

    @Test
    void shouldNormalizeRerunIdAndPersist() {
        when(repository.findByTaskIdAndBatchDateAndRerunId("t3", "2026-03-14", null))
                .thenReturn(Optional.empty());
        when(repository.save(any(TaskExecutionState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert("t3", "2026-03-14", null, "ready", null, null, null, null, null, false, null);

        ArgumentCaptor<TaskExecutionState> captor = ArgumentCaptor.forClass(TaskExecutionState.class);
        verify(repository).save(captor.capture());
        assertNotNull(captor.getValue().getUpdatedAt());
        assertEquals("", captor.getValue().getRerunId());
    }
}

