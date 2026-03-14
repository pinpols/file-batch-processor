package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ops")
public class OpsSchedulerController {

    private final TaskSchedulerService taskSchedulerService;

    public OpsSchedulerController(TaskSchedulerService taskSchedulerService) {
        this.taskSchedulerService = taskSchedulerService;
    }

    @GetMapping("/scheduler")
    public Map<String, Object> schedulerSnapshot() {
        return taskSchedulerService.schedulerSnapshot();
    }

    @PostMapping("/scheduler/trigger/{taskId}")
    public Map<String, Object> triggerTask(@PathVariable String taskId) {
        taskSchedulerService.enqueueByTaskId(taskId);
        return Map.of(
                "taskId", taskId,
                "accepted", true
        );
    }
}
