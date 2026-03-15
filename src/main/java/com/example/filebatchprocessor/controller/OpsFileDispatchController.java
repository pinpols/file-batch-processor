package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.service.FileDispatchRecordService;
import com.example.filebatchprocessor.service.FileDistributionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ops/file-dispatch")
public class OpsFileDispatchController {

    private final FileDistributionService fileDistributionService;
    private final FileDispatchRecordService fileDispatchRecordService;

    public OpsFileDispatchController(FileDistributionService fileDistributionService,
                                     FileDispatchRecordService fileDispatchRecordService) {
        this.fileDistributionService = fileDistributionService;
        this.fileDispatchRecordService = fileDispatchRecordService;
    }

    @PostMapping("/{taskId}/ack")
    public Map<String, Object> acknowledge(@PathVariable Long taskId,
                                           @RequestBody(required = false) AckRequest request,
                                           Authentication authentication) {
        AckRequest ackRequest = request == null ? new AckRequest(true, null, null) : request;
        String operatorName = authentication == null ? "SYSTEM" : authentication.getName();
        fileDistributionService.acknowledgeDispatch(taskId,
                ackRequest.accepted(),
                operatorName,
                ackRequest.message(),
                ackRequest.payload(),
                null);
        return dispatchResponse(taskId, operatorName);
    }

    @PostMapping("/{taskId}/resend")
    public Map<String, Object> resend(@PathVariable Long taskId,
                                      @RequestBody(required = false) ResendRequest request,
                                      Authentication authentication) {
        ResendRequest resendRequest = request == null ? new ResendRequest(null) : request;
        String operatorName = authentication == null ? "SYSTEM" : authentication.getName();
        fileDistributionService.scheduleResend(taskId,
                operatorName,
                resendRequest.reason() == null || resendRequest.reason().isBlank()
                        ? "Manual resend requested"
                        : resendRequest.reason(),
                null);
        return dispatchResponse(taskId, operatorName);
    }

    private Map<String, Object> dispatchResponse(Long taskId, String operatorName) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("operator", operatorName);
        FileDistributionTask task = fileDistributionService.findTaskById(taskId).orElse(null);
        FileDispatchRecord dispatchRecord = fileDispatchRecordService.findByLegacyDistributionTaskId(taskId).orElse(null);
        response.put("taskStatus", task == null ? null : task.getStatus());
        response.put("retryCount", task == null ? null : task.getRetryCount());
        response.put("dispatchRecordId", dispatchRecord == null ? null : dispatchRecord.getId());
        response.put("dispatchStatus", dispatchRecord == null ? null : dispatchRecord.getDispatchStatus());
        response.put("ackStatus", dispatchRecord == null ? null : dispatchRecord.getAckStatus());
        response.put("ackRequired", dispatchRecord == null ? null : dispatchRecord.getAckRequired());
        response.put("resendCount", dispatchRecord == null ? null : dispatchRecord.getResendCount());
        return response;
    }

    public record AckRequest(boolean accepted, String message, Map<String, Object> payload) {
    }

    public record ResendRequest(String reason) {
    }
}
