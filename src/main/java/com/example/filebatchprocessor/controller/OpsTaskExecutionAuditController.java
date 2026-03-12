package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.TaskExecutionAudit;
import com.example.filebatchprocessor.repository.TaskExecutionAuditRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ops/task-audit")
public class OpsTaskExecutionAuditController {

    private final TaskExecutionAuditRepository repository;

    public OpsTaskExecutionAuditController(TaskExecutionAuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TaskExecutionAudit> recent(@RequestParam(required = false) String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            return repository.findTop200ByTaskIdOrderByCreatedAtDesc(taskId);
        }
        return repository.findTop200ByOrderByCreatedAtDesc();
    }
}
