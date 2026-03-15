package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileProcessLog;
import com.example.filebatchprocessor.repository.FileProcessLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional
public class FileProcessLogService {

    private final FileProcessLogRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired
    public FileProcessLogService(FileProcessLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(Long fileRecordId,
                    String stepName,
                    String actionType,
                    String statusFrom,
                    String statusTo,
                    String result,
                    String taskId,
                    String jobName,
                    Integer retryNo,
                    String errorCode,
                    String errorMsg,
                    Map<String, Object> extra) {
        if (fileRecordId == null) {
            return;
        }

        FileProcessLog log = new FileProcessLog();
        log.setFileRecordId(fileRecordId);
        log.setStepName(stepName);
        log.setActionType(actionType);
        log.setStatusFrom(statusFrom);
        log.setStatusTo(statusTo);
        log.setResult(result);
        log.setTaskId(taskId);
        log.setJobName(jobName);
        log.setRetryNo(retryNo == null ? 0 : retryNo);
        log.setErrorCode(errorCode);
        log.setErrorMsg(truncate(errorMsg, 1000));
        log.setExtra(toJson(extra));
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        repository.save(log);
    }

    private String toJson(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize file process log extra payload", e);
        }
    }

    private String truncate(String raw, int maxLength) {
        if (raw == null || raw.length() <= maxLength) {
            return raw;
        }
        return raw.substring(0, maxLength);
    }
}
