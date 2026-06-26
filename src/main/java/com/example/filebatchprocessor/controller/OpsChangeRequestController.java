package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.OpsChangeRequest;
import com.example.filebatchprocessor.service.OpsChangeManagementService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/change-requests")
public class OpsChangeRequestController {

    private final OpsChangeManagementService opsChangeManagementService;

    public OpsChangeRequestController(OpsChangeManagementService opsChangeManagementService) {
        this.opsChangeManagementService = opsChangeManagementService;
    }

    @GetMapping
    public List<OpsChangeRequest> listRecent() {
        return opsChangeManagementService.listRecent();
    }

    @PostMapping
    public OpsChangeRequest create(@RequestBody CreateChangeRequest request, Authentication authentication) {
        return opsChangeManagementService.createRequest(
                authentication.getName(),
                request.targetType(),
                request.taskId(),
                request.fieldName(),
                request.newValue(),
                request.reason(),
                request.windowStart(),
                request.windowEnd(),
                request.impactSummary(),
                request.riskLevel(),
                request.rollbackPlan());
    }

    @PostMapping("/{id}/approve")
    public OpsChangeRequest approve(@PathVariable Long id, Authentication authentication) {
        return opsChangeManagementService.approve(id, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    public OpsChangeRequest reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String reason = body == null ? "" : body.getOrDefault("reason", "");
        return opsChangeManagementService.reject(id, authentication.getName(), reason);
    }

    @PostMapping("/{id}/apply")
    public OpsChangeRequest apply(@PathVariable Long id, Authentication authentication) {
        return opsChangeManagementService.apply(id, authentication.getName());
    }

    public record CreateChangeRequest(
            String targetType,
            String taskId,
            String fieldName,
            String newValue,
            String reason,
            String windowStart,
            String windowEnd,
            String impactSummary,
            String riskLevel,
            String rollbackPlan) {}
}
