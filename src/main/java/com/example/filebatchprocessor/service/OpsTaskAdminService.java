package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskTriggerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class OpsTaskAdminService {

    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskTriggerRepository taskTriggerRepository;
    private final OpsAuditService opsAuditService;

    public OpsTaskAdminService(TaskDefinitionRepository taskDefinitionRepository,
                               TaskTriggerRepository taskTriggerRepository,
                               OpsAuditService opsAuditService) {
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskTriggerRepository = taskTriggerRepository;
        this.opsAuditService = opsAuditService;
    }

    public List<Map<String, Object>> listTasks() {
        return taskDefinitionRepository.findAll().stream()
                .map(def -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("taskId", def.getTaskId());
                    row.put("jobName", def.getJobName());
                    row.put("priority", def.getPriority());
                    row.put("enabled", def.getEnabled());
                    row.put("allowParallel", def.getAllowParallel());
                    row.put("trigger", taskTriggerRepository.findByTaskId(def.getTaskId()).orElse(null));
                    return row;
                })
                .toList();
    }

    public TaskDefinition toggleTask(String taskId, boolean enabled, String actor) {
        TaskDefinition def = taskDefinitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task definition not found: " + taskId));
        def.setEnabled(enabled);
        TaskDefinition saved = taskDefinitionRepository.save(def);
        opsAuditService.log("TASK_TOGGLE", actor, "TASK_DEFINITION", taskId, "SUCCESS", "enabled=" + enabled);
        return saved;
    }
}

