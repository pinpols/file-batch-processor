package com.example.filebatchprocessor.unit.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.config.BatchTimezoneProvider;
import com.example.filebatchprocessor.config.TaskDefinitionProperties;
import com.example.filebatchprocessor.config.TaskOrchestrationConfig;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.TaskOrchestrationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

class TaskOrchestrationConfigTest {

    @Test
    void shouldNotResetQuartzSchedulesByDefault() throws Exception {
        TaskOrchestrationConfig config = new TaskOrchestrationConfig();
        ReflectionTestUtils.setField(config, "orchestrationEnabled", true);
        ReflectionTestUtils.setField(config, "configSource", "db");
        ReflectionTestUtils.setField(config, "quartzResetOnStartup", false);

        TaskDefinitionProperties properties = new TaskDefinitionProperties();
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        TaskOrchestrationRegistry taskOrchestrationRegistry = mock(TaskOrchestrationRegistry.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        when(quartzScheduler.isStarted()).thenReturn(false);

        config.registerConfiguredTasks(
                        properties,
                        schedulerService,
                        taskOrchestrationRegistry,
                        quartzScheduler,
                        env,
                        new BatchTimezoneProvider("Asia/Shanghai"))
                .run();

        verify(schedulerService, never()).resetPersistedSchedules();
    }

    @Test
    void shouldResetQuartzSchedulesWhenFlagEnabled() throws Exception {
        TaskOrchestrationConfig config = new TaskOrchestrationConfig();
        ReflectionTestUtils.setField(config, "orchestrationEnabled", true);
        ReflectionTestUtils.setField(config, "configSource", "db");
        ReflectionTestUtils.setField(config, "quartzResetOnStartup", true);

        TaskDefinitionProperties properties = new TaskDefinitionProperties();
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        TaskOrchestrationRegistry taskOrchestrationRegistry = mock(TaskOrchestrationRegistry.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        when(quartzScheduler.isStarted()).thenReturn(false);

        config.registerConfiguredTasks(
                        properties,
                        schedulerService,
                        taskOrchestrationRegistry,
                        quartzScheduler,
                        env,
                        new BatchTimezoneProvider("Asia/Shanghai"))
                .run();

        verify(schedulerService, times(1)).resetPersistedSchedules();
    }

    @Test
    void shouldRegisterFromDbByDefault() throws Exception {
        TaskOrchestrationConfig config = new TaskOrchestrationConfig();
        ReflectionTestUtils.setField(config, "orchestrationEnabled", true);
        ReflectionTestUtils.setField(config, "configSource", "db");
        ReflectionTestUtils.setField(config, "quartzResetOnStartup", false);

        TaskDefinitionProperties properties = new TaskDefinitionProperties();
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        TaskOrchestrationRegistry taskOrchestrationRegistry = mock(TaskOrchestrationRegistry.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        when(quartzScheduler.isStarted()).thenReturn(false);
        CommandLineRunner runner = config.registerConfiguredTasks(
                properties,
                schedulerService,
                taskOrchestrationRegistry,
                quartzScheduler,
                env,
                new BatchTimezoneProvider("Asia/Shanghai"));
        runner.run();

        verify(taskOrchestrationRegistry, times(1)).registerEnabledDbTasks();
    }

    @Test
    void shouldRejectYamlOutsideLocalDev() throws Exception {
        TaskOrchestrationConfig config = new TaskOrchestrationConfig();
        ReflectionTestUtils.setField(config, "orchestrationEnabled", true);
        ReflectionTestUtils.setField(config, "configSource", "yaml");
        ReflectionTestUtils.setField(config, "quartzResetOnStartup", false);

        TaskDefinitionProperties properties = new TaskDefinitionProperties();
        properties.setTasks(List.of(new OrchestrationTaskDefinition()));
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        TaskOrchestrationRegistry taskOrchestrationRegistry = mock(TaskOrchestrationRegistry.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[] {"prod"});
        when(quartzScheduler.isStarted()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> {
            config.registerConfiguredTasks(
                            properties,
                            schedulerService,
                            taskOrchestrationRegistry,
                            quartzScheduler,
                            env,
                            new BatchTimezoneProvider("Asia/Shanghai"))
                    .run();
        });
    }

    @Test
    void shouldUseRegistryForDbSource() throws Exception {
        TaskOrchestrationConfig config = new TaskOrchestrationConfig();
        ReflectionTestUtils.setField(config, "orchestrationEnabled", true);
        ReflectionTestUtils.setField(config, "configSource", "db");
        ReflectionTestUtils.setField(config, "quartzResetOnStartup", false);

        TaskDefinitionProperties properties = new TaskDefinitionProperties();
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        TaskOrchestrationRegistry taskOrchestrationRegistry = mock(TaskOrchestrationRegistry.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        when(quartzScheduler.isStarted()).thenReturn(false);
        CommandLineRunner runner = config.registerConfiguredTasks(
                properties,
                schedulerService,
                taskOrchestrationRegistry,
                quartzScheduler,
                env,
                new BatchTimezoneProvider("Asia/Shanghai"));
        runner.run();

        verify(taskOrchestrationRegistry, times(1)).registerEnabledDbTasks();
    }
}
