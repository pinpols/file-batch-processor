package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.OpsAuditLog;
import com.example.filebatchprocessor.repository.OpsAuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpsAuditService {

    private final OpsAuditLogRepository opsAuditLogRepository;

    public OpsAuditService(OpsAuditLogRepository opsAuditLogRepository) {
        this.opsAuditLogRepository = opsAuditLogRepository;
    }

    public void log(String action, String actor, String resourceType, String resourceId, String result, String details) {
        OpsAuditLog log = new OpsAuditLog();
        log.setAction(action);
        log.setActor(actor);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResult(result);
        log.setDetails(details);
        opsAuditLogRepository.save(log);
    }

    public List<OpsAuditLog> recentLogs() {
        return opsAuditLogRepository.findTop500ByOrderByCreatedAtDesc();
    }
}

