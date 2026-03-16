package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.model.DagDefinition;
import com.example.filebatchprocessor.model.DagNode;
import com.example.filebatchprocessor.model.DagNodeRun;
import com.example.filebatchprocessor.model.DagRun;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskDependency;
import com.example.filebatchprocessor.repository.DagDefinitionRepository;
import com.example.filebatchprocessor.repository.DagNodeRepository;
import com.example.filebatchprocessor.repository.DagNodeRunRepository;
import com.example.filebatchprocessor.repository.DagRunRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskDependencyRepository;
import com.example.filebatchprocessor.service.DagOrchestratorService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class DagDependencyTimeoutIT extends PostgresContainerSupport {

    @Autowired
    private DagOrchestratorService dagOrchestratorService;
    @Autowired
    private DagDefinitionRepository dagDefinitionRepository;
    @Autowired
    private DagNodeRepository dagNodeRepository;
    @Autowired
    private DagRunRepository dagRunRepository;
    @Autowired
    private DagNodeRunRepository dagNodeRunRepository;
    @Autowired
    private TaskDefinitionRepository taskDefinitionRepository;
    @Autowired
    private TaskDependencyRepository taskDependencyRepository;

    @BeforeEach
    void setUp() {
        dagNodeRunRepository.deleteAll();
        dagRunRepository.deleteAll();
        dagNodeRepository.deleteAll();
        dagDefinitionRepository.deleteAll();
        taskDependencyRepository.deleteAll();
        taskDefinitionRepository.deleteAll();
    }

    @Test
    void shouldMarkRemainingNodesFailedWhenDagMaxDurationExceeded() {
        String dagId = "dag-timeout-it";
        TaskDefinition t1 = task("task-timeout-upstream", "fileReceptionJob");
        TaskDefinition t2 = task("task-timeout-downstream", "fileDistributionJob");
        taskDefinitionRepository.saveAll(List.of(t1, t2));

        dagDefinitionRepository.save(dag(dagId, false, 1L));
        dagNodeRepository.saveAll(List.of(node(dagId, t1.getTaskId(), 1), node(dagId, t2.getTaskId(), 2)));

        DagRun run = dagOrchestratorService.executeDag(dagId, "2026-03-14", "timeout-case");

        assertEquals("FAILED", run.getStatus());
        List<DagNodeRun> nodeRuns = dagNodeRunRepository.findByDagRunIdOrderByIdAsc(run.getId());
        assertEquals(2, nodeRuns.size());
        assertEquals("FAILED", nodeRuns.get(1).getStatus());
        assertTrue(
                nodeRuns.get(1).getErrorMessage() != null
                        && nodeRuns.get(1).getErrorMessage().contains("DAG max duration exceeded"),
                "downstream node should fail with max-duration reason"
        );
    }

    @Test
    void shouldSkipDownstreamNodeWhenDependencyFailedAndActionIsSkip() {
        String dagId = "dag-dep-skip-it";
        TaskDefinition upstream = task("task-upstream-fail", "missingJobBean");
        TaskDefinition downstream = task("task-downstream-skip", "missingJobBean-2");
        taskDefinitionRepository.saveAll(List.of(upstream, downstream));

        TaskDependency dependency = new TaskDependency();
        dependency.setTaskId(downstream.getTaskId());
        dependency.setDependsOnTaskId(upstream.getTaskId());
        dependency.setOnFailureAction("SKIP");
        taskDependencyRepository.save(dependency);

        dagDefinitionRepository.save(dag(dagId, false, 60_000L));
        dagNodeRepository.saveAll(List.of(node(dagId, upstream.getTaskId(), 1), node(dagId, downstream.getTaskId(), 2)));

        DagRun run = dagOrchestratorService.executeDag(dagId, "2026-03-14", "dep-skip-case");

        assertEquals("FAILED", run.getStatus());
        List<DagNodeRun> nodeRuns = dagNodeRunRepository.findByDagRunIdOrderByIdAsc(run.getId());
        assertEquals(2, nodeRuns.size());
        assertEquals("FAILED", nodeRuns.get(0).getStatus());
        assertEquals("SKIPPED", nodeRuns.get(1).getStatus());
        assertTrue(nodeRuns.get(1).getErrorMessage() != null && nodeRuns.get(1).getErrorMessage().contains("policy=SKIP"));
    }

    private TaskDefinition task(String taskId, String jobName) {
        TaskDefinition t = new TaskDefinition();
        t.setTaskId(taskId);
        t.setJobName(jobName);
        t.setEnabled(true);
        t.setAllowParallel(true);
        t.setMaxAttempts(1);
        t.setPriority("NORMAL");
        t.setTimeoutMs(1000L);
        t.setRetryBackoffMs(1000L);
        return t;
    }

    private DagDefinition dag(String dagId, boolean failFast, Long maxDurationMs) {
        DagDefinition d = new DagDefinition();
        d.setDagId(dagId);
        d.setDagName(dagId);
        d.setEnabled(true);
        d.setFailFast(failFast);
        d.setMaxDurationMs(maxDurationMs);
        return d;
    }

    private DagNode node(String dagId, String taskId, int order) {
        DagNode node = new DagNode();
        node.setDagId(dagId);
        node.setTaskId(taskId);
        node.setNodeOrder(order);
        node.setEnabled(true);
        return node;
    }
}
