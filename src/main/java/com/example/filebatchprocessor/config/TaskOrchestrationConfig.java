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
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Value("${orchestration.enabled:true}")
    private boolean orchestrationEnabled;

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
                                                     Scheduler quartzScheduler,
                                                     JdbcTemplate jdbcTemplate,
                                                     Environment environment) {
        return _ -> {
            boolean startQuartzAfterRegistration = false;
            int expectedTriggerCount = 0;
            try {
                if (quartzScheduler.isStarted() && !quartzScheduler.isInStandbyMode()) {
                    quartzScheduler.standby();
                    startQuartzAfterRegistration = true;
                    log.info("Quartz put into standby before orchestration task registration");
                } else if (quartzScheduler.isStarted()) {
                    startQuartzAfterRegistration = true;
                    log.info("Quartz already in standby before orchestration task registration");
                } else {
                    startQuartzAfterRegistration = true;
                    log.info("Quartz is not started yet; will start after orchestration task registration");
                }
            } catch (SchedulerException e) {
                throw new IllegalStateException("Failed to prepare Quartz before task registration", e);
            }
            try {
                if (!orchestrationEnabled) {
                    log.info("Orchestration registration disabled by orchestration.enabled=false");
                    return;
                }
                String source = configSource == null ? "db" : configSource.trim().toLowerCase(Locale.ROOT);
                if ("yaml".equals(source)) {
                    boolean localProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                            .anyMatch(p -> "local".equalsIgnoreCase(p) || "dev".equalsIgnoreCase(p));
                    if (!localProfile) {
                        throw new IllegalStateException("orchestration.config-source=yaml is allowed only for local/dev profile");
                    }
                    log.info("Registering orchestration tasks from YAML");
                    for (OrchestrationTaskDefinition task : properties.getTasks()) {
                        if (task != null && task.getTrigger() != null) {
                            expectedTriggerCount++;
                        }
                        schedulerService.register(task);
                    }
                    replayYamlRegistrationIfSubtypeMissing(properties, schedulerService, quartzScheduler, jdbcTemplate);
                } else {
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
                            if (taskTrigger != null) {
                                expectedTriggerCount++;
                            }
                        } catch (Exception ex) {
                            log.error("Skip task registration due to invalid config: taskId={}", taskDefinition.getTaskId(), ex);
                        }
                    }
                }
            } finally {
                resumeQuartzIfNecessary(quartzScheduler, startQuartzAfterRegistration, jdbcTemplate, expectedTriggerCount);
            }
        };
    }

    private void replayYamlRegistrationIfSubtypeMissing(TaskDefinitionProperties properties,
                                                        TaskSchedulerService schedulerService,
                                                        Scheduler quartzScheduler,
                                                        JdbcTemplate jdbcTemplate) {
        try {
            String schedName = quartzScheduler.getSchedulerName();
            Integer missingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) " +
                            "FROM qrtz_triggers t " +
                            "LEFT JOIN qrtz_simple_triggers st ON st.sched_name=t.sched_name AND st.trigger_name=t.trigger_name AND st.trigger_group=t.trigger_group " +
                            "LEFT JOIN qrtz_cron_triggers ct ON ct.sched_name=t.sched_name AND ct.trigger_name=t.trigger_name AND ct.trigger_group=t.trigger_group " +
                            "WHERE t.sched_name=? AND ((t.trigger_type='SIMPLE' AND st.trigger_name IS NULL) OR (t.trigger_type='CRON' AND ct.trigger_name IS NULL))",
                    Integer.class,
                    schedName
            );
            int missing = missingCount == null ? 0 : missingCount;
            if (missing <= 0) {
                return;
            }
            log.warn("Detected {} Quartz trigger subtype missing rows before scheduler start, replaying YAML task registration", missing);
            properties.getTasks().forEach(schedulerService::register);
        } catch (Exception e) {
            log.warn("Failed to validate Quartz trigger subtype consistency before start", e);
        }
    }

    private void resumeQuartzIfNecessary(Scheduler quartzScheduler,
                                         boolean startQuartzAfterRegistration,
                                         JdbcTemplate jdbcTemplate,
                                         int expectedTriggerCount) {
        if (!startQuartzAfterRegistration) {
            return;
        }
        try {
            waitForQuartzTriggerSubtypeConsistency(quartzScheduler, jdbcTemplate, expectedTriggerCount);
            quartzScheduler.start();
            log.info("Quartz resumed after orchestration task registration");
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to resume Quartz after task registration", e);
        }
    }

    private void waitForQuartzTriggerSubtypeConsistency(Scheduler quartzScheduler,
                                                        JdbcTemplate jdbcTemplate,
                                                        int expectedTriggerCount) {
        try {
            String schedName = quartzScheduler.getSchedulerName();
            int stableRounds = 0;
            for (int i = 0; i < 40; i++) {
                Integer triggerCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM qrtz_triggers WHERE sched_name=?",
                        Integer.class,
                        schedName
                );
                Integer missingCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) " +
                                "FROM qrtz_triggers t " +
                                "LEFT JOIN qrtz_simple_triggers st ON st.sched_name=t.sched_name AND st.trigger_name=t.trigger_name AND st.trigger_group=t.trigger_group " +
                                "LEFT JOIN qrtz_cron_triggers ct ON ct.sched_name=t.sched_name AND ct.trigger_name=t.trigger_name AND ct.trigger_group=t.trigger_group " +
                                "WHERE t.sched_name=? AND ((t.trigger_type='SIMPLE' AND st.trigger_name IS NULL) OR (t.trigger_type='CRON' AND ct.trigger_name IS NULL))",
                        Integer.class,
                        schedName
                );
                int triggerRows = triggerCount == null ? 0 : triggerCount;
                int missing = missingCount == null ? 0 : missingCount;
                boolean triggerReady = triggerRows >= Math.max(0, expectedTriggerCount);
                boolean subtypeReady = missing == 0;
                if (triggerReady && subtypeReady) {
                    stableRounds++;
                } else {
                    stableRounds = 0;
                }
                if (stableRounds >= 3) {
                    return;
                }
                if (i == 0 || i % 10 == 9 || i == 39) {
                    log.warn(
                            "Quartz trigger rows not ready yet (rows={}, expected>={}, missingSubType={}), waiting before scheduler start",
                            triggerRows,
                            Math.max(0, expectedTriggerCount),
                            missing
                    );
                }
                Thread.sleep(200L);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Failed to wait for Quartz trigger subtype consistency before start", e);
        }
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
