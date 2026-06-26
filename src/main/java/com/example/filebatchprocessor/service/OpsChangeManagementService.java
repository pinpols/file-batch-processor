package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.OpsChangeRequest;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskParameter;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.repository.OpsChangeRequestRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskParameterRepository;
import com.example.filebatchprocessor.repository.TaskTriggerRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OpsChangeManagementService {

    private static final String STATUS_PENDING = "PENDING_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_APPLIED = "APPLIED";

    private final OpsChangeRequestRepository opsChangeRequestRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskTriggerRepository taskTriggerRepository;
    private final TaskParameterRepository taskParameterRepository;
    private final OpsAuditService opsAuditService;

    public OpsChangeManagementService(
            OpsChangeRequestRepository opsChangeRequestRepository,
            TaskDefinitionRepository taskDefinitionRepository,
            TaskTriggerRepository taskTriggerRepository,
            TaskParameterRepository taskParameterRepository,
            OpsAuditService opsAuditService) {
        this.opsChangeRequestRepository = opsChangeRequestRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskTriggerRepository = taskTriggerRepository;
        this.taskParameterRepository = taskParameterRepository;
        this.opsAuditService = opsAuditService;
    }

    public OpsChangeRequest createRequest(
            String actor,
            String targetType,
            String taskId,
            String fieldName,
            String newValue,
            String reason,
            String windowStart,
            String windowEnd,
            String impactSummary,
            String riskLevel,
            String rollbackPlan) {
        validateTargetType(targetType);
        String normalizedTarget = targetType.trim().toUpperCase(Locale.ROOT);
        String oldValue = loadOldValue(normalizedTarget, taskId, fieldName);

        OpsChangeRequest request = new OpsChangeRequest();
        request.setRequestNo(
                "CR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        request.setTargetType(normalizedTarget);
        request.setTaskId(taskId);
        request.setFieldName(fieldName);
        request.setOldValue(oldValue);
        request.setNewValue(newValue);
        request.setReason(reason);
        request.setRequestedBy(actor);
        request.setStatus(STATUS_PENDING);
        request.setWindowStart(parseOptionalDateTime(windowStart));
        request.setWindowEnd(parseOptionalDateTime(windowEnd));
        request.setImpactSummary(impactSummary);
        request.setRiskLevel(riskLevel);
        request.setRollbackPlan(rollbackPlan);
        OpsChangeRequest saved = opsChangeRequestRepository.save(request);
        opsAuditService.log(
                "CHANGE_REQUEST_CREATE",
                actor,
                "OPS_CHANGE_REQUEST",
                saved.getRequestNo(),
                "SUCCESS",
                "target=" + normalizedTarget + ", taskId=" + taskId + ", field=" + fieldName + ", window=" + windowStart
                        + "~" + windowEnd);
        return saved;
    }

    public OpsChangeRequest approve(Long id, String actor) {
        OpsChangeRequest request = getById(id);
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new IllegalStateException("Only pending request can be approved");
        }
        request.setStatus(STATUS_APPROVED);
        request.setApprovedBy(actor);
        request.setApprovedAt(LocalDateTime.now());
        OpsChangeRequest saved = opsChangeRequestRepository.save(request);
        opsAuditService.log(
                "CHANGE_REQUEST_APPROVE", actor, "OPS_CHANGE_REQUEST", saved.getRequestNo(), "SUCCESS", null);
        return saved;
    }

    public OpsChangeRequest reject(Long id, String actor, String rejectReason) {
        OpsChangeRequest request = getById(id);
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new IllegalStateException("Only pending request can be rejected");
        }
        request.setStatus(STATUS_REJECTED);
        request.setApprovedBy(actor);
        request.setRejectReason(rejectReason);
        request.setApprovedAt(LocalDateTime.now());
        OpsChangeRequest saved = opsChangeRequestRepository.save(request);
        opsAuditService.log(
                "CHANGE_REQUEST_REJECT", actor, "OPS_CHANGE_REQUEST", saved.getRequestNo(), "SUCCESS", rejectReason);
        return saved;
    }

    public OpsChangeRequest apply(Long id, String actor) {
        OpsChangeRequest request = getById(id);
        if (!STATUS_APPROVED.equals(request.getStatus())) {
            throw new IllegalStateException("Only approved request can be applied");
        }
        if (!withinWindow(request)) {
            throw new IllegalStateException("Change request is outside allowed window");
        }

        applyChange(request);
        request.setStatus(STATUS_APPLIED);
        request.setAppliedBy(actor);
        request.setAppliedAt(LocalDateTime.now());
        OpsChangeRequest saved = opsChangeRequestRepository.save(request);
        opsAuditService.log("CHANGE_REQUEST_APPLY", actor, "OPS_CHANGE_REQUEST", saved.getRequestNo(), "SUCCESS", null);
        return saved;
    }

    public List<OpsChangeRequest> listRecent() {
        return opsChangeRequestRepository.findTop200ByOrderByCreatedAtDesc();
    }

    private void applyChange(OpsChangeRequest request) {
        String target = request.getTargetType().toUpperCase(Locale.ROOT);
        switch (target) {
            case "TASK_DEFINITION" -> applyTaskDefinitionChange(request);
            case "TASK_TRIGGER" -> applyTaskTriggerChange(request);
            case "TASK_PARAMETER" -> applyTaskParameterChange(request);
            default -> throw new IllegalArgumentException("Unsupported targetType: " + target);
        }
    }

    private void applyTaskDefinitionChange(OpsChangeRequest request) {
        TaskDefinition def = taskDefinitionRepository
                .findByTaskId(request.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException("Task definition not found: " + request.getTaskId()));
        String field = request.getFieldName();
        String value = request.getNewValue();
        switch (field) {
            case "enabled" -> def.setEnabled(Boolean.parseBoolean(value));
            case "priority" -> def.setPriority(value);
            case "allowParallel" -> def.setAllowParallel(Boolean.parseBoolean(value));
            case "jobName" -> def.setJobName(value);
            case "dedupKey" -> def.setDedupKey(value);
            default -> throw new IllegalArgumentException("Unsupported task_definition field: " + field);
        }
        taskDefinitionRepository.save(def);
    }

    private void applyTaskTriggerChange(OpsChangeRequest request) {
        TaskTrigger trigger = taskTriggerRepository
                .findByTaskId(request.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException("Task trigger not found: " + request.getTaskId()));
        String field = request.getFieldName();
        String value = request.getNewValue();
        switch (field) {
            case "triggerType" -> trigger.setTriggerType(value);
            case "cronExpression" -> trigger.setCronExpression(value);
            case "fixedRateMs" -> trigger.setFixedRateMs(parseLong(value, field));
            case "fixedDelayMs" -> trigger.setFixedDelayMs(parseLong(value, field));
            case "oneTimeAt" -> trigger.setOneTimeAt(LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            case "enabled" -> trigger.setEnabled(Boolean.parseBoolean(value));
            default -> throw new IllegalArgumentException("Unsupported task_trigger field: " + field);
        }
        taskTriggerRepository.save(trigger);
    }

    private void applyTaskParameterChange(OpsChangeRequest request) {
        String paramName = request.getFieldName();
        TaskParameter param = taskParameterRepository
                .findByTaskIdAndParamName(request.getTaskId(), paramName)
                .orElseGet(() -> {
                    TaskParameter p = new TaskParameter();
                    p.setTaskId(request.getTaskId());
                    p.setParamName(paramName);
                    p.setParamType("STRING");
                    p.setDescription("Created by change request");
                    return p;
                });
        param.setParamValue(request.getNewValue());
        taskParameterRepository.save(param);
    }

    private String loadOldValue(String targetType, String taskId, String fieldName) {
        return switch (targetType) {
            case "TASK_DEFINITION" -> loadTaskDefinitionOldValue(taskId, fieldName);
            case "TASK_TRIGGER" -> loadTaskTriggerOldValue(taskId, fieldName);
            case "TASK_PARAMETER" ->
                taskParameterRepository
                        .findByTaskIdAndParamName(taskId, fieldName)
                        .map(TaskParameter::getParamValue)
                        .orElse(null);
            default -> null;
        };
    }

    private String loadTaskDefinitionOldValue(String taskId, String fieldName) {
        TaskDefinition def = taskDefinitionRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task definition not found: " + taskId));
        return switch (fieldName) {
            case "enabled" -> String.valueOf(def.getEnabled());
            case "priority" -> def.getPriority();
            case "allowParallel" -> String.valueOf(def.getAllowParallel());
            case "jobName" -> def.getJobName();
            case "dedupKey" -> def.getDedupKey();
            default -> null;
        };
    }

    private LocalDateTime parseOptionalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private boolean withinWindow(OpsChangeRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (request.getWindowStart() != null && now.isBefore(request.getWindowStart())) {
            return false;
        }
        if (request.getWindowEnd() != null && now.isAfter(request.getWindowEnd())) {
            return false;
        }
        return true;
    }

    private String loadTaskTriggerOldValue(String taskId, String fieldName) {
        TaskTrigger trigger = taskTriggerRepository
                .findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task trigger not found: " + taskId));
        return switch (fieldName) {
            case "triggerType" -> trigger.getTriggerType();
            case "cronExpression" -> trigger.getCronExpression();
            case "fixedRateMs" -> asString(trigger.getFixedRateMs());
            case "fixedDelayMs" -> asString(trigger.getFixedDelayMs());
            case "oneTimeAt" ->
                trigger.getOneTimeAt() == null ? null : trigger.getOneTimeAt().toString();
            case "enabled" -> String.valueOf(trigger.getEnabled());
            default -> null;
        };
    }

    private OpsChangeRequest getById(Long id) {
        return opsChangeRequestRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Change request not found: " + id));
    }

    private void validateTargetType(String targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("targetType is required");
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        if (!"TASK_DEFINITION".equals(normalized)
                && !"TASK_TRIGGER".equals(normalized)
                && !"TASK_PARAMETER".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported targetType: " + targetType);
        }
    }

    private Long parseLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be numeric");
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
