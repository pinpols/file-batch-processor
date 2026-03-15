package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.BusinessJobExecutionLog;
import com.example.filebatchprocessor.repository.BusinessJobExecutionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class JobExecutionLogService {

    private final BusinessJobExecutionLogRepository repository;
    private final ObjectMapper objectMapper;

    public JobExecutionLogService(BusinessJobExecutionLogRepository repository,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(Long jobInstanceId,
                    Long jobStepInstanceId,
                    String eventType,
                    String level,
                    String message,
                    String operatorName,
                    Map<String, ?> payload) {
        if (jobInstanceId == null || eventType == null || eventType.isBlank() || message == null || message.isBlank()) {
            return;
        }
        BusinessJobExecutionLog log = new BusinessJobExecutionLog();
        log.setJobInstanceId(jobInstanceId);
        log.setJobStepInstanceId(jobStepInstanceId);
        log.setEventType(eventType);
        log.setLevel(level == null || level.isBlank() ? "INFO" : level.toUpperCase());
        log.setMessage(message);
        log.setOperatorName(operatorName);
        log.setPayload(toJson(payload));
        repository.save(log);
    }

    private String toJson(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return payload.toString();
        }
    }
}
