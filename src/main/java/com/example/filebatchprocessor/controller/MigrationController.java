package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.service.MigrationService;
import com.example.filebatchprocessor.service.OpsAuditService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/migration")
public class MigrationController {

    private final MigrationService migrationService;
    private final OpsAuditService opsAuditService;

    public MigrationController(MigrationService migrationService, OpsAuditService opsAuditService) {
        this.migrationService = migrationService;
        this.opsAuditService = opsAuditService;
    }

    @GetMapping("/status")
    public Map<String, Object> getAllMigrations() {
        List<Map<String, Object>> migrations = migrationService.getAllMigrations();
        return Map.of("migrations", migrations);
    }

    @GetMapping("/status/{name}")
    public Map<String, Object> getMigrationStatus(@PathVariable String name) {
        return migrationService.getMigrationStatus(name);
    }

    @GetMapping("/health")
    public Map<String, Object> getMigrationHealth() {
        return migrationService.getMigrationHealth();
    }

    @PostMapping("/backfill")
    public Map<String, Object> triggerBackfill(Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        migrationService.backfillFileRecords();
        opsAuditService.log(
                "MIGRATION_BACKFILL", operator, "MIGRATION", "FILE_RECORD_BACKFILL", "SUCCESS", "trigger=manual");
        return Map.of(
                "status", "STARTED",
                "message", "File record backfill started",
                "operator", operator);
    }

    @PostMapping("/switch/{tableType}")
    public Map<String, Object> switchToNewModel(@PathVariable String tableType, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        Map<String, Object> result = migrationService.switchToNewModel(tableType);
        opsAuditService.log(
                "MIGRATION_SWITCH",
                operator,
                "MIGRATION",
                String.valueOf(result.get("table")),
                "SUCCESS",
                "message=" + result.get("message"));
        return result;
    }

    @PostMapping("/deprecate/{tableName}")
    public Map<String, Object> deprecateLegacyTable(@PathVariable String tableName, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        Map<String, Object> result = migrationService.deprecateLegacyTable(tableName);
        opsAuditService.log(
                "MIGRATION_DEPRECATE",
                operator,
                "MIGRATION",
                String.valueOf(result.get("table")),
                "SUCCESS",
                "message=" + result.get("message"));
        return result;
    }
}
