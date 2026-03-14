package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.TaskExecutionAudit;
import com.example.filebatchprocessor.repository.TaskExecutionAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/page")
    public Page<TaskExecutionAudit> page(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        if (taskId != null && !taskId.isBlank() && startTime != null && endTime != null) {
            return repository.findByTaskIdAndCreatedAtBetween(taskId, startTime, endTime, pageRequest);
        }
        
        if (taskId != null && !taskId.isBlank()) {
            return repository.findByTaskId(taskId, pageRequest);
        }
        
        if (status != null && !status.isBlank()) {
            return repository.findByStatus(status, pageRequest);
        }
        
        return repository.findAll(pageRequest);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false, defaultValue = "24") int hoursBack) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        
        long successCount = repository.countByTaskIdAndStatusSince(taskId, "COMPLETED", since);
        long failedCount = repository.countByTaskIdAndStatusSince(taskId, "FAILED", since);
        
        return Map.of(
            "periodHours", hoursBack,
            "since", since.toString(),
            "successCount", successCount,
            "failedCount", failedCount,
            "totalCount", successCount + failedCount,
            "successRate", successCount + failedCount > 0 
                ? String.format("%.2f%%", (double) successCount / (successCount + failedCount) * 100) 
                : "N/A"
        );
    }
}
