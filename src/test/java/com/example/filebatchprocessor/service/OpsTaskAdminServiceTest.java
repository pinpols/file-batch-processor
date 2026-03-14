package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskTriggerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsTaskAdminServiceTest {

    @Mock
    private TaskDefinitionRepository taskDefinitionRepository;
    @Mock
    private TaskTriggerRepository taskTriggerRepository;
    @Mock
    private OpsAuditService opsAuditService;

    private OpsTaskAdminService service;

    @BeforeEach
    void setUp() {
        service = new OpsTaskAdminService(taskDefinitionRepository, taskTriggerRepository, opsAuditService);
    }

    @Test
    void shouldListTasksWithTrigger() {
        TaskDefinition def = new TaskDefinition();
        def.setTaskId("task-1");
        def.setJobName("importJob");
        def.setPriority("HIGH");
        def.setEnabled(true);
        def.setAllowParallel(true);
        TaskTrigger trigger = new TaskTrigger();
        trigger.setTaskId("task-1");
        trigger.setTriggerType("CRON");

        when(taskDefinitionRepository.findAll()).thenReturn(List.of(def));
        when(taskTriggerRepository.findByTaskId("task-1")).thenReturn(Optional.of(trigger));

        List<Map<String, Object>> rows = service.listTasks();

        assertEquals(1, rows.size());
        assertEquals("task-1", rows.get(0).get("taskId"));
        assertEquals(trigger, rows.get(0).get("trigger"));
    }

    @Test
    void shouldToggleTaskAndAudit() {
        TaskDefinition def = new TaskDefinition();
        def.setTaskId("task-2");
        def.setEnabled(true);
        when(taskDefinitionRepository.findByTaskId("task-2")).thenReturn(Optional.of(def));
        when(taskDefinitionRepository.save(any(TaskDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskDefinition saved = service.toggleTask("task-2", false, "operator");

        assertEquals(false, saved.getEnabled());
        verify(opsAuditService).log("TASK_TOGGLE", "operator", "TASK_DEFINITION", "task-2", "SUCCESS", "enabled=false");
    }

    @Test
    void shouldThrowWhenTaskNotFound() {
        when(taskDefinitionRepository.findByTaskId("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.toggleTask("missing", true, "operator"));
    }
}

