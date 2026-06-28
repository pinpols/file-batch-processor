package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.config.BatchTimezoneProvider;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.TaskConfigService;
import com.example.filebatchprocessor.service.TaskOrchestrationRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

class TaskOrchestrationRegistryTest {

    @Test
    void shouldRegisterEnabledDbTasksWithResolvedParameters() {
        TaskConfigService taskConfigService = mock(TaskConfigService.class);
        TaskSchedulerService schedulerService = mock(TaskSchedulerService.class);
        Environment environment = mock(Environment.class);
        TaskOrchestrationRegistry registry = new TaskOrchestrationRegistry(
                taskConfigService, schedulerService, environment, new BatchTimezoneProvider("Asia/Shanghai"));

        TaskDefinition definition = new TaskDefinition();
        definition.setTaskId("t-delay");
        definition.setJobName("fileReceptionJob");
        definition.setPriority("NORMAL");
        definition.setAllowParallel(true);
        definition.setEnabled(true);

        TaskTrigger trigger = new TaskTrigger();
        trigger.setTriggerType("FIXED_DELAY");
        trigger.setFixedDelayMs(2000L);

        when(taskConfigService.getAllEnabledTasks()).thenReturn(List.of(definition));
        when(taskConfigService.getTaskTrigger("t-delay")).thenReturn(trigger);
        when(taskConfigService.getTaskParametersAsMap("t-delay"))
                .thenReturn(Map.of("input.file.name", "${user.dir}/a.csv"));
        when(taskConfigService.getTaskDependencyConfigs("t-delay")).thenReturn(List.of());
        when(environment.resolvePlaceholders("${user.dir}/a.csv")).thenReturn("/workspace/a.csv");

        int registered = registry.registerEnabledDbTasks();

        ArgumentCaptor<OrchestrationTaskDefinition> captor = ArgumentCaptor.forClass(OrchestrationTaskDefinition.class);
        verify(schedulerService).register(captor.capture());
        assertEquals(1, registered);
        assertEquals("t-delay", captor.getValue().getId());
        assertEquals(2000L, captor.getValue().getTrigger().getFixedDelayMs());
        assertEquals("/workspace/a.csv", captor.getValue().getParameters().get("input.file.name"));
    }
}
