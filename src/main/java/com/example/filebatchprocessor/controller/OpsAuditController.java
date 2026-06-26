package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.OpsAuditLog;
import com.example.filebatchprocessor.service.OpsAuditService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops/audit")
public class OpsAuditController {

    private final OpsAuditService opsAuditService;

    public OpsAuditController(OpsAuditService opsAuditService) {
        this.opsAuditService = opsAuditService;
    }

    @GetMapping
    public List<OpsAuditLog> recent() {
        return opsAuditService.recentLogs();
    }
}
