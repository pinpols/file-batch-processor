package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerStateReconcilerTest {

    @Mock
    private TaskExecutionStateRepository taskExecutionStateRepository;
    @Mock
    private DlqRecordRepository dlqRecordRepository;
    @Mock
    private SchedulerLeaderService schedulerLeaderService;

    private SchedulerStateReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new SchedulerStateReconciler(taskExecutionStateRepository, dlqRecordRepository, schedulerLeaderService, 120_000);
    }

    @Test
    void shouldSkipWhenNotLeader() {
        when(schedulerLeaderService.isLeader()).thenReturn(false);

        reconciler.reconcileStaleStates();

        verify(taskExecutionStateRepository, never()).findTop200ByStatusInAndUpdatedAtBefore(anyList(), any(LocalDateTime.class));
    }

    @Test
    void shouldReconcileStaleStateToFailedAndCreateDlq() {
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        TaskExecutionState stale = new TaskExecutionState();
        stale.setTaskId("task-1");
        stale.setBatchDate("2026-03-14");
        stale.setStatus(TaskExecutionStatus.RUNNING.name());
        when(taskExecutionStateRepository.findTop200ByStatusInAndUpdatedAtBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        reconciler.reconcileStaleStates();

        ArgumentCaptor<TaskExecutionState> stateCaptor = ArgumentCaptor.forClass(TaskExecutionState.class);
        verify(taskExecutionStateRepository).save(stateCaptor.capture());
        assertEquals(TaskExecutionStatus.FAILED.name(), stateCaptor.getValue().getStatus());
        verify(dlqRecordRepository).save(any());
    }
}

