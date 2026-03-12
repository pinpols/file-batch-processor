package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.batch.scheduler.TaskGraphManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ops")
public class OpsDagController {

    private final TaskGraphManager taskGraphManager;

    public OpsDagController(TaskGraphManager taskGraphManager) {
        this.taskGraphManager = taskGraphManager;
    }

    @GetMapping("/dag")
    public Map<String, Object> dagSnapshot() {
        return taskGraphManager.snapshot();
    }
}
