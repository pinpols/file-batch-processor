package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MisfirePolicyServiceTest {

    @Mock
    private TaskExecutionStateRepository taskExecutionStateRepository;
    @Mock
    private TaskSchedulerService taskSchedulerService;
    @Mock
    private SchedulerLeaderService schedulerLeaderService;

    private MisfirePolicyService service;

    @BeforeEach
    void setUp() {
        MisfirePolicyProperties properties = new MisfirePolicyProperties();
        properties.setEnabled(true);
        properties.setDetectionWindowMs(60_000);
        properties.setRecoveryDelayMs(5_000);
        properties.setMaxRecoveryAttempts(3);
        service = new MisfirePolicyService(
                taskExecutionStateRepository,
                taskSchedulerService,
                schedulerLeaderService,
                properties
        );
    }

    @Test
    void shouldSkipDetectionWhenNotLeader() {
        when(schedulerLeaderService.isLeader()).thenReturn(false);

        service.detectAndHandleMisfires();

        verify(taskExecutionStateRepository, never()).findAll();
    }

    @Test
    void shouldIgnoreStateThatIsNotPastDetectionThreshold() {
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        TaskExecutionState stale = new TaskExecutionState();
        stale.setTaskId("task-a");
        stale.setStatus(TaskExecutionStatus.READY.name());
        stale.setAttempt(0);
        stale.setNextRetryAt(LocalDateTime.now().minusSeconds(20));
        when(taskExecutionStateRepository.findAll()).thenReturn(List.of(stale));

        service.detectAndHandleMisfires();

        verify(taskExecutionStateRepository, never()).save(any(TaskExecutionState.class));
        verify(taskSchedulerService, never()).enqueueByTaskId("task-a");
    }

    @Test
    void shouldAbandonAfterMaxAttempts() {
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        TaskExecutionState stale = new TaskExecutionState();
        stale.setTaskId("task-b");
        stale.setStatus(TaskExecutionStatus.READY.name());
        stale.setAttempt(3);
        stale.setNextRetryAt(LocalDateTime.now().minusMinutes(5));
        when(taskExecutionStateRepository.findAll()).thenReturn(List.of(stale));
        when(taskExecutionStateRepository.save(any(TaskExecutionState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.detectAndHandleMisfires();

        ArgumentCaptor<TaskExecutionState> captor = ArgumentCaptor.forClass(TaskExecutionState.class);
        verify(taskExecutionStateRepository).save(captor.capture());
        assertEquals(TaskExecutionStatus.FAILED.name(), captor.getValue().getStatus());
        verify(taskSchedulerService, never()).enqueueByTaskId("task-b");
    }
}
