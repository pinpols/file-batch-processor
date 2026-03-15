package com.example.filebatchprocessor.unit.config;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.config.TaskDefinitionProperties;
import com.example.filebatchprocessor.config.TaskOrchestrationConfig;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.TaskConfigService;
import org.quartz.Scheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TaskOrchestrationConfigTest {

    @Test
    void shouldNotResetQuartzSchedulesByDefault() throws Exception {
        TaskOrchestrationConfig config = new TaskOrchestrationConfig();
        ReflectionTestUtils.setField(config, "orchestrationEnabled", true);
        ReflectionTestUtils.setField(config, "configSource", "db");
        ReflectionTestUtils.setField(config, "quartzResetOnStartup", false);

        TaskDefinitionProperties properties = new TaskDefinitionProperties();
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        when(taskConfigService.getAllEnabledTasks()).thenReturn(List.of());
        when(quartzScheduler.isStarted()).thenReturn(false);

        config.registerConfiguredTasks(
                properties, schedulerService, taskConfigService, quartzScheduler, env
        ).run();

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
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        when(taskConfigService.getAllEnabledTasks()).thenReturn(List.of());
        when(quartzScheduler.isStarted()).thenReturn(false);

        config.registerConfiguredTasks(
                properties, schedulerService, taskConfigService, quartzScheduler, env
        ).run();

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
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        TaskDefinition definition = new TaskDefinition();
        definition.setTaskId("t1");
        definition.setJobName("processFileJob");
        definition.setPriority("HIGH");
        definition.setAllowParallel(true);

        TaskTrigger trigger = new TaskTrigger();
        trigger.setTriggerType("FIXED_RATE");
        trigger.setFixedRateMs(1000L);

        when(taskConfigService.getAllEnabledTasks()).thenReturn(List.of(definition));
        when(taskConfigService.getTaskTrigger("t1")).thenReturn(trigger);
        when(taskConfigService.getTaskParametersAsMap("t1")).thenReturn(Map.of("k", "v"));
        when(taskConfigService.getTaskDependencyConfigs("t1")).thenReturn(List.of());

        when(quartzScheduler.isStarted()).thenReturn(false);
        CommandLineRunner runner = config.registerConfiguredTasks(
                properties, schedulerService, taskConfigService, quartzScheduler, env
        );
        runner.run();

        verify(schedulerService, times(1)).register(any(OrchestrationTaskDefinition.class));
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
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(quartzScheduler.isStarted()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> {
            config.registerConfiguredTasks(
                    properties, schedulerService, taskConfigService, quartzScheduler, env
            ).run();
        });
    }

    @Test
    void shouldMapFixedDelayTriggerFromDb() throws Exception {
        TaskOrchestrationConfig config = new TaskOrchestrationConfig();
        ReflectionTestUtils.setField(config, "orchestrationEnabled", true);
        ReflectionTestUtils.setField(config, "configSource", "db");
        ReflectionTestUtils.setField(config, "quartzResetOnStartup", false);

        TaskDefinitionProperties properties = new TaskDefinitionProperties();
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        Scheduler quartzScheduler = mock(Scheduler.class);
        Environment env = mock(Environment.class);

        TaskDefinition definition = new TaskDefinition();
        definition.setTaskId("t-delay");
        definition.setJobName("fileReceptionJob");
        definition.setPriority("NORMAL");
        definition.setAllowParallel(true);

        TaskTrigger trigger = new TaskTrigger();
        trigger.setTriggerType("FIXED_DELAY");
        trigger.setFixedDelayMs(2000L);

        when(taskConfigService.getAllEnabledTasks()).thenReturn(List.of(definition));
        when(taskConfigService.getTaskTrigger("t-delay")).thenReturn(trigger);
        when(taskConfigService.getTaskParametersAsMap("t-delay")).thenReturn(Map.of());
        when(taskConfigService.getTaskDependencyConfigs("t-delay")).thenReturn(List.of());

        when(quartzScheduler.isStarted()).thenReturn(false);
        CommandLineRunner runner = config.registerConfiguredTasks(
                properties, schedulerService, taskConfigService, quartzScheduler, env
        );
        runner.run();

        var captor = org.mockito.ArgumentCaptor.forClass(OrchestrationTaskDefinition.class);
        verify(schedulerService, times(1)).register(captor.capture());
        assertEquals(2000L, captor.getValue().getTrigger().getFixedDelayMs());
    }
}
