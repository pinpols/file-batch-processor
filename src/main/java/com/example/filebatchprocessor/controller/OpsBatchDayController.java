package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.BatchDayInstance;
import com.example.filebatchprocessor.model.BatchDayReplayScope;
import com.example.filebatchprocessor.model.BatchDayReplaySession;
import com.example.filebatchprocessor.model.BatchDayStatus;
import com.example.filebatchprocessor.service.BatchDayReplayService;
import com.example.filebatchprocessor.service.BatchDayService;
import com.example.filebatchprocessor.service.OpsAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops/batch-days")
public class OpsBatchDayController {

    private final BatchDayService batchDayService;
    private final BatchDayReplayService batchDayReplayService;
    private final OpsAuditService opsAuditService;

    public OpsBatchDayController(
            BatchDayService batchDayService,
            BatchDayReplayService batchDayReplayService,
            OpsAuditService opsAuditService) {
        this.batchDayService = batchDayService;
        this.batchDayReplayService = batchDayReplayService;
        this.opsAuditService = opsAuditService;
    }

    @GetMapping
    public List<BatchDayInstance> recentBatchDays() {
        return batchDayService.recent();
    }

    @PostMapping
    public BatchDayInstance ensureBatchDay(@Valid @RequestBody EnsureBatchDayRequest request) {
        return batchDayService.ensure(request.tenantId(), request.calendarCode(), request.bizDate());
    }

    @PostMapping("/{id}/status")
    public BatchDayInstance transitionBatchDay(
            @PathVariable Long id,
            @Valid @RequestBody TransitionBatchDayRequest request,
            Authentication authentication) {
        BatchDayInstance instance = batchDayService.transition(id, request.status());
        String operator = authentication == null ? "SYSTEM" : authentication.getName();
        opsAuditService.log(
                "BATCH_DAY_STATUS_CHANGE",
                operator,
                "BATCH_DAY",
                String.valueOf(id),
                "SUCCESS",
                "status=" + request.status());
        return instance;
    }

    @GetMapping("/replays")
    public List<BatchDayReplaySession> recentReplaySessions() {
        return batchDayReplayService.recentSessions();
    }

    @GetMapping("/replays/{sessionId}")
    public Map<String, Object> replayDetail(@PathVariable Long sessionId) {
        return batchDayReplayService.sessionDetail(sessionId);
    }

    @PostMapping("/replays")
    public BatchDayReplaySession submitReplay(
            @Valid @RequestBody SubmitReplayRequest request, Authentication authentication) {
        String operator = authentication == null ? "SYSTEM" : authentication.getName();
        BatchDayReplayService.SubmitRequest command = new BatchDayReplayService.SubmitRequest(
                request.tenantId(),
                request.calendarCode(),
                request.bizDate(),
                request.scope(),
                request.taskIds(),
                request.reason());
        BatchDayReplaySession session = batchDayReplayService.submit(command, operator);
        opsAuditService.log(
                "BATCH_DAY_REPLAY_SUBMIT",
                operator,
                "BATCH_DAY_REPLAY",
                String.valueOf(session.getId()),
                "SUCCESS",
                "bizDate=" + session.getBizDate() + ", scope=" + session.getScope());
        return session;
    }

    @PostMapping("/replays/{sessionId}/reconcile")
    public BatchDayReplaySession reconcileReplay(@PathVariable Long sessionId) {
        return batchDayReplayService.reconcile(sessionId);
    }

    public record EnsureBatchDayRequest(
            String tenantId, String calendarCode, @NotNull LocalDate bizDate) {}

    public record TransitionBatchDayRequest(@NotNull BatchDayStatus status) {}

    public record SubmitReplayRequest(
            String tenantId,
            String calendarCode,
            @NotNull LocalDate bizDate,
            BatchDayReplayScope scope,
            List<String> taskIds,
            String reason) {}
}
