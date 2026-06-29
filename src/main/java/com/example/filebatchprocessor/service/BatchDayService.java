package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.BatchDayInstance;
import com.example.filebatchprocessor.model.BatchDayStatus;
import com.example.filebatchprocessor.repository.BatchDayInstanceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BatchDayService {

    public static final String DEFAULT_TENANT = "default";
    public static final String DEFAULT_CALENDAR = "default";

    private final BatchDayInstanceRepository repository;

    public BatchDayService(BatchDayInstanceRepository repository) {
        this.repository = repository;
    }

    public BatchDayInstance ensure(String tenantId, String calendarCode, LocalDate bizDate) {
        String tenant = normalize(tenantId, DEFAULT_TENANT);
        String calendar = normalize(calendarCode, DEFAULT_CALENDAR);
        return repository
                .findByTenantIdAndCalendarCodeAndBizDate(tenant, calendar, bizDate)
                .orElseGet(() -> {
                    BatchDayInstance instance = new BatchDayInstance();
                    instance.setTenantId(tenant);
                    instance.setCalendarCode(calendar);
                    instance.setBizDate(bizDate);
                    instance.setStatus(BatchDayStatus.OPEN);
                    return repository.save(instance);
                });
    }

    public BatchDayInstance transition(Long id, BatchDayStatus status) {
        BatchDayInstance instance =
                repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch day not found: " + id));
        instance.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        if (status == BatchDayStatus.FROZEN && instance.getFrozenAt() == null) {
            instance.setFrozenAt(now);
        }
        if (status == BatchDayStatus.SETTLED && instance.getSettledAt() == null) {
            instance.setSettledAt(now);
        }
        return repository.save(instance);
    }

    public void markReplaying(String tenantId, String calendarCode, LocalDate bizDate, Long replaySessionId) {
        BatchDayInstance instance = ensure(tenantId, calendarCode, bizDate);
        instance.setStatus(BatchDayStatus.REPLAYING);
        instance.setLastReplaySessionId(replaySessionId);
        repository.save(instance);
    }

    public void markReplayCompleted(
            String tenantId, String calendarCode, LocalDate bizDate, Long replaySessionId, boolean succeeded) {
        repository
                .findByTenantIdAndCalendarCodeAndBizDate(
                        normalize(tenantId, DEFAULT_TENANT), normalize(calendarCode, DEFAULT_CALENDAR), bizDate)
                .ifPresent(instance -> {
                    if (!BatchDayStatus.REPLAYING.equals(instance.getStatus())) {
                        return;
                    }
                    if (replaySessionId != null && !replaySessionId.equals(instance.getLastReplaySessionId())) {
                        return;
                    }
                    instance.setStatus(succeeded ? BatchDayStatus.SETTLED : BatchDayStatus.SETTLING);
                    if (succeeded && instance.getSettledAt() == null) {
                        instance.setSettledAt(LocalDateTime.now());
                    }
                    repository.save(instance);
                });
    }

    @Transactional(readOnly = true)
    public List<BatchDayInstance> recent() {
        return repository.findTop100ByOrderByBizDateDesc();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
