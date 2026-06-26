package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.batch.scheduler.TaskGraphManager;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/dag/mermaid")
    public String dagMermaid() {
        return taskGraphManager.toMermaid();
    }

    @GetMapping("/dag/topological")
    public Map<String, Object> dagTopological() {
        return taskGraphManager.topologicallySorted();
    }
}
