package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.DagDefinition;
import com.example.filebatchprocessor.model.DagNode;
import com.example.filebatchprocessor.model.DagNodeRun;
import com.example.filebatchprocessor.model.DagRun;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskDependency;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.model.TaskParameter;
import com.example.filebatchprocessor.repository.DagDefinitionRepository;
import com.example.filebatchprocessor.repository.DagNodeRepository;
import com.example.filebatchprocessor.repository.DagNodeRunRepository;
import com.example.filebatchprocessor.repository.DagRunRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskDependencyRepository;
import com.example.filebatchprocessor.repository.TaskParameterRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DagOrchestratorService {

    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DagDefinitionRepository dagDefinitionRepository;
    private final DagNodeRepository dagNodeRepository;
    private final DagRunRepository dagRunRepository;
    private final DagNodeRunRepository dagNodeRunRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskParameterRepository taskParameterRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskExecutionStateService taskExecutionStateService;
    private final JobOperator jobOperator;
    private final ObjectProvider<Map<String, Job>> jobsProvider;

    public DagOrchestratorService(
            DagDefinitionRepository dagDefinitionRepository,
            DagNodeRepository dagNodeRepository,
            DagRunRepository dagRunRepository,
            DagNodeRunRepository dagNodeRunRepository,
            TaskDefinitionRepository taskDefinitionRepository,
            TaskParameterRepository taskParameterRepository,
            TaskDependencyRepository taskDependencyRepository,
            TaskExecutionStateService taskExecutionStateService,
            JobOperator jobOperator,
            ObjectProvider<Map<String, Job>> jobsProvider) {
        this.dagDefinitionRepository = dagDefinitionRepository;
        this.dagNodeRepository = dagNodeRepository;
        this.dagRunRepository = dagRunRepository;
        this.dagNodeRunRepository = dagNodeRunRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskParameterRepository = taskParameterRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskExecutionStateService = taskExecutionStateService;
        this.jobOperator = jobOperator;
        this.jobsProvider = jobsProvider;
    }

    @Transactional
    public DagRun executeDag(String dagId, String batchDate, String rerunId) {
        DagDefinition dag = dagDefinitionRepository
                .findByDagIdAndEnabledTrue(dagId)
                .orElseThrow(() -> new IllegalArgumentException("DAG not found or disabled: " + dagId));

        String normalizedBatchDate = normalizeBatchDate(batchDate);
        String normalizedRerunId = rerunId == null ? "" : rerunId;

        DagRun run = new DagRun();
        run.setDagId(dagId);
        run.setBatchDate(normalizedBatchDate);
        run.setRerunId(normalizedRerunId);
        run.setStatus(TaskExecutionStatus.RUNNING.name());
        run.setStartedAt(LocalDateTime.now());
        run = dagRunRepository.save(run);

        try {
            executeDagInternal(dag, run);
            return dagRunRepository.save(run);
        } catch (Exception ex) {
            run.setStatus(TaskExecutionStatus.FAILED.name());
            run.setMessage(trim("DAG failed: " + ex.getMessage(), 1000));
            run.setEndedAt(LocalDateTime.now());
            dagRunRepository.save(run);
            throw ex;
        }
    }

    private void executeDagInternal(DagDefinition dag, DagRun run) {
        List<DagNode> nodes = dagNodeRepository.findByDagIdAndEnabledTrueOrderByNodeOrderAscIdAsc(dag.getDagId());
        if (nodes.isEmpty()) {
            run.setStatus(TaskExecutionStatus.SKIPPED.name());
            run.setMessage("No enabled nodes in DAG");
            run.setEndedAt(LocalDateTime.now());
            return;
        }

        List<String> taskIds = nodes.stream().map(DagNode::getTaskId).toList();
        Map<String, TaskDefinition> taskDefs = taskDefinitionRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(TaskDefinition::getTaskId, Function.identity()));
        Map<String, List<TaskParameter>> taskParams = taskParameterRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskParameter::getTaskId));
        Map<String, List<TaskDependency>> dependencyMap = taskDependencyRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskDependency::getTaskId));

        Set<String> pending = new LinkedHashSet<>(taskIds);
        Map<String, TaskExecutionStatus> nodeStatus = new HashMap<>();
        Instant dagStart = Instant.now();
        long maxDurationMs = dag.getMaxDurationMs() == null ? 0L : Math.max(0L, dag.getMaxDurationMs());
        boolean failFast = Boolean.TRUE.equals(dag.getFailFast());

        while (!pending.isEmpty()) {
            boolean progressed = false;
            List<String> round = new ArrayList<>(pending);
            for (String taskId : round) {
                if (maxDurationMs > 0 && Instant.now().toEpochMilli() - dagStart.toEpochMilli() > maxDurationMs) {
                    markRemainingAsFailed(run, pending, nodeStatus, "DAG max duration exceeded");
                    pending.clear();
                    break;
                }

                DependencyState depState =
                        resolveDependencies(taskId, dependencyMap.getOrDefault(taskId, List.of()), nodeStatus);
                if (depState == DependencyState.WAITING) {
                    continue;
                }

                progressed = true;
                pending.remove(taskId);

                if (depState == DependencyState.SKIPPED) {
                    markNode(run, taskId, TaskExecutionStatus.SKIPPED, "Dependency failed and policy=SKIP");
                    nodeStatus.put(taskId, TaskExecutionStatus.SKIPPED);
                    continue;
                }
                if (depState == DependencyState.FAILED) {
                    markNode(run, taskId, TaskExecutionStatus.FAILED, "Dependency failed and policy=FAIL");
                    nodeStatus.put(taskId, TaskExecutionStatus.FAILED);
                    if (failFast) {
                        markRemainingAsSkipped(run, pending, nodeStatus, "DAG fail-fast");
                        pending.clear();
                    }
                    continue;
                }

                TaskDefinition taskDefinition = taskDefs.get(taskId);
                if (taskDefinition == null) {
                    markNode(run, taskId, TaskExecutionStatus.FAILED, "Task definition missing: " + taskId);
                    nodeStatus.put(taskId, TaskExecutionStatus.FAILED);
                    if (failFast) {
                        markRemainingAsSkipped(run, pending, nodeStatus, "DAG fail-fast");
                        pending.clear();
                    }
                    continue;
                }

                NodeExecutionResult result =
                        executeNode(run, taskDefinition, taskParams.getOrDefault(taskId, List.of()));
                nodeStatus.put(taskId, result.status());
                markNode(run, taskId, result.status(), result.message());
                if (result.status() == TaskExecutionStatus.FAILED && failFast) {
                    markRemainingAsSkipped(run, pending, nodeStatus, "DAG fail-fast");
                    pending.clear();
                }
            }

            if (!progressed && !pending.isEmpty()) {
                markRemainingAsFailed(
                        run, pending, nodeStatus, "No progress, unresolved dependency loop or missing upstream status");
                pending.clear();
            }
        }

        finalizeDagStatus(run, nodeStatus);
    }

    private DependencyState resolveDependencies(
            String taskId, List<TaskDependency> dependencies, Map<String, TaskExecutionStatus> nodeStatus) {
        for (TaskDependency dep : dependencies) {
            TaskExecutionStatus depStatus = nodeStatus.get(dep.getDependsOnTaskId());
            if (depStatus == null
                    || depStatus == TaskExecutionStatus.RUNNING
                    || depStatus == TaskExecutionStatus.BLOCKED
                    || depStatus == TaskExecutionStatus.READY) {
                return DependencyState.WAITING;
            }
            if (depStatus == TaskExecutionStatus.FAILED || depStatus == TaskExecutionStatus.PARTIAL) {
                String action = dep.getOnFailureAction() == null
                        ? "FAIL"
                        : dep.getOnFailureAction().trim().toUpperCase(Locale.ROOT);
                if ("SKIP".equals(action)) {
                    return DependencyState.SKIPPED;
                }
                if ("IGNORE".equals(action)) {
                    continue;
                }
                return DependencyState.FAILED;
            }
        }
        return DependencyState.READY;
    }

    private NodeExecutionResult executeNode(DagRun run, TaskDefinition taskDefinition, List<TaskParameter> parameters) {
        Map<String, Job> jobs = jobsProvider.getIfAvailable();
        if (jobs == null || !jobs.containsKey(taskDefinition.getJobName())) {
            return new NodeExecutionResult(
                    TaskExecutionStatus.FAILED, "Job bean not found: " + taskDefinition.getJobName());
        }

        Job job = jobs.get(taskDefinition.getJobName());
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("task.id", taskDefinition.getTaskId())
                .addString("batchDate", run.getBatchDate())
                .addString("rerunId", run.getRerunId());

        StringBuilder paramPreview = new StringBuilder();
        for (TaskParameter parameter : parameters) {
            String value = resolveTemplate(parameter.getParamValue(), run.getBatchDate(), run.getRerunId());
            builder.addString(parameter.getParamName(), value == null ? "" : value);
            if (paramPreview.length() > 0) {
                paramPreview.append('&');
            }
            paramPreview.append(parameter.getParamName()).append('=').append(value);
        }
        builder.addString("job.param", paramPreview.toString());

        Instant started = Instant.now();
        taskExecutionStateService.upsert(
                taskDefinition.getTaskId(),
                run.getBatchDate(),
                run.getRerunId(),
                TaskExecutionStatus.RUNNING.name(),
                taskDefinition.getMaxAttempts(),
                LocalDateTime.now(),
                null,
                null,
                null,
                false,
                null);
        try {
            JobExecution execution = jobOperator.run(job, builder.toJobParameters());
            BatchStatus status = execution.getStatus();
            TaskExecutionStatus normalized = convertBatchStatus(status, execution.getStepExecutions());
            taskExecutionStateService.upsert(
                    taskDefinition.getTaskId(),
                    run.getBatchDate(),
                    run.getRerunId(),
                    normalized.name(),
                    taskDefinition.getMaxAttempts(),
                    LocalDateTime.now(),
                    null,
                    normalized == TaskExecutionStatus.FAILED ? "Node execution failed" : null,
                    null,
                    normalized == TaskExecutionStatus.FAILED,
                    null);
            long duration = Instant.now().toEpochMilli() - started.toEpochMilli();
            return new NodeExecutionResult(normalized, "executionId=" + execution.getId() + ",durationMs=" + duration);
        } catch (Exception ex) {
            taskExecutionStateService.upsert(
                    taskDefinition.getTaskId(),
                    run.getBatchDate(),
                    run.getRerunId(),
                    TaskExecutionStatus.FAILED.name(),
                    taskDefinition.getMaxAttempts(),
                    LocalDateTime.now(),
                    null,
                    trim(ex.getMessage(), 1000),
                    null,
                    true,
                    null);
            return new NodeExecutionResult(TaskExecutionStatus.FAILED, trim("Exception: " + ex.getMessage(), 1000));
        }
    }

    private TaskExecutionStatus convertBatchStatus(BatchStatus status, Collection<StepExecution> stepExecutions) {
        if (status == BatchStatus.FAILED || status == BatchStatus.STOPPED || status == BatchStatus.ABANDONED) {
            return TaskExecutionStatus.FAILED;
        }
        long skip =
                stepExecutions.stream().mapToLong(StepExecution::getSkipCount).sum();
        if (skip > 0) {
            return TaskExecutionStatus.PARTIAL;
        }
        return TaskExecutionStatus.SUCCESS;
    }

    private void markNode(DagRun run, String taskId, TaskExecutionStatus status, String message) {
        DagNodeRun nodeRun = dagNodeRunRepository
                .findByDagRunIdAndTaskId(run.getId(), taskId)
                .orElseGet(DagNodeRun::new);
        if (nodeRun.getId() == null) {
            nodeRun.setDagRunId(run.getId());
            nodeRun.setTaskId(taskId);
            nodeRun.setStartedAt(LocalDateTime.now());
        }
        nodeRun.setStatus(status.name());
        nodeRun.setErrorMessage(message);
        nodeRun.setEndedAt(LocalDateTime.now());
        dagNodeRunRepository.save(nodeRun);
    }

    private void markRemainingAsFailed(
            DagRun run, Set<String> pending, Map<String, TaskExecutionStatus> nodeStatus, String reason) {
        for (String taskId : pending) {
            nodeStatus.put(taskId, TaskExecutionStatus.FAILED);
            markNode(run, taskId, TaskExecutionStatus.FAILED, reason);
        }
    }

    private void markRemainingAsSkipped(
            DagRun run, Set<String> pending, Map<String, TaskExecutionStatus> nodeStatus, String reason) {
        for (String taskId : pending) {
            nodeStatus.put(taskId, TaskExecutionStatus.SKIPPED);
            markNode(run, taskId, TaskExecutionStatus.SKIPPED, reason);
        }
    }

    private void finalizeDagStatus(DagRun run, Map<String, TaskExecutionStatus> nodeStatus) {
        boolean anyFailed = nodeStatus.values().stream().anyMatch(s -> s == TaskExecutionStatus.FAILED);
        boolean anyPartialOrSkipped = nodeStatus.values().stream()
                .anyMatch(s -> s == TaskExecutionStatus.PARTIAL || s == TaskExecutionStatus.SKIPPED);
        if (anyFailed) {
            run.setStatus(TaskExecutionStatus.FAILED.name());
        } else if (anyPartialOrSkipped) {
            run.setStatus(TaskExecutionStatus.PARTIAL.name());
        } else {
            run.setStatus(TaskExecutionStatus.SUCCESS.name());
        }
        run.setEndedAt(LocalDateTime.now());
        run.setMessage("nodes=" + nodeStatus.size());
    }

    private String resolveTemplate(String raw, String batchDate, String rerunId) {
        if (raw == null) {
            return "";
        }
        return raw.replace("${batchDate}", batchDate).replace("${rerunId}", rerunId == null ? "" : rerunId);
    }

    private String normalizeBatchDate(String batchDate) {
        if (batchDate == null || batchDate.isBlank()) {
            return LocalDate.now().format(BATCH_DATE_FORMATTER);
        }
        return batchDate;
    }

    private String trim(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private enum DependencyState {
        READY,
        WAITING,
        FAILED,
        SKIPPED
    }

    private record NodeExecutionResult(TaskExecutionStatus status, String message) {}
}
