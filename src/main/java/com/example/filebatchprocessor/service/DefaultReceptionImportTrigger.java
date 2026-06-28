package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.params.ImportJobParams;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import java.time.LocalDate;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 清单对账通过后,复用单体内现有 fileImportJob 触发入库。
 */
@Slf4j
@Component
public class DefaultReceptionImportTrigger implements ReceptionImportTrigger {

    private final FileReceptionQueueRepository queueRepository;
    private final FileReceptionService fileReceptionService;
    private final ReceptionGroupRepository groupRepository;
    private final JobLauncher jobLauncher;
    private final Job fileImportJob;

    public DefaultReceptionImportTrigger(
            FileReceptionQueueRepository queueRepository,
            FileReceptionService fileReceptionService,
            ReceptionGroupRepository groupRepository,
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            @Qualifier(BatchJobNames.FILE_IMPORT_JOB) Job fileImportJob) {
        this.queueRepository = queueRepository;
        this.fileReceptionService = fileReceptionService;
        this.groupRepository = groupRepository;
        this.jobLauncher = jobLauncher;
        this.fileImportJob = fileImportJob;
    }

    @Override
    public void triggerImport(Long queueId) {
        if (queueId == null) {
            throw new IllegalArgumentException("queueId is required");
        }
        FileReceptionQueue queue = queueRepository
                .findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("reception queue not found: " + queueId));
        if ("COMPLETED".equals(queue.getStatus()) || "PROCESSING".equals(queue.getStatus())) {
            log.info("[reception-import] skip duplicate trigger: queueId={} status={}", queueId, queue.getStatus());
            return;
        }

        fileReceptionService.markAsReady(queueId);
        fileReceptionService.markAsProcessing(queueId);
        JobExecution execution;
        try {
            execution = jobLauncher.run(fileImportJob, jobParameters(queue));
        } catch (Exception e) {
            fileReceptionService.markAsFailed(queueId, e.getMessage());
            throw new IllegalStateException("failed to trigger reception import: queueId=" + queueId, e);
        }
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            fileReceptionService.markAsCompleted(queueId);
            log.info("[reception-import] import completed: queueId={} executionId={}", queueId, execution.getId());
            return;
        }
        String reason = "fileImportJob ended with status " + execution.getStatus();
        fileReceptionService.markAsFailed(queueId, reason);
        throw new IllegalStateException(reason);
    }

    private JobParameters jobParameters(FileReceptionQueue queue) {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addString(ImportJobParams.KEY_INPUT_FILE_NAME, queue.getFilePath())
                .addString(ImportJobParams.KEY_BATCH_DATE, resolveBatchDate(queue))
                .addString(ImportJobParams.KEY_FILE_FORMAT, inferFileFormat(queue))
                .addString("reception.queue.id", String.valueOf(queue.getId()))
                .addLong("triggeredAt", System.currentTimeMillis());
        if (queue.getSourceSystem() != null && !queue.getSourceSystem().isBlank()) {
            builder.addString("sourceSystem", queue.getSourceSystem());
        }
        return builder.toJobParameters();
    }

    private String resolveBatchDate(FileReceptionQueue queue) {
        if (queue.getReceptionGroupId() != null) {
            return groupRepository
                    .findById(queue.getReceptionGroupId())
                    .map(group -> group.getBizDate())
                    .filter(v -> v != null && !v.isBlank())
                    .orElseGet(() -> LocalDate.now().toString());
        }
        return LocalDate.now().toString();
    }

    private String inferFileFormat(FileReceptionQueue queue) {
        String name = queue.getFileName() == null || queue.getFileName().isBlank()
                ? queue.getFilePath()
                : queue.getFileName();
        if (name == null) {
            return "CSV";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            return "JSON";
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "EXCEL";
        }
        return "CSV";
    }
}
