package com.example.filebatchprocessor.config;


import com.example.filebatchprocessor.batch.scheduler.TaskPriority;
import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.batch.scheduler.TriggerType;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskDependency;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskTrigger;
import com.example.filebatchprocessor.service.TaskConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties({TaskDefinitionProperties.class, SchedulerConcurrencyProperties.class, ImportParseErrorGateProperties.class, CircuitBreakerProperties.class})
public class TaskOrchestrationConfig {

    @Value("${orchestration.scheduler.pool-size:4}")
    private int schedulerPoolSize;

    @Value("${orchestration.config-source:db}")
    private String configSource;

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, schedulerPoolSize));
        scheduler.setThreadNamePrefix("orchestration-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public CommandLineRunner registerConfiguredTasks(TaskDefinitionProperties properties,
                                                     TaskSchedulerService schedulerService,
                                                     TaskConfigService taskConfigService,
                                                     Environment environment) {
        return _ -> {
            String source = configSource == null ? "db" : configSource.trim().toLowerCase(Locale.ROOT);
            if ("yaml".equals(source)) {
                boolean localProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                        .anyMatch(p -> "local".equalsIgnoreCase(p) || "dev".equalsIgnoreCase(p));
                if (!localProfile) {
                    throw new IllegalStateException("orchestration.config-source=yaml is allowed only for local/dev profile");
                }
                log.info("Registering orchestration tasks from YAML");
                properties.getTasks().forEach(schedulerService::register);
                return;
            }
            if (!"db".equals(source)) {
                log.warn("Unknown orchestration.config-source={}, fallback to db", configSource);
            }
            log.info("Registering orchestration tasks from database");
            for (TaskDefinition taskDefinition : taskConfigService.getAllEnabledTasks()) {
                try {
                    TaskTrigger taskTrigger = taskConfigService.getTaskTrigger(taskDefinition.getTaskId());
                    Map<String, String> parameters = resolveTaskParameters(
                            taskConfigService.getTaskParametersAsMap(taskDefinition.getTaskId()),
                            environment
                    );
                    var dependencyConfigs = taskConfigService.getTaskDependencyConfigs(taskDefinition.getTaskId());
                    schedulerService.register(toOrchestrationTask(taskDefinition, taskTrigger, parameters, dependencyConfigs));
                } catch (Exception ex) {
                    log.error("Skip task registration due to invalid config: taskId={}", taskDefinition.getTaskId(), ex);
                }
            }
        };
    }

    private Map<String, String> resolveTaskParameters(Map<String, String> rawParameters, Environment environment) {
        java.util.Map<String, String> resolved = new java.util.HashMap<>();
        if (rawParameters == null || rawParameters.isEmpty()) {
            return resolved;
        }
        rawParameters.forEach((k, v) -> {
            if (v == null) {
                resolved.put(k, null);
                return;
            }
            // Allow task parameters like ${user.dir}/... configured in DB.
            resolved.put(k, environment.resolvePlaceholders(v));
        });
        return resolved;
    }

    private OrchestrationTaskDefinition toOrchestrationTask(TaskDefinition taskDefinition,
                                                            TaskTrigger trigger,
                                                            Map<String, String> parameters,
                                                            java.util.List<TaskDependency> dependencyConfigs) {
        OrchestrationTaskTrigger mappedTrigger = OrchestrationTaskTrigger.builder()
                .type(TriggerType.valueOf(trigger.getTriggerType().toUpperCase(Locale.ROOT)))
                .cron(trigger.getCronExpression())
                .fixedRateMs(trigger.getFixedRateMs())
                .fixedDelayMs(trigger.getFixedDelayMs())
                .oneTimeAt(trigger.getOneTimeAt() == null
                        ? null
                        : trigger.getOneTimeAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
        TaskPriority priority = TaskPriority.NORMAL;
        if (taskDefinition.getPriority() != null && !taskDefinition.getPriority().isBlank()) {
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
        orchestrationTaskDefinition.setTimeoutMs(taskDefinition.getTimeoutMs());
        orchestrationTaskDefinition.setMaxQueueWaitMs(taskDefinition.getMaxQueueWaitMs());
        orchestrationTaskDefinition.setDependencyTimeoutMs(taskDefinition.getDependencyTimeoutMs());
        orchestrationTaskDefinition.setRerunWindowMs(taskDefinition.getRerunWindowMs());
        orchestrationTaskDefinition.setMaxAttempts(taskDefinition.getMaxAttempts());
        orchestrationTaskDefinition.setRetryBackoffMs(taskDefinition.getRetryBackoffMs());
        orchestrationTaskDefinition.setDynamicShardMax(taskDefinition.getDynamicShardMax());
        orchestrationTaskDefinition.setTrigger(mappedTrigger);
        orchestrationTaskDefinition.setParameters(parameters);
        java.util.List<String> dependencies = dependencyConfigs.stream()
                .map(TaskDependency::getDependsOnTaskId)
                .toList();
        java.util.Map<String, Long> depTimeouts = new java.util.HashMap<>();
        java.util.Map<String, String> depFailureActions = new java.util.HashMap<>();
        for (TaskDependency dep : dependencyConfigs) {
            depTimeouts.put(dep.getDependsOnTaskId(), dep.getDependencyTimeoutMs());
            depFailureActions.put(dep.getDependsOnTaskId(), dep.getOnFailureAction());
        }
        orchestrationTaskDefinition.setDependencies(dependencies);
        orchestrationTaskDefinition.setDependencyTimeoutByTask(depTimeouts);
        orchestrationTaskDefinition.setDependencyFailureActionByTask(depFailureActions);
        return orchestrationTaskDefinition;
    }
}
