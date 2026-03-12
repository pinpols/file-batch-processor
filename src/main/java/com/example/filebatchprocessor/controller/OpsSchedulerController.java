package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import org.springframework.web.bind.annotation.GetMapping;
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
}
