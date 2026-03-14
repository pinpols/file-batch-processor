package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.DagDefinition;
import com.example.filebatchprocessor.model.DagNode;
import com.example.filebatchprocessor.model.DagRun;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.repository.DagDefinitionRepository;
import com.example.filebatchprocessor.repository.DagNodeRepository;
import com.example.filebatchprocessor.repository.DagNodeRunRepository;
import com.example.filebatchprocessor.repository.DagRunRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskDependencyRepository;
import com.example.filebatchprocessor.repository.TaskParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DagOrchestratorServiceTest {

    @Mock
    private DagDefinitionRepository dagDefinitionRepository;
    @Mock
    private DagNodeRepository dagNodeRepository;
    @Mock
    private DagRunRepository dagRunRepository;
    @Mock
    private DagNodeRunRepository dagNodeRunRepository;
    @Mock
    private TaskDefinitionRepository taskDefinitionRepository;
    @Mock
    private TaskParameterRepository taskParameterRepository;
    @Mock
    private TaskDependencyRepository taskDependencyRepository;
    @Mock
    private TaskExecutionStateService taskExecutionStateService;
    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private ObjectProvider<Map<String, Job>> jobsProvider;
    @Mock
    private Job job;

    private DagOrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new DagOrchestratorService(
                dagDefinitionRepository,
                dagNodeRepository,
                dagRunRepository,
                dagNodeRunRepository,
                taskDefinitionRepository,
                taskParameterRepository,
                taskDependencyRepository,
                taskExecutionStateService,
                jobLauncher,
                jobsProvider
        );
    }

    @Test
    void shouldThrowWhenDagNotFound() {
        when(dagDefinitionRepository.findByDagIdAndEnabledTrue("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.executeDag("missing", "2026-03-14", "r1"));
    }

    @Test
    void shouldMarkSkippedWhenNoEnabledNodes() {
        DagDefinition dag = new DagDefinition();
        dag.setDagId("dag-1");
        dag.setDagName("Dag One");
        dag.setEnabled(true);
        when(dagDefinitionRepository.findByDagIdAndEnabledTrue("dag-1")).thenReturn(Optional.of(dag));
        when(dagRunRepository.save(any(DagRun.class))).thenAnswer(invocation -> {
            DagRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(1L);
            }
            return run;
        });
        when(dagNodeRepository.findByDagIdAndEnabledTrueOrderByNodeOrderAscIdAsc("dag-1")).thenReturn(List.of());

        DagRun result = service.executeDag("dag-1", "2026-03-14", "r1");

        assertEquals("SKIPPED", result.getStatus());
        verify(dagRunRepository, atLeastOnce()).save(any(DagRun.class));
    }

    @Test
    void shouldExecuteSingleNodeSuccessfully() throws Exception {
        DagDefinition dag = new DagDefinition();
        dag.setDagId("dag-2");
        dag.setDagName("Dag Two");
        dag.setEnabled(true);
        dag.setFailFast(true);
        when(dagDefinitionRepository.findByDagIdAndEnabledTrue("dag-2")).thenReturn(Optional.of(dag));

        DagNode node = new DagNode();
        node.setDagId("dag-2");
        node.setTaskId("task-1");
        node.setNodeOrder(1);
        when(dagNodeRepository.findByDagIdAndEnabledTrueOrderByNodeOrderAscIdAsc("dag-2")).thenReturn(List.of(node));

        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setTaskId("task-1");
        taskDefinition.setJobName("importJob");
        taskDefinition.setMaxAttempts(3);
        when(taskDefinitionRepository.findByTaskIdIn(List.of("task-1"))).thenReturn(List.of(taskDefinition));
        when(taskParameterRepository.findByTaskIdIn(List.of("task-1"))).thenReturn(List.of());
        when(taskDependencyRepository.findByTaskIdIn(List.of("task-1"))).thenReturn(List.of());
        when(jobsProvider.getIfAvailable()).thenReturn(Map.of("importJob", job));

        JobExecution execution = new JobExecution(10L, new JobInstance(11L, "importJob"), new JobParameters());
        execution.setStatus(BatchStatus.COMPLETED);
        StepExecution stepExecution = new StepExecution("s1", execution);
        execution.addStepExecutions(List.of(stepExecution));
        when(jobLauncher.run(any(Job.class), any())).thenReturn(execution);

        when(dagRunRepository.save(any(DagRun.class))).thenAnswer(invocation -> {
            DagRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(2L);
            }
            return run;
        });
        when(dagNodeRunRepository.findByDagRunIdAndTaskId(any(), any())).thenReturn(Optional.empty());

        DagRun result = service.executeDag("dag-2", "2026-03-14", "r1");

        assertEquals("SUCCESS", result.getStatus());
        verify(taskExecutionStateService, atLeastOnce()).upsert(
                anyString(), anyString(), anyString(), anyString(),
                anyInt(), any(), any(), any(), any(), anyBoolean(), any()
        );
        ArgumentCaptor<DagRun> captor = ArgumentCaptor.forClass(DagRun.class);
        verify(dagRunRepository, atLeastOnce()).save(captor.capture());
    }
}
