package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.exception.SystemException;
import com.example.filebatchprocessor.model.BusinessJobInstance;
import com.example.filebatchprocessor.model.CompensationActionType;
import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件分发服务：
 * 1. 创建分发任务
 * 2. 管理重传机制
 * 3. 支持多种传输协议（SFTP、HTTP、FTP 等）
 */
@Slf4j
@Service
@Transactional
public class FileDistributionService {

    private final FileDistributionTaskRepository fileDistributionTaskRepository;
    private final RecordTraceRepository recordTraceRepository;
    private final FileAssetService fileAssetService;
    private final FileDispatchRecordService fileDispatchRecordService;
    private final FileProcessLogService fileProcessLogService;
    private final RetryCompensationService retryCompensationService;

    @Autowired
    public FileDistributionService(
            FileDistributionTaskRepository fileDistributionTaskRepository,
            RecordTraceRepository recordTraceRepository,
            FileAssetService fileAssetService,
            FileDispatchRecordService fileDispatchRecordService,
            FileProcessLogService fileProcessLogService,
            RetryCompensationService retryCompensationService) {
        this.fileDistributionTaskRepository = fileDistributionTaskRepository;
        this.recordTraceRepository = recordTraceRepository;
        this.fileAssetService = fileAssetService;
        this.fileDispatchRecordService = fileDispatchRecordService;
        this.fileProcessLogService = fileProcessLogService;
        this.retryCompensationService = retryCompensationService;
    }

    public FileDistributionService(
            FileDistributionTaskRepository fileDistributionTaskRepository,
            RecordTraceRepository recordTraceRepository) {
        this(fileDistributionTaskRepository, recordTraceRepository, null, null, null, null);
    }

    public FileDistributionTask createDistributionTask(
            String fileName, String filePath, String targetSystem, String targetAddress) {
        return createDistributionTask(fileName, filePath, targetSystem, targetAddress, false, null, null);
    }

    public FileDistributionTask createDistributionTask(
            String fileName,
            String filePath,
            String targetSystem,
            String targetAddress,
            boolean ackRequired,
            Integer ackTimeoutMinutes,
            Long createdJobInstanceId) {
        log.info("Creating distribution task: file={}, target={}://{}", fileName, targetSystem, targetAddress);

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + filePath);
            }

            FileDistributionTask task = new FileDistributionTask();
            task.setFileName(fileName);
            task.setFilePath(filePath);
            task.setFileSize(file.length());
            task.setTargetSystem(targetSystem);
            task.setTargetAddress(targetAddress);
            task.setStatus("PENDING");
            task.setRetryCount(0);
            task.setMaxRetries(3);
            task.setRetryIntervalSeconds(300L);

            FileAssetRecord fileRecord = resolveOrCreateFileRecord(fileName, filePath, null);
            if (fileRecord != null) {
                task.setFileRecordId(fileRecord.getId());
            }

            FileDistributionTask saved = fileDistributionTaskRepository.save(task);
            saved = ensureLinkage(saved, ackRequired, ackTimeoutMinutes, createdJobInstanceId);
            logFileProcess(
                    saved.getFileRecordId(),
                    "createDistributionTask",
                    "DISPATCH_CREATE",
                    "PROCESSED",
                    "PROCESSED",
                    "SUCCESS",
                    null,
                    0,
                    distributionExtra(targetSystem, targetAddress, null, null));
            log.info("Distribution task created: id={}", saved.getId());
            return saved;
        } catch (BusinessException e) {
            log.warn("Failed to create distribution task (business): {}", fileName, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create distribution task: {}", fileName, e);
            throw new SystemException(
                    ErrorCode.INTERNAL_ERROR, "Failed to create distribution task: " + e.getMessage(), e);
        }
    }

    public void markAsInProgress(Long taskId) {
        markAsInProgress(taskId, null);
    }

    public void markAsInProgress(Long taskId, Long jobInstanceId) {
        FileDistributionTask task = ensureLinkage(loadTask(taskId), false, null, jobInstanceId);

        task.setStatus("IN_PROGRESS");
        task.setLastAttemptTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task = fileDistributionTaskRepository.save(task);
        FileAssetStateMachineService.TransitionResult transition = markDispatching(task, jobInstanceId);

        persistDistributionTrace(task, "DISTRIBUTE", "IN_PROGRESS", null);
        logFileProcess(
                task.getFileRecordId(),
                "markAsInProgress",
                "DISPATCH",
                statusFrom(transition, "PROCESSED"),
                statusTo(transition, "DISPATCHING"),
                "SUCCESS",
                null,
                task.getRetryCount(),
                distributionExtra(task.getTargetSystem(), task.getTargetAddress(), null, jobInstanceId));
    }

    public void markAsSuccess(Long taskId) {
        markAsSuccess(taskId, null, false, null, null);
    }

    public void markAsSuccess(
            Long taskId,
            Long jobInstanceId,
            boolean ackRequired,
            Integer ackTimeoutMinutes,
            Map<String, Object> responsePayload) {
        FileDistributionTask task = ensureLinkage(loadTask(taskId), ackRequired, ackTimeoutMinutes, jobInstanceId);

        task.setStatus("SUCCESS");
        task.setCompletedTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task = fileDistributionTaskRepository.save(task);

        FileAssetStateMachineService.TransitionResult transition = null;
        if (task.getFileRecordId() != null && fileAssetService != null) {
            transition = fileAssetService.markDispatched(task.getFileRecordId());
        }
        if (fileDispatchRecordService != null) {
            fileDispatchRecordService.markSuccess(
                    taskId, jobInstanceId, ackRequired, ackTimeoutMinutes, responsePayload);
        }
        logFileProcess(
                task.getFileRecordId(),
                "markAsSuccess",
                "DISPATCH",
                statusFrom(transition, "DISPATCHING"),
                statusTo(transition, "DISPATCHED"),
                "SUCCESS",
                null,
                task.getRetryCount(),
                distributionExtra(task.getTargetSystem(), task.getTargetAddress(), null, jobInstanceId));

        persistDistributionTrace(task, "DISTRIBUTE", "SUCCESS", null);
    }

    public boolean markAsFailed(Long taskId, String errorMessage) {
        return markAsFailed(taskId, errorMessage, null);
    }

    public boolean markAsFailed(Long taskId, String errorMessage, Long jobInstanceId) {
        FileDistributionTask task = ensureLinkage(loadTask(taskId), false, null, jobInstanceId);

        task.setRetryCount(task.getRetryCount() + 1);
        task.setErrorMessage(errorMessage);
        task.setUpdatedAt(LocalDateTime.now());

        FileAssetStateMachineService.TransitionResult transition = null;
        if (task.getRetryCount() < task.getMaxRetries()) {
            task.setStatus("RETRY");
            log.info(
                    "Task will be retried: id={}, retryCount={}/{}",
                    taskId,
                    task.getRetryCount(),
                    task.getMaxRetries());
            if (fileDispatchRecordService != null) {
                fileDispatchRecordService.markRetryPending(taskId, errorMessage, jobInstanceId);
            }
            if (task.getFileRecordId() != null && fileAssetService != null) {
                transition = fileAssetService.resetToProcessed(
                        task.getFileRecordId(), retryMetadata(errorMessage, task.getRetryCount(), true));
            }
        } else {
            task.setStatus("FAILED");
            task.setCompletedTime(LocalDateTime.now());
            log.error("Task failed after max retries: id={}", taskId);
            if (fileDispatchRecordService != null) {
                fileDispatchRecordService.markFailed(taskId, errorMessage, jobInstanceId);
            }
            if (task.getFileRecordId() != null && fileAssetService != null) {
                transition = fileAssetService.markFailed(task.getFileRecordId(), errorMessage);
            }
        }

        task = fileDistributionTaskRepository.save(task);
        logFileProcess(
                task.getFileRecordId(),
                "markAsFailed",
                "DISPATCH",
                statusFrom(transition, "DISPATCHING"),
                statusTo(transition, "RETRY".equals(task.getStatus()) ? "PROCESSED" : "FAILED"),
                "FAILED",
                errorMessage,
                task.getRetryCount(),
                distributionExtra(task.getTargetSystem(), task.getTargetAddress(), task.getStatus(), jobInstanceId));

        persistDistributionTrace(task, "DISTRIBUTE", task.getStatus(), errorMessage);
        return "RETRY".equals(task.getStatus());
    }

    private void persistDistributionTrace(FileDistributionTask task, String eventType, String status, String message) {
        try {
            RecordTrace trace = new RecordTrace();
            trace.setBusinessKey("FILE:" + task.getId());
            trace.setBatchDate(null);
            trace.setJobName("fileDistribution");
            trace.setSourceFileName(task.getFilePath());
            trace.setEventType(eventType);
            trace.setStatus(status);
            trace.setMessage(message);
            trace.setTargetSystem(task.getTargetSystem());
            trace.setTargetAddress(task.getTargetAddress());
            recordTraceRepository.save(trace);
        } catch (Exception ex) {
            log.error("Failed to persist distribution trace: taskId={}", task.getId(), ex);
        }
    }

    public List<FileDistributionTask> findPendingTasks() {
        return fileDistributionTaskRepository.findByStatus("PENDING");
    }

    public List<FileDistributionTask> findRetryableTasks(int minutesInterval) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesInterval, ChronoUnit.MINUTES);
        return fileDistributionTaskRepository.findRetriableTasks(threshold);
    }

    public List<FileDistributionTask> findTimeoutTasks(int minutesTimeout) {
        LocalDateTime threshold = LocalDateTime.now().minus(minutesTimeout, ChronoUnit.MINUTES);
        return fileDistributionTaskRepository.findTimeoutTasks(threshold);
    }

    @Transactional
    public void retryFailedTask(Long taskId) {
        retryFailedTask(taskId, "SYSTEM", "Automatic distribution retry", null);
    }

    @Transactional
    public void retryFailedTask(Long taskId, String operatorName, String reason) {
        retryFailedTask(taskId, operatorName, reason, null);
    }

    @Transactional
    public void retryFailedTask(Long taskId, String operatorName, String reason, Long jobInstanceId) {
        FileDistributionTask task = ensureLinkage(loadTask(taskId), false, null, jobInstanceId);
        Long targetJobInstanceId = task.getFileRecordId() == null || retryCompensationService == null
                ? jobInstanceId
                : retryCompensationService
                        .findLatestJobInstanceByRelatedFileId(task.getFileRecordId())
                        .map(BusinessJobInstance::getId)
                        .orElse(jobInstanceId);
        Long compensationRecordId = retryCompensationService == null
                ? null
                : retryCompensationService
                        .startCompensation(new RetryCompensationService.StartRequest(
                                CompensationActionType.FILE_RETRY,
                                targetJobInstanceId,
                                null,
                                task.getFileRecordId(),
                                null,
                                task.getId(),
                                null,
                                operatorName,
                                reason,
                                retryCompensationPayload(task)))
                        .getId();

        if (task.getRetryCount() >= task.getMaxRetries()) {
            String message = "Task has exceeded max retries: id=" + taskId;
            log.warn(message);
            if (retryCompensationService != null) {
                retryCompensationService.failCompensation(
                        compensationRecordId, targetJobInstanceId, null, message, Map.of("taskId", taskId));
            }
            return;
        }

        task.setStatus("PENDING");
        task.setCompletedTime(null);
        task.setUpdatedAt(LocalDateTime.now());
        task = fileDistributionTaskRepository.save(task);
        if (fileDispatchRecordService != null) {
            fileDispatchRecordService.markPendingForRetry(taskId, jobInstanceId, false);
        }
        if (task.getFileRecordId() != null && fileAssetService != null) {
            fileAssetService.resetToProcessed(task.getFileRecordId(), retryMetadata(null, task.getRetryCount(), false));
        }

        log.info("Task ready for retry: id={}, nextRetry={}", taskId, task.getRetryCount() + 1);
        if (retryCompensationService != null) {
            retryCompensationService.completeCompensation(
                    compensationRecordId,
                    targetJobInstanceId,
                    null,
                    Map.of("taskId", taskId, "status", task.getStatus(), "nextRetryCount", task.getRetryCount() + 1));
        }
    }

    public FileDistributionStats getStatistics() {
        long pendingCount = fileDistributionTaskRepository.countByStatus("PENDING");
        long inProgressCount = fileDistributionTaskRepository.countByStatus("IN_PROGRESS");
        long retryCount = fileDistributionTaskRepository.countByStatus("RETRY");
        long successCount = fileDistributionTaskRepository.countByStatus("SUCCESS");
        long failedCount = fileDistributionTaskRepository.countByStatus("FAILED");

        return new FileDistributionStats(pendingCount, inProgressCount, retryCount, successCount, failedCount);
    }

    @Transactional(readOnly = true)
    public Optional<FileDistributionTask> findTaskById(Long taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return fileDistributionTaskRepository.findById(taskId);
    }

    @Transactional(readOnly = true)
    public List<FileDistributionTask> findAckTimeoutTasks(int fallbackAckTimeoutMinutes) {
        if (fileDispatchRecordService == null) {
            return List.of();
        }
        return fileDispatchRecordService
                .findAckTimeoutCandidates(LocalDateTime.now(), fallbackAckTimeoutMinutes)
                .stream()
                .map(FileDispatchRecord::getLegacyDistributionTaskId)
                .filter(id -> id != null)
                .map(this::findTaskById)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional
    public void acknowledgeDispatch(
            Long taskId,
            boolean accepted,
            String operatorName,
            String ackMessage,
            Map<String, Object> ackPayload,
            Long jobInstanceId) {
        FileDistributionTask task = ensureLinkage(loadTask(taskId), true, null, jobInstanceId);
        Optional<FileDispatchRecord> updatedRecord = fileDispatchRecordService == null
                ? Optional.empty()
                : fileDispatchRecordService.markAckReceived(
                        taskId, jobInstanceId, accepted, operatorName, ackMessage, ackPayload);

        FileAssetStateMachineService.TransitionResult transition = null;
        if (accepted) {
            task.setStatus("SUCCESS");
            task.setCompletedTime(task.getCompletedTime() == null ? LocalDateTime.now() : task.getCompletedTime());
        } else {
            task.setStatus("RETRY");
            task.setCompletedTime(null);
            task.setErrorMessage(ackMessage);
            if (task.getFileRecordId() != null && fileAssetService != null) {
                transition = fileAssetService.resetToProcessed(
                        task.getFileRecordId(),
                        ackMetadata(ackMessage, operatorName, false, updatedRecord.orElse(null)));
            }
        }
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);

        logFileProcess(
                task.getFileRecordId(),
                "acknowledgeDispatch",
                "DISPATCH_ACK",
                statusFrom(transition, "DISPATCHED"),
                statusTo(transition, accepted ? "DISPATCHED" : "PROCESSED"),
                accepted ? "SUCCESS" : "FAILED",
                ackMessage,
                task.getRetryCount(),
                ackExtra(task, updatedRecord.orElse(null), operatorName, jobInstanceId, ackPayload));
    }

    @Transactional
    public void markAckTimedOut(Long taskId, String message, Long jobInstanceId) {
        FileDistributionTask task = ensureLinkage(loadTask(taskId), true, null, jobInstanceId);
        Long targetJobInstanceId = task.getFileRecordId() == null || retryCompensationService == null
                ? jobInstanceId
                : retryCompensationService
                        .findLatestJobInstanceByRelatedFileId(task.getFileRecordId())
                        .map(BusinessJobInstance::getId)
                        .orElse(jobInstanceId);
        Long compensationRecordId = retryCompensationService == null
                ? null
                : retryCompensationService
                        .startCompensation(new RetryCompensationService.StartRequest(
                                CompensationActionType.DISPATCH_ACK_TIMEOUT,
                                targetJobInstanceId,
                                null,
                                task.getFileRecordId(),
                                null,
                                task.getId(),
                                null,
                                "SYSTEM",
                                message,
                                retryCompensationPayload(task)))
                        .getId();

        Optional<FileDispatchRecord> updatedRecord = fileDispatchRecordService == null
                ? Optional.empty()
                : fileDispatchRecordService.markAckTimeout(
                        taskId, jobInstanceId, message, ackTimeoutPayload(taskId, jobInstanceId));

        // #1 修复:ACK 超时必须计入 retryCount,超上限即落 FAILED 终态,杜绝目标长期不回 ACK 时的无限重发
        task.setRetryCount(task.getRetryCount() + 1);
        boolean exhausted = task.getRetryCount() >= task.getMaxRetries();
        task.setStatus(exhausted ? "FAILED" : "RETRY");
        task.setCompletedTime(null);
        task.setErrorMessage(message);
        task.setUpdatedAt(LocalDateTime.now());
        fileDistributionTaskRepository.save(task);
        if (exhausted) {
            log.warn(
                    "ACK timeout retries exhausted: taskId={}, retryCount={}/{} -> FAILED",
                    task.getId(),
                    task.getRetryCount(),
                    task.getMaxRetries());
        }

        FileAssetStateMachineService.TransitionResult transition = null;
        if (task.getFileRecordId() != null && fileAssetService != null) {
            transition = exhausted
                    ? fileAssetService.markFailed(task.getFileRecordId(), message)
                    : fileAssetService.resetToProcessed(
                            task.getFileRecordId(), ackMetadata(message, "SYSTEM", false, updatedRecord.orElse(null)));
        }
        logFileProcess(
                task.getFileRecordId(),
                "markAckTimedOut",
                "DISPATCH_ACK",
                statusFrom(transition, "DISPATCHED"),
                statusTo(transition, "PROCESSED"),
                "FAILED",
                message,
                task.getRetryCount(),
                ackExtra(task, updatedRecord.orElse(null), "SYSTEM", jobInstanceId, null));

        if (retryCompensationService != null) {
            retryCompensationService.completeCompensation(
                    compensationRecordId,
                    targetJobInstanceId,
                    null,
                    Map.of(
                            "taskId", taskId,
                            "status", task.getStatus(),
                            "ackStatus",
                                    updatedRecord
                                            .map(FileDispatchRecord::getAckStatus)
                                            .orElse("TIMEOUT")));
        }
    }

    @Transactional
    public void scheduleResend(Long taskId, String operatorName, String reason, Long jobInstanceId) {
        FileDistributionTask task = ensureLinkage(loadTask(taskId), true, null, jobInstanceId);
        Long targetJobInstanceId = task.getFileRecordId() == null || retryCompensationService == null
                ? jobInstanceId
                : retryCompensationService
                        .findLatestJobInstanceByRelatedFileId(task.getFileRecordId())
                        .map(BusinessJobInstance::getId)
                        .orElse(jobInstanceId);
        Long compensationRecordId = retryCompensationService == null
                ? null
                : retryCompensationService
                        .startCompensation(new RetryCompensationService.StartRequest(
                                CompensationActionType.DISPATCH_RESEND,
                                targetJobInstanceId,
                                null,
                                task.getFileRecordId(),
                                null,
                                task.getId(),
                                null,
                                operatorName,
                                reason,
                                retryCompensationPayload(task)))
                        .getId();

        // #1 修复:重发同样计入 retryCount,超上限即落 FAILED 终态,避免无界重发
        task.setRetryCount(task.getRetryCount() + 1);
        boolean exhausted = task.getRetryCount() >= task.getMaxRetries();
        task.setStatus(exhausted ? "FAILED" : "RETRY");
        task.setCompletedTime(null);
        task.setUpdatedAt(LocalDateTime.now());
        task.setErrorMessage(reason);
        fileDistributionTaskRepository.save(task);
        if (exhausted) {
            log.warn(
                    "Resend retries exhausted: taskId={}, retryCount={}/{} -> FAILED",
                    task.getId(),
                    task.getRetryCount(),
                    task.getMaxRetries());
        }
        if (!exhausted && fileDispatchRecordService != null) {
            fileDispatchRecordService.markPendingForRetry(taskId, jobInstanceId, true);
        }
        if (task.getFileRecordId() != null && fileAssetService != null) {
            if (exhausted) {
                fileAssetService.markFailed(task.getFileRecordId(), reason);
            } else {
                fileAssetService.resetToProcessed(
                        task.getFileRecordId(), retryMetadata(reason, task.getRetryCount(), true));
            }
        }
        logFileProcess(
                task.getFileRecordId(),
                "scheduleResend",
                "DISPATCH_RESEND",
                "DISPATCHED",
                "PROCESSED",
                "SUCCESS",
                reason,
                task.getRetryCount(),
                distributionExtra(task.getTargetSystem(), task.getTargetAddress(), task.getStatus(), jobInstanceId));
        if (retryCompensationService != null) {
            retryCompensationService.completeCompensation(
                    compensationRecordId,
                    targetJobInstanceId,
                    null,
                    Map.of("taskId", taskId, "status", task.getStatus(), "resendScheduled", true));
        }
    }

    public static class FileDistributionStats {
        public long pendingCount;
        public long inProgressCount;
        public long retryCount;
        public long successCount;
        public long failedCount;

        public FileDistributionStats(
                long pendingCount, long inProgressCount, long retryCount, long successCount, long failedCount) {
            this.pendingCount = pendingCount;
            this.inProgressCount = inProgressCount;
            this.retryCount = retryCount;
            this.successCount = successCount;
            this.failedCount = failedCount;
        }
    }

    private FileDistributionTask loadTask(Long taskId) {
        return fileDistributionTaskRepository
                .findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + taskId));
    }

    private FileDistributionTask ensureLinkage(
            FileDistributionTask task, boolean ackRequired, Integer ackTimeoutMinutes, Long createdJobInstanceId) {
        FileAssetRecord fileRecord = null;
        if (fileAssetService != null) {
            if (task.getFileRecordId() != null) {
                fileRecord = fileAssetService.findById(task.getFileRecordId()).orElse(null);
            }
            if (fileRecord == null) {
                fileRecord = resolveOrCreateFileRecord(task.getFileName(), task.getFilePath(), task.getId());
                if (fileRecord != null && !fileRecord.getId().equals(task.getFileRecordId())) {
                    task.setFileRecordId(fileRecord.getId());
                    task.setUpdatedAt(LocalDateTime.now());
                    task = fileDistributionTaskRepository.save(task);
                }
            }
        }
        if (fileRecord != null
                && fileDispatchRecordService != null
                && task.getId() != null
                && fileDispatchRecordService
                        .findByLegacyDistributionTaskId(task.getId())
                        .isEmpty()) {
            createDispatchRecord(fileRecord, task, ackRequired, ackTimeoutMinutes, createdJobInstanceId);
        }
        return task;
    }

    private FileAssetRecord resolveOrCreateFileRecord(String fileName, String filePath, Long legacyTaskId) {
        if (fileAssetService == null) {
            return null;
        }
        return fileAssetService
                .findLatestByStoredPath(filePath)
                .orElseGet(() -> fileAssetService.registerOutboundFile(
                        fileName,
                        filePath,
                        "FILE_DISTRIBUTION",
                        null,
                        null,
                        null,
                        "PROCESSED",
                        outboundMetadata(legacyTaskId)));
    }

    private Map<String, Object> outboundMetadata(Long legacyTaskId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("createdBy", "FileDistributionService");
        metadata.put("legacyTable", "file_distribution_task");
        if (legacyTaskId != null) {
            metadata.put("legacyDistributionTaskId", legacyTaskId);
        }
        return metadata;
    }

    private void createDispatchRecord(
            FileAssetRecord fileRecord,
            FileDistributionTask task,
            boolean ackRequired,
            Integer ackTimeoutMinutes,
            Long createdJobInstanceId) {
        if (fileRecord == null || fileDispatchRecordService == null) {
            return;
        }
        fileDispatchRecordService.createPendingDispatch(
                fileRecord,
                task.getId(),
                task.getTargetSystem(),
                task.getTargetAddress(),
                task.getMaxRetries(),
                ackRequired,
                ackTimeoutMinutes,
                createdJobInstanceId);
    }

    private FileAssetStateMachineService.TransitionResult markDispatching(
            FileDistributionTask task, Long jobInstanceId) {
        if (task.getFileRecordId() != null && fileAssetService != null) {
            FileAssetStateMachineService.TransitionResult transition =
                    fileAssetService.markDispatching(task.getFileRecordId());
            if (fileDispatchRecordService != null) {
                fileDispatchRecordService.markDispatching(task.getId(), jobInstanceId);
            }
            return transition;
        }
        if (fileDispatchRecordService != null) {
            fileDispatchRecordService.markDispatching(task.getId(), jobInstanceId);
        }
        return null;
    }

    private void logFileProcess(
            Long fileRecordId,
            String stepName,
            String actionType,
            String statusFrom,
            String statusTo,
            String result,
            String errorMessage,
            Integer retryNo,
            Map<String, Object> extra) {
        if (fileProcessLogService == null || fileRecordId == null) {
            return;
        }
        fileProcessLogService.log(
                fileRecordId,
                stepName,
                actionType,
                statusFrom,
                statusTo,
                result,
                null,
                "fileDistributionJob",
                retryNo,
                "FAILED".equals(result) ? "FILE_DISTRIBUTION_FAILED" : null,
                errorMessage,
                extra);
    }

    private Map<String, Object> distributionExtra(
            String targetSystem, String targetAddress, String legacyStatus, Long jobInstanceId) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (targetSystem != null) {
            extra.put("targetSystem", targetSystem);
        }
        if (targetAddress != null) {
            extra.put("targetAddress", targetAddress);
        }
        if (legacyStatus != null) {
            extra.put("legacyStatus", legacyStatus);
        }
        if (jobInstanceId != null) {
            extra.put("jobInstanceId", jobInstanceId);
        }
        return extra;
    }

    private Map<String, Object> retryCompensationPayload(FileDistributionTask task) {
        Map<String, Object> payload =
                distributionExtra(task.getTargetSystem(), task.getTargetAddress(), task.getStatus(), null);
        payload.put("taskId", task.getId());
        payload.put("retryCount", task.getRetryCount());
        payload.put("maxRetries", task.getMaxRetries());
        return payload;
    }

    private Map<String, Object> retryMetadata(String errorMessage, Integer retryCount, boolean retryPending) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("retryPending", retryPending);
        if (retryCount != null) {
            metadata.put("retryCount", retryCount);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            metadata.put("lastError", errorMessage);
        }
        return metadata;
    }

    private Map<String, Object> ackTimeoutPayload(Long taskId, Long jobInstanceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        if (jobInstanceId != null) {
            payload.put("jobInstanceId", jobInstanceId);
        }
        return payload;
    }

    private String statusFrom(FileAssetStateMachineService.TransitionResult transition, String fallback) {
        return transition == null ? fallback : transition.from().name();
    }

    private String statusTo(FileAssetStateMachineService.TransitionResult transition, String fallback) {
        return transition == null ? fallback : transition.to().name();
    }

    private Map<String, Object> ackMetadata(
            String ackMessage, String operatorName, boolean accepted, FileDispatchRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ackAccepted", accepted);
        if (ackMessage != null && !ackMessage.isBlank()) {
            metadata.put("ackMessage", ackMessage);
        }
        if (operatorName != null && !operatorName.isBlank()) {
            metadata.put("operatorName", operatorName);
        }
        if (record != null) {
            metadata.put("dispatchRecordId", record.getId());
            metadata.put("ackStatus", record.getAckStatus());
        }
        return metadata;
    }

    private Map<String, Object> ackExtra(
            FileDistributionTask task,
            FileDispatchRecord record,
            String operatorName,
            Long jobInstanceId,
            Map<String, Object> ackPayload) {
        Map<String, Object> extra =
                distributionExtra(task.getTargetSystem(), task.getTargetAddress(), task.getStatus(), jobInstanceId);
        if (record != null) {
            extra.put("dispatchRecordId", record.getId());
            extra.put("dispatchNo", record.getDispatchNo());
            extra.put("ackStatus", record.getAckStatus());
        }
        if (operatorName != null && !operatorName.isBlank()) {
            extra.put("operatorName", operatorName);
        }
        if (ackPayload != null && !ackPayload.isEmpty()) {
            extra.put("ackPayload", ackPayload);
        }
        return extra;
    }
}
