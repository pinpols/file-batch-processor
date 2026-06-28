package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.batch.scheduler.TaskPriority;
import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.batch.scheduler.TriggerType;
import com.example.filebatchprocessor.config.BatchTimezoneProvider;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskDependency;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskTrigger;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskOrchestrationRegistry {

    private final TaskConfigService taskConfigService;
    private final TaskSchedulerService schedulerService;
    private final Environment environment;
    private final BatchTimezoneProvider timezoneProvider;

    public TaskOrchestrationRegistry(
            TaskConfigService taskConfigService,
            TaskSchedulerService schedulerService,
            Environment environment,
            BatchTimezoneProvider timezoneProvider) {
        this.taskConfigService = taskConfigService;
        this.schedulerService = schedulerService;
        this.environment = environment;
        this.timezoneProvider = timezoneProvider;
    }

    public int registerEnabledDbTasks() {
        int registered = 0;
        for (TaskDefinition taskDefinition : taskConfigService.getAllEnabledTasks()) {
            try {
                TaskTrigger taskTrigger = taskConfigService.getTaskTrigger(taskDefinition.getTaskId());
                Map<String, String> parameters = resolveTaskParameters(
                        taskConfigService.getTaskParametersAsMap(taskDefinition.getTaskId()), environment);
                List<TaskDependency> dependencyConfigs =
                        taskConfigService.getTaskDependencyConfigs(taskDefinition.getTaskId());
                schedulerService.register(toOrchestrationTask(
                        taskDefinition, taskTrigger, parameters, dependencyConfigs, timezoneProvider.zoneId()));
                registered++;
            } catch (Exception ex) {
                log.error("Skip task registration due to invalid config: taskId={}", taskDefinition.getTaskId(), ex);
            }
        }
        return registered;
    }

    private Map<String, String> resolveTaskParameters(Map<String, String> rawParameters, Environment environment) {
        Map<String, String> resolved = new HashMap<>();
        if (rawParameters == null || rawParameters.isEmpty()) {
            return resolved;
        }
        rawParameters.forEach((k, v) -> resolved.put(k, v == null ? null : environment.resolvePlaceholders(v)));
        return resolved;
    }

    private OrchestrationTaskDefinition toOrchestrationTask(
            TaskDefinition taskDefinition,
            TaskTrigger trigger,
            Map<String, String> parameters,
            List<TaskDependency> dependencyConfigs,
            ZoneId zoneId) {
        OrchestrationTaskTrigger mappedTrigger = OrchestrationTaskTrigger.builder()
                .type(TriggerType.valueOf(trigger.getTriggerType().toUpperCase(Locale.ROOT)))
                .cron(trigger.getCronExpression())
                .fixedRateMs(trigger.getFixedRateMs())
                .fixedDelayMs(trigger.getFixedDelayMs())
                .oneTimeAt(
                        trigger.getOneTimeAt() == null
                                ? null
                                : trigger.getOneTimeAt().atZone(zoneId).toInstant())
                .build();

        TaskPriority priority = TaskPriority.NORMAL;
        if (taskDefinition.getPriority() != null
                && !taskDefinition.getPriority().isBlank()) {
            priority = TaskPriority.valueOf(taskDefinition.getPriority().toUpperCase(Locale.ROOT));
        }

        OrchestrationTaskDefinition orchestrationTaskDefinition = new OrchestrationTaskDefinition();
        orchestrationTaskDefinition.setId(taskDefinition.getTaskId());
        orchestrationTaskDefinition.setJobName(taskDefinition.getJobName());
        orchestrationTaskDefinition.setTenantId(taskDefinition.getTenantId());
        orchestrationTaskDefinition.setBizDomain(taskDefinition.getBizDomain());
        orchestrationTaskDefinition.setEnv(taskDefinition.getEnv());
        orchestrationTaskDefinition.setSlaMaxDurationMs(taskDefinition.getSlaMaxDurationMs());
        orchestrationTaskDefinition.setSlaMaxQueueDelayMs(taskDefinition.getSlaMaxQueueDelayMs());
        orchestrationTaskDefinition.setRateLimitPerMinute(taskDefinition.getRateLimitPerMinute());
        orchestrationTaskDefinition.setPriority(priority);
        orchestrationTaskDefinition.setAllowParallel(Boolean.TRUE.equals(taskDefinition.getAllowParallel()));
        orchestrationTaskDefinition.setDedupKey(taskDefinition.getDedupKey());
        orchestrationTaskDefinition.setEnabled(taskDefinition.getEnabled());
        orchestrationTaskDefinition.setTimeoutMs(taskDefinition.getTimeoutMs());
        orchestrationTaskDefinition.setMaxQueueWaitMs(taskDefinition.getMaxQueueWaitMs());
        orchestrationTaskDefinition.setDependencyTimeoutMs(taskDefinition.getDependencyTimeoutMs());
        orchestrationTaskDefinition.setRerunWindowMs(taskDefinition.getRerunWindowMs());
        orchestrationTaskDefinition.setMaxAttempts(taskDefinition.getMaxAttempts());
        orchestrationTaskDefinition.setRetryBackoffMs(taskDefinition.getRetryBackoffMs());
        orchestrationTaskDefinition.setDynamicShardMax(taskDefinition.getDynamicShardMax());
        orchestrationTaskDefinition.setTrigger(mappedTrigger);
        orchestrationTaskDefinition.setParameters(parameters);
        orchestrationTaskDefinition.setDependencies(dependencyConfigs.stream()
                .map(TaskDependency::getDependsOnTaskId)
                .toList());

        Map<String, Long> depTimeouts = new HashMap<>();
        Map<String, String> depFailureActions = new HashMap<>();
        for (TaskDependency dep : dependencyConfigs) {
            depTimeouts.put(dep.getDependsOnTaskId(), dep.getDependencyTimeoutMs());
            depFailureActions.put(dep.getDependsOnTaskId(), dep.getOnFailureAction());
        }
        orchestrationTaskDefinition.setDependencyTimeoutByTask(depTimeouts);
        orchestrationTaskDefinition.setDependencyFailureActionByTask(depFailureActions);
        return orchestrationTaskDefinition;
    }
}
