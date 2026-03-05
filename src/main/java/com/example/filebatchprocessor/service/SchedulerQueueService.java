package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.SchedulerQueueRecord;
import com.example.filebatchprocessor.repository.SchedulerQueueRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SchedulerQueueService {

    private final SchedulerQueueRecordRepository repository;

    public SchedulerQueueService(SchedulerQueueRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean tryEnqueue(String runKey, String taskId, String batchDate, String rerunId) {
        SchedulerQueueRecord record = new SchedulerQueueRecord();
        record.setRunKey(runKey);
        record.setTaskId(taskId);
        record.setBatchDate(batchDate);
        record.setRerunId(rerunId == null ? "" : rerunId);
        record.setEnqueuedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        try {
            repository.save(record);
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate enqueue rejected by DB queue key: runKey={}", runKey);
            return false;
        }
    }

    @Transactional
    public void dequeue(String runKey) {
        repository.deleteById(runKey);
    }
}
