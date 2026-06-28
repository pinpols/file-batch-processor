package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.model.FileProcessLog;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.repository.FileProcessLogRepository;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.OpsAuditService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/files")
public class OpsFileController {

    private final FileAssetRecordRepository fileAssetRepository;
    private final FileProcessLogRepository fileProcessLogRepository;
    private final FileDispatchRecordRepository fileDispatchRecordRepository;
    private final FileAssetService fileAssetService;
    private final OpsAuditService opsAuditService;

    public OpsFileController(
            FileAssetRecordRepository fileAssetRepository,
            FileProcessLogRepository fileProcessLogRepository,
            FileDispatchRecordRepository fileDispatchRecordRepository,
            FileAssetService fileAssetService,
            OpsAuditService opsAuditService) {
        this.fileAssetRepository = fileAssetRepository;
        this.fileProcessLogRepository = fileProcessLogRepository;
        this.fileDispatchRecordRepository = fileDispatchRecordRepository;
        this.fileAssetService = fileAssetService;
        this.opsAuditService = opsAuditService;
    }

    @GetMapping
    public Map<String, Object> searchFiles(
            @RequestParam(required = false) String fileNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String bizDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FileAssetRecord> result;

        if (fileNo != null && !fileNo.isBlank()) {
            List<FileAssetRecord> records = fileAssetRepository.findByFileNoIn(List.of(fileNo));
            result = new org.springframework.data.domain.PageImpl<>(records, pageable, records.size());
        } else if (status != null && !status.isBlank()) {
            result = fileAssetRepository.findByStatus(status, pageable);
        } else if (sourceSystem != null && !sourceSystem.isBlank()) {
            if (bizDate != null && !bizDate.isBlank()) {
                result = fileAssetRepository.findBySourceSystemAndBizDate(sourceSystem, bizDate, pageable);
            } else {
                result = fileAssetRepository.findBySourceSystem(sourceSystem, pageable);
            }
        } else if (bizDate != null && !bizDate.isBlank()) {
            result = fileAssetRepository.findByBizDate(bizDate, pageable);
        } else {
            result = fileAssetRepository.findAll(pageable);
        }

        opsAuditService.log(
                "FILE_SEARCH",
                operator,
                "FILE",
                "QUERY",
                "SUCCESS",
                "Query files with criteria: fileNo=" + fileNo + ", status=" + status + ", sourceSystem=" + sourceSystem
                        + ", bizDate=" + bizDate);

        return buildFilePageResponse(result);
    }

    @GetMapping("/{fileId}")
    public Map<String, Object> getFileDetail(@PathVariable Long fileId, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";

        FileAssetRecord record = fileAssetRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        List<FileProcessLog> logs = fileProcessLogRepository.findByFileRecordIdOrderByCreatedAtDesc(fileId);
        List<FileDispatchRecord> dispatches =
                fileDispatchRecordRepository.findByFileRecordIdOrderByCreatedAtDesc(fileId);

        opsAuditService.log("FILE_VIEW", operator, "FILE", String.valueOf(fileId), "SUCCESS", "View file detail");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("file", record);
        response.put("processLogs", logs);
        response.put("dispatchRecords", dispatches);
        return response;
    }

    @GetMapping("/{fileId}/logs")
    public Map<String, Object> getFileProcessLogs(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";

        FileAssetRecord record = fileAssetRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FileProcessLog> logs = fileProcessLogRepository.findByFileRecordId(fileId, pageable);

        opsAuditService.log(
                "FILE_LOGS_VIEW", operator, "FILE", String.valueOf(fileId), "SUCCESS", "View file process logs");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileId", fileId);
        response.put("fileNo", record.getFileNo());
        response.put("logs", logs.getContent());
        response.put("page", logs.getNumber());
        response.put("totalPages", logs.getTotalPages());
        response.put("totalElements", logs.getTotalElements());
        return response;
    }

    @GetMapping("/{fileId}/dispatches")
    public Map<String, Object> getFileDispatchRecords(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";

        FileAssetRecord record = fileAssetRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FileDispatchRecord> dispatches = fileDispatchRecordRepository.findByFileRecordId(fileId, pageable);

        opsAuditService.log(
                "FILE_DISPATCH_VIEW",
                operator,
                "FILE",
                String.valueOf(fileId),
                "SUCCESS",
                "View file dispatch records");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileId", fileId);
        response.put("fileNo", record.getFileNo());
        response.put("dispatches", dispatches.getContent());
        response.put("page", dispatches.getNumber());
        response.put("totalPages", dispatches.getTotalPages());
        response.put("totalElements", dispatches.getTotalElements());
        return response;
    }

    @PostMapping("/{fileId}/reprocess")
    public Map<String, Object> reprocessFile(
            @PathVariable Long fileId,
            @RequestBody(required = false) ReprocessRequest request,
            Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";
        String reason = request != null && request.reason() != null ? request.reason() : "Manual reprocess requested";

        FileAssetRecord record = fileAssetRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        fileAssetService.reprocessFile(fileId, operator, reason);

        opsAuditService.log(
                "FILE_REPROCESS", operator, "FILE", String.valueOf(fileId), "SUCCESS", "Reprocess file: " + reason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileId", fileId);
        response.put("fileNo", record.getFileNo());
        response.put("operator", operator);
        response.put("reason", reason);
        response.put("message", "File reprocess initiated");
        return response;
    }

    @GetMapping("/download/{fileId}/authorize")
    public Map<String, Object> authorizeDownload(@PathVariable Long fileId, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "SYSTEM";

        FileAssetRecord record = fileAssetRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        opsAuditService.log(
                "FILE_DOWNLOAD_AUTHORIZE",
                operator,
                "FILE",
                String.valueOf(fileId),
                "SUCCESS",
                "Authorize file download");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authorized", true);
        response.put("fileId", fileId);
        response.put("fileNo", record.getFileNo());
        response.put("originalName", record.getOriginalName());
        response.put("operator", operator);
        response.put("expiresIn", 300);
        return response;
    }

    private Map<String, Object> buildFilePageResponse(Page<FileAssetRecord> page) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("files", page.getContent());
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalPages", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("hasNext", page.hasNext());
        response.put("hasPrevious", page.hasPrevious());
        return response;
    }

    public record ReprocessRequest(String reason) {}
}
