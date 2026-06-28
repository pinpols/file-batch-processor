package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileAssetStatus;
import com.example.filebatchprocessor.model.FileRetentionPolicy;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.FileRetentionPolicyRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupMemberRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class FileArchivalService {

    private final FileAssetRecordRepository fileAssetRepository;
    private final FileRetentionPolicyRepository retentionPolicyRepository;
    private final FileAssetStateMachineService stateMachineService;

    @Value("${file.archive.enabled:true}")
    private boolean enabled;

    @Value("${file.archive.dry-run:true}")
    private boolean dryRun;

    public FileArchivalService(
            FileAssetRecordRepository fileAssetRepository,
            FileRetentionPolicyRepository retentionPolicyRepository,
            FileAssetStateMachineService stateMachineService) {
        this.fileAssetRepository = fileAssetRepository;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.stateMachineService = stateMachineService;
    }

    // #8:归档/删文件只让 leader 跑,避免多副本并发改/删同一批文件
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SchedulerLeaderService schedulerLeaderService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FileDispatchRecordRepository fileDispatchRecordRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FileReceptionQueueRepository fileReceptionQueueRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReceptionGroupMemberRepository receptionGroupMemberRepository;

    @Scheduled(cron = "${file.archive.cron:0 0 2 * * *}")
    public void runArchiveJob() {
        if (schedulerLeaderService != null && !schedulerLeaderService.isLeader()) {
            return;
        }
        if (!enabled) {
            return;
        }

        log.info("Starting file archival job");

        List<FileRetentionPolicy> policies = retentionPolicyRepository.findByEnabledTrue();
        int archivedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;

        for (FileRetentionPolicy policy : policies) {
            try {
                List<FileAssetRecord> filesToArchive = fileAssetRepository.findFilesForArchive(
                        policy.getFileCategory(), LocalDateTime.now().minusDays(policy.getRetentionDays()), 100);

                for (FileAssetRecord file : filesToArchive) {
                    try {
                        if (!isTerminalState(file.getStatus())) {
                            log.debug("Skipping file {} - not in terminal state", file.getId());
                            continue;
                        }

                        if (hasActiveDependencies(file)) {
                            log.debug("Skipping file {} - has active dependencies", file.getId());
                            continue;
                        }

                        if (policy.getArchiveBeforeDelete()) {
                            if (!dryRun) {
                                archiveFile(file);
                            }
                            archivedCount++;
                        } else {
                            if (!dryRun) {
                                deleteFile(file);
                            }
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        log.error("Failed to process file {} for archival", file.getId(), e);
                        failedCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process policy {}: {}", policy.getPolicyName(), e.getMessage());
            }
        }

        log.info(
                "Archive job completed: archived={}, deleted={}, failed={}, dryRun={}",
                archivedCount,
                deletedCount,
                failedCount,
                dryRun);
    }

    private boolean isTerminalState(String status) {
        return "PROCESSED".equals(status) || "DISPATCHED".equals(status) || "ARCHIVED".equals(status);
    }

    private boolean hasActiveDependencies(FileAssetRecord file) {
        if (file.getId() == null) {
            return false;
        }
        if (fileDispatchRecordRepository != null
                && fileDispatchRecordRepository.countByFileRecordIdAndDispatchStatusIn(
                                file.getId(), List.of("PENDING", "DISPATCHING", "RETRY"))
                        > 0) {
            return true;
        }
        if (fileReceptionQueueRepository != null
                && fileReceptionQueueRepository.countByFileRecordIdAndStatusIn(
                                file.getId(), List.of("RECEIVED", "WAITING", "PROCESSING"))
                        > 0) {
            return true;
        }
        if (receptionGroupMemberRepository != null) {
            try {
                if (fileReceptionQueueRepository == null) {
                    return false;
                }
                for (var queue : fileReceptionQueueRepository.findByFileRecordId(file.getId())) {
                    if (queue.getId() != null
                            && receptionGroupMemberRepository.countByActualQueueId(queue.getId()) > 0) {
                        return true;
                    }
                }
                return false;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private void archiveFile(FileAssetRecord file) {
        stateMachineService.transition(file.getId(), FileAssetStatus.ARCHIVED, "auto-archive", null);
        log.info("Archived file: {}", file.getFileNo());
    }

    private void deleteFile(FileAssetRecord file) {
        if (file.getStoredPath() != null) {
            try {
                Path path = Path.of(file.getStoredPath());
                if (Files.exists(path)) {
                    Files.delete(path);
                    log.info("Deleted physical file: {}", path);
                }
            } catch (Exception e) {
                log.warn("Failed to delete physical file: {}", file.getStoredPath(), e);
            }
        }

        file.setDeletedFlag(true);
        file.setDeletedTime(LocalDateTime.now());
        fileAssetRepository.save(file);
        log.info("Marked file as deleted: {}", file.getFileNo());
    }

    public void archiveFileManually(Long fileId, String operator) {
        FileAssetRecord file = fileAssetRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        if (!isTerminalState(file.getStatus())) {
            throw new IllegalStateException("File is not in terminal state: " + file.getStatus());
        }

        stateMachineService.transition(
                fileId, FileAssetStatus.ARCHIVED, "manual-archive", java.util.Map.of("operator", operator));
    }

    public void deleteFileManually(Long fileId, String operator) {
        FileAssetRecord file = fileAssetRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        if (!"ARCHIVED".equals(file.getStatus())) {
            throw new IllegalStateException("File must be archived before deletion: " + file.getStatus());
        }

        deleteFile(file);
    }
}
