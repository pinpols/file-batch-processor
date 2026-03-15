package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.service.MigrationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ops/migration")
public class MigrationController {

    private final MigrationService migrationService;

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
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
        return Map.of(
                "status", "STARTED",
                "message", "File record backfill started",
                "operator", operator
        );
    }

    @PostMapping("/switch/{tableType}")
    public Map<String, Object> switchToNewModel(@PathVariable String tableType, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        return migrationService.switchToNewModel(tableType);
    }

    @PostMapping("/deprecate/{tableName}")
    public Map<String, Object> deprecateLegacyTable(@PathVariable String tableName, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        return migrationService.deprecateLegacyTable(tableName);
    }
}
