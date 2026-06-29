package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.model.CompensationActionType;
import com.example.filebatchprocessor.model.CompensationRecord;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.OpsAuditService;
import com.example.filebatchprocessor.service.RetryCompensationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/batch")
public class OpsBatchController {

    private final TaskSchedulerService taskSchedulerService;
    private final RetryCompensationService retryCompensationService;
    private final OpsAuditService opsAuditService;
    private final FileAssetService fileAssetService;

    public OpsBatchController(
            TaskSchedulerService taskSchedulerService,
            RetryCompensationService retryCompensationService,
            OpsAuditService opsAuditService,
            FileAssetService fileAssetService) {
        this.taskSchedulerService = taskSchedulerService;
        this.retryCompensationService = retryCompensationService;
        this.opsAuditService = opsAuditService;
        this.fileAssetService = fileAssetService;
    }

    @PostMapping("/rerun")
    public Map<String, Object> rerunBatch(@Valid @RequestBody RerunRequest request, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        String bizDate = request.bizDate();
        String taskId = request.taskId();
        String reason = request.reason() != null ? request.reason() : "Manual rerun requested";

        TaskSchedulerService.ManualEnqueueResult enqueued =
                taskSchedulerService.enqueueManualRerun(taskId, bizDate, reason, operator);

        opsAuditService.log(
                "BATCH_RERUN",
                operator,
                "TASK",
                taskId,
                "SUCCESS",
                "bizDate=" + enqueued.batchDate() + ", taskId=" + taskId + ", rerunId=" + enqueued.rerunId()
                        + ", reason=" + reason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", true);
        response.put("taskId", taskId);
        response.put("jobName", enqueued.jobName());
        response.put("bizDate", enqueued.batchDate());
        response.put("rerunId", enqueued.rerunId());
        response.put("runKey", enqueued.runKey());
        response.put("operator", operator);
        response.put("reason", reason);
        response.put("message", "Batch rerun enqueued");
        return response;
    }

    @PostMapping("/compensate")
    public Map<String, Object> compensate(
            @Valid @RequestBody CompensateRequest request, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        String reason = request.reason() != null ? request.reason() : "Manual compensation requested";

        if (request.fileId() != null) {
            CompensationRecord record =
                    retryCompensationService.startCompensation(new RetryCompensationService.StartRequest(
                            CompensationActionType.FILE_REPROCESS,
                            null,
                            null,
                            request.fileId(),
                            null,
                            null,
                            null,
                            operator,
                            reason,
                            null));
            fileAssetService.reprocessFile(request.fileId(), operator, reason);

            opsAuditService.log(
                    "FILE_COMPENSATE",
                    operator,
                    "FILE",
                    String.valueOf(request.fileId()),
                    "SUCCESS",
                    "compensationId=" + record.getId() + ", reason=" + reason);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "FILE");
            response.put("fileId", request.fileId());
            response.put("compensationId", record.getId());
            response.put("operator", operator);
            response.put("reason", reason);
            response.put("message", "File compensation initiated");
            return response;
        } else if (request.instanceId() != null) {
            CompensationRecord record =
                    retryCompensationService.startCompensation(new RetryCompensationService.StartRequest(
                            CompensationActionType.JOB_RESTART,
                            request.instanceId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            operator,
                            reason,
                            null));

            opsAuditService.log(
                    "JOB_COMPENSATE",
                    operator,
                    "JOB_INSTANCE",
                    String.valueOf(request.instanceId()),
                    "SUCCESS",
                    "compensationId=" + record.getId() + ", reason=" + reason);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "JOB_INSTANCE");
            response.put("instanceId", request.instanceId());
            response.put("compensationId", record.getId());
            response.put("operator", operator);
            response.put("reason", reason);
            response.put("message", "Job compensation initiated");
            return response;
        } else if (request.stepId() != null) {
            CompensationRecord record =
                    retryCompensationService.startCompensation(new RetryCompensationService.StartRequest(
                            CompensationActionType.STEP_RESTART,
                            null,
                            request.stepId(),
                            null,
                            null,
                            null,
                            null,
                            operator,
                            reason,
                            null));

            opsAuditService.log(
                    "STEP_COMPENSATE",
                    operator,
                    "STEP",
                    String.valueOf(request.stepId()),
                    "SUCCESS",
                    "compensationId=" + record.getId() + ", reason=" + reason);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "STEP");
            response.put("stepId", request.stepId());
            response.put("compensationId", record.getId());
            response.put("operator", operator);
            response.put("reason", reason);
            response.put("message", "Step compensation initiated");
            return response;
        } else {
            throw new IllegalArgumentException("At least one of fileId, instanceId, or stepId must be provided");
        }
    }

    @PostMapping("/retry")
    public Map<String, Object> retry(@Valid @RequestBody RetryRequest request, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        String reason = request.reason() != null ? request.reason() : "Manual retry requested";

        if (request.fileId() != null) {
            CompensationRecord record =
                    retryCompensationService.startCompensation(new RetryCompensationService.StartRequest(
                            CompensationActionType.FILE_RETRY,
                            null,
                            null,
                            request.fileId(),
                            null,
                            null,
                            null,
                            operator,
                            reason,
                            null));
            fileAssetService.reprocessFile(request.fileId(), operator, reason);

            opsAuditService.log(
                    "FILE_RETRY",
                    operator,
                    "FILE",
                    String.valueOf(request.fileId()),
                    "SUCCESS",
                    "compensationId=" + record.getId() + ", reason=" + reason);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "FILE");
            response.put("fileId", request.fileId());
            response.put("compensationId", record.getId());
            response.put("operator", operator);
            response.put("reason", reason);
            response.put("message", "File retry initiated");
            return response;
        } else if (request.instanceId() != null) {
            CompensationRecord record =
                    retryCompensationService.startCompensation(new RetryCompensationService.StartRequest(
                            CompensationActionType.JOB_RETRY,
                            request.instanceId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            operator,
                            reason,
                            null));

            opsAuditService.log(
                    "JOB_RETRY",
                    operator,
                    "JOB_INSTANCE",
                    String.valueOf(request.instanceId()),
                    "SUCCESS",
                    "compensationId=" + record.getId() + ", reason=" + reason);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "JOB_INSTANCE");
            response.put("instanceId", request.instanceId());
            response.put("compensationId", record.getId());
            response.put("operator", operator);
            response.put("reason", reason);
            response.put("message", "Job retry initiated");
            return response;
        } else {
            throw new IllegalArgumentException("At least one of fileId or instanceId must be provided");
        }
    }

    public record RerunRequest(String bizDate, @NotBlank String taskId, String reason) {}

    public record CompensateRequest(Long fileId, Long instanceId, Long stepId, String reason) {
        @AssertTrue(message = "one of fileId, instanceId, stepId is required")
        public boolean hasTarget() {
            return fileId != null || instanceId != null || stepId != null;
        }
    }

    public record RetryRequest(Long fileId, Long instanceId, String reason) {
        @AssertTrue(message = "one of fileId, instanceId is required")
        public boolean hasTarget() {
            return fileId != null || instanceId != null;
        }
    }
}
