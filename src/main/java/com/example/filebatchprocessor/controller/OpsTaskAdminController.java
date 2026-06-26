package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.service.OpsTaskAdminService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/tasks")
public class OpsTaskAdminController {

    private final OpsTaskAdminService opsTaskAdminService;

    public OpsTaskAdminController(OpsTaskAdminService opsTaskAdminService) {
        this.opsTaskAdminService = opsTaskAdminService;
    }

    @GetMapping
    public List<Map<String, Object>> listTasks() {
        return opsTaskAdminService.listTasks();
    }

    @PostMapping("/{taskId}/toggle")
    public Map<String, Object> toggleTask(
            @PathVariable String taskId, @RequestParam boolean enabled, Authentication authentication) {
        var updated = opsTaskAdminService.toggleTask(taskId, enabled, authentication.getName());
        return Map.of(
                "taskId", updated.getTaskId(),
                "enabled", updated.getEnabled());
    }
}
