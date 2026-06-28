package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.service.TaskOrchestrationRegistry;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops")
public class OpsSchedulerController {

    private final TaskSchedulerService taskSchedulerService;
    private final TaskOrchestrationRegistry taskOrchestrationRegistry;

    public OpsSchedulerController(
            TaskSchedulerService taskSchedulerService, TaskOrchestrationRegistry taskOrchestrationRegistry) {
        this.taskSchedulerService = taskSchedulerService;
        this.taskOrchestrationRegistry = taskOrchestrationRegistry;
    }

    @GetMapping("/scheduler")
    public Map<String, Object> schedulerSnapshot() {
        return taskSchedulerService.schedulerSnapshot();
    }

    @PostMapping("/scheduler/trigger/{taskId}")
    public Map<String, Object> triggerTask(@PathVariable String taskId) {
        taskSchedulerService.enqueueByTaskId(taskId);
        return Map.of("taskId", taskId, "accepted", true);
    }

    @PostMapping("/scheduler/reload")
    public Map<String, Object> reloadSchedulerTasks() {
        int registered = taskOrchestrationRegistry.registerEnabledDbTasks();
        return Map.of("accepted", true, "registered", registered);
    }
}
