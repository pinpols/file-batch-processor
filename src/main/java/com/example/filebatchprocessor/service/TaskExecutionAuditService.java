package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.TaskExecutionAudit;
import com.example.filebatchprocessor.repository.TaskExecutionAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TaskExecutionAuditService {

    private final TaskExecutionAuditRepository repository;
    private final ObjectMapper objectMapper;

    public TaskExecutionAuditService(TaskExecutionAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(String taskId,
                    String jobName,
                    String batchDate,
                    String runKey,
                    String eventType,
                    String status,
                    String reason,
                    Map<String, String> params) {
        TaskExecutionAudit audit = new TaskExecutionAudit();
        audit.setTaskId(taskId);
        audit.setJobName(jobName);
        audit.setBatchDate(batchDate);
        audit.setRunKey(runKey);
        audit.setEventType(eventType);
        audit.setStatus(status);
        audit.setReason(reason);
        audit.setParams(toJson(params));
        repository.save(audit);
    }

    private String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return params.toString();
        }
    }
}
