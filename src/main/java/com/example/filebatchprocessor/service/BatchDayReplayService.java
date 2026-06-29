package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.model.BatchDayReplayEntry;
import com.example.filebatchprocessor.model.BatchDayReplayEntryStatus;
import com.example.filebatchprocessor.model.BatchDayReplayScope;
import com.example.filebatchprocessor.model.BatchDayReplaySession;
import com.example.filebatchprocessor.model.BatchDayReplayStatus;
import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.TaskExecutionStatus;
import com.example.filebatchprocessor.repository.BatchDayReplayEntryRepository;
import com.example.filebatchprocessor.repository.BatchDayReplaySessionRepository;
import com.example.filebatchprocessor.repository.BusinessJobInstanceRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BatchDayReplayService {

    private static final List<String> TERMINAL_INSTANCE_STATUSES =
            List.of("COMPLETED", "PARTIAL_SUCCESS", "FAILED", "CANCELLED", "TIMEOUT");
    private static final List<String> FAILED_INSTANCE_STATUSES = List.of("PARTIAL_SUCCESS", "FAILED", "TIMEOUT");

    private final BatchDayReplaySessionRepository sessionRepository;
    private final BatchDayReplayEntryRepository entryRepository;
    private final BusinessJobInstanceRepository jobInstanceRepository;
    private final TaskExecutionStateRepository taskExecutionStateRepository;
    private final TaskSchedulerService taskSchedulerService;
    private final BatchDayService batchDayService;
    private final ObjectMapper objectMapper;

    public BatchDayReplayService(
            BatchDayReplaySessionRepository sessionRepository,
            BatchDayReplayEntryRepository entryRepository,
            BusinessJobInstanceRepository jobInstanceRepository,
            TaskExecutionStateRepository taskExecutionStateRepository,
            TaskSchedulerService taskSchedulerService,
            BatchDayService batchDayService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
        this.jobInstanceRepository = jobInstanceRepository;
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.taskSchedulerService = taskSchedulerService;
        this.batchDayService = batchDayService;
        this.objectMapper = objectMapper;
    }

    public BatchDayReplaySession submit(SubmitRequest request, String operator) {
        String tenantId = normalize(request.tenantId(), BatchDayService.DEFAULT_TENANT);
        String calendarCode = normalize(request.calendarCode(), BatchDayService.DEFAULT_CALENDAR);
        LocalDate bizDate = request.bizDate();
        BatchDayReplayScope scope = request.scope() == null ? BatchDayReplayScope.ALL_FAILED : request.scope();
        String reason = normalize(request.reason(), "Batch day replay requested");

        sessionRepository
                .findFirstByTenantIdAndCalendarCodeAndBizDateAndStatusIn(
                        tenantId, calendarCode, bizDate, List.of(BatchDayReplayStatus.RUNNING))
                .ifPresent(existing -> {
                    throw new IllegalStateException("Active replay session already exists: " + existing.getId());
                });

        List<ReplayCandidate> candidates = resolveCandidates(scope, bizDate, request.taskIds());
        BatchDayReplaySession session = new BatchDayReplaySession();
        session.setTenantId(tenantId);
        session.setCalendarCode(calendarCode);
        session.setBizDate(bizDate);
        session.setScope(scope);
        session.setScopePayload(toJson(Map.of(
                "taskIds", candidates.stream().map(ReplayCandidate::taskId).toList())));
        session.setReason(reason);
        session.setRequestedBy(normalize(operator, "SYSTEM"));
        session.setStartedAt(LocalDateTime.now());
        session.setTotalCount(candidates.size());
        session.setInFlightCount(candidates.size());
        session = sessionRepository.save(session);
        batchDayService.markReplaying(tenantId, calendarCode, bizDate, session.getId());

        int enqueueFailures = 0;
        for (ReplayCandidate candidate : candidates) {
            BatchDayReplayEntry entry = new BatchDayReplayEntry();
            entry.setSessionId(session.getId());
            entry.setTenantId(tenantId);
            entry.setTaskId(candidate.taskId());
            entry.setJobName(candidate.jobName());
            entry.setSourceInstanceId(candidate.sourceInstanceId());
            try {
                TaskSchedulerService.ManualEnqueueResult result = taskSchedulerService.enqueueManualRerun(
                        candidate.taskId(), bizDate.toString(), "BATCH_DAY_REPLAY:" + reason, operator);
                entry.setRerunId(result.rerunId());
                entry.setRunKey(result.runKey());
                entry.setStatus(BatchDayReplayEntryStatus.ENQUEUED);
                entry.setJobName(result.jobName());
            } catch (RuntimeException ex) {
                enqueueFailures++;
                entry.setStatus(BatchDayReplayEntryStatus.FAILED);
                entry.setFailureReason(truncate(ex.getMessage()));
            }
            entryRepository.save(entry);
        }

        if (candidates.isEmpty() || enqueueFailures > 0) {
            return reconcile(session.getId());
        }
        return session;
    }

    public BatchDayReplaySession reconcile(Long sessionId) {
        BatchDayReplaySession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Replay session not found: " + sessionId));
        List<BatchDayReplayEntry> entries = entryRepository.findBySessionIdOrderByIdAsc(sessionId);
        int succeeded = 0;
        int failed = 0;
        int inFlight = 0;
        for (BatchDayReplayEntry entry : entries) {
            if (entry.getStatus() == BatchDayReplayEntryStatus.ENQUEUED && entry.getRerunId() != null) {
                taskExecutionStateRepository
                        .findByTaskIdAndBatchDateAndRerunId(
                                entry.getTaskId(), session.getBizDate().toString(), entry.getRerunId())
                        .ifPresent(state -> {
                            String status = TaskExecutionStatus.normalize(state.getStatus());
                            if (TaskExecutionStatus.SUCCESS.name().equals(status)) {
                                entry.setStatus(BatchDayReplayEntryStatus.SUCCEEDED);
                            } else if (TaskExecutionStatus.FAILED.name().equals(status)
                                    || TaskExecutionStatus.PARTIAL.name().equals(status)) {
                                entry.setStatus(BatchDayReplayEntryStatus.FAILED);
                                entry.setFailureReason(truncate(state.getLastError()));
                            } else if (TaskExecutionStatus.SKIPPED.name().equals(status)) {
                                entry.setStatus(BatchDayReplayEntryStatus.SKIPPED);
                            }
                            entryRepository.save(entry);
                        });
            }
            if (entry.getStatus() == BatchDayReplayEntryStatus.SUCCEEDED
                    || entry.getStatus() == BatchDayReplayEntryStatus.SKIPPED) {
                succeeded++;
            } else if (entry.getStatus() == BatchDayReplayEntryStatus.FAILED) {
                failed++;
            } else {
                inFlight++;
            }
        }
        session.setSucceededCount(succeeded);
        session.setFailedCount(failed);
        session.setInFlightCount(inFlight);
        if (inFlight == 0) {
            session.setCompletedAt(LocalDateTime.now());
            session.setStatus(failed == 0 ? BatchDayReplayStatus.SUCCEEDED : BatchDayReplayStatus.PARTIAL_FAILED);
            batchDayService.markReplayCompleted(
                    session.getTenantId(),
                    session.getCalendarCode(),
                    session.getBizDate(),
                    session.getId(),
                    failed == 0);
        }
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<BatchDayReplaySession> recentSessions() {
        return sessionRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sessionDetail(Long sessionId) {
        BatchDayReplaySession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Replay session not found: " + sessionId));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("session", session);
        detail.put("entries", entryRepository.findBySessionIdOrderByIdAsc(sessionId));
        return detail;
    }

    private List<ReplayCandidate> resolveCandidates(
            BatchDayReplayScope scope, LocalDate bizDate, List<String> requestedTaskIds) {
        if (scope == BatchDayReplayScope.SUBSET_TASK_IDS) {
            if (requestedTaskIds == null || requestedTaskIds.isEmpty()) {
                throw new IllegalArgumentException("taskIds is required when scope=SUBSET_TASK_IDS");
            }
            List<BusinessJobInstance> source =
                    jobInstanceRepository.findByBizDateOrderByCreatedAtDesc(bizDate.toString());
            Map<String, BusinessJobInstance> latestByTask = latestByTask(source);
            List<ReplayCandidate> candidates = new ArrayList<>();
            for (String taskId : requestedTaskIds) {
                BusinessJobInstance latest = latestByTask.get(taskId);
                candidates.add(new ReplayCandidate(
                        taskId, latest == null ? null : latest.getJobName(), latest == null ? null : latest.getId()));
            }
            return candidates;
        }
        List<BusinessJobInstance> source = scope == BatchDayReplayScope.ALL
                ? jobInstanceRepository.findByBizDateAndStatusInOrderByCreatedAtDesc(
                        bizDate.toString(), TERMINAL_INSTANCE_STATUSES)
                : jobInstanceRepository.findByBizDateAndStatusInOrderByCreatedAtDesc(
                        bizDate.toString(), FAILED_INSTANCE_STATUSES);
        Set<String> seenTaskIds = new LinkedHashSet<>();
        List<ReplayCandidate> candidates = new ArrayList<>();
        for (BusinessJobInstance instance : source) {
            if (instance.getTaskId() != null && seenTaskIds.add(instance.getTaskId())) {
                candidates.add(new ReplayCandidate(instance.getTaskId(), instance.getJobName(), instance.getId()));
            }
        }
        return candidates;
    }

    private Map<String, BusinessJobInstance> latestByTask(List<BusinessJobInstance> instances) {
        Map<String, BusinessJobInstance> latest = new LinkedHashMap<>();
        for (BusinessJobInstance instance : instances) {
            latest.putIfAbsent(instance.getTaskId(), instance);
        }
        return latest;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record ReplayCandidate(String taskId, String jobName, Long sourceInstanceId) {}

    public record SubmitRequest(
            String tenantId,
            String calendarCode,
            LocalDate bizDate,
            BatchDayReplayScope scope,
            List<String> taskIds,
            String reason) {}
}
