package com.example.filebatchprocessor.batch.config;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.ReceptionGroup;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import com.example.filebatchprocessor.service.FileDistributionService;
import com.example.filebatchprocessor.service.FileExportService;
import com.example.filebatchprocessor.service.FileReceptionService;
import com.example.filebatchprocessor.service.JobInstanceParameters;
import com.example.filebatchprocessor.service.PartitionedImportService;
import com.example.filebatchprocessor.service.ReceptionGroupCompletionService;
import com.example.filebatchprocessor.service.distribution.FileDistributorDispatcher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
public class OperationalTaskJobConfig {

    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int DEFAULT_RECEPTION_TIMEOUT_MINUTES = 360;
    private static final int DEFAULT_DISTRIBUTION_RETRY_MINUTES = 15;
    private static final int DEFAULT_DISTRIBUTION_TIMEOUT_MINUTES = 720;
    private static final int DEFAULT_DISTRIBUTION_ACK_TIMEOUT_MINUTES = 120;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    // tasklet 式文件导出会一次性物化结果,超过上界时应改用流式 dataExportJob。
    @org.springframework.beans.factory.annotation.Value("${batch.file-export.max-rows:500000}")
    private int fileExportMaxRows;

    public OperationalTaskJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Tasklet partitionedImportTasklet(
            ImportedRecordRepository importedRecordRepository, PartitionedImportService partitionedImportService) {
        return (contribution, chunkContext) -> {
            String batchDate = resolveBatchDate(
                    chunkContext.getStepContext().getJobParameters().get("batchDate"));
            List<ImportedRecord> records = importedRecordRepository.findByBatchDateOrderByIdAsc(batchDate);
            // 旧分区导入按行写入会产生大量往返,这里按批幂等写入降低数据库压力。
            int imported = 0;
            List<ImportedRecordPartitioned> buffer = new ArrayList<>(1000);
            for (ImportedRecord record : records) {
                ImportedRecordPartitioned e = new ImportedRecordPartitioned();
                e.setBusinessKey(record.getBusinessKey());
                e.setName(record.getName());
                e.setDescription(record.getDescription());
                e.setBatchDate(batchDate);
                // 显式补全 partition_key/时间戳(与 importRecord 一致):派生在实体上完成,
                // 不依赖底层 INSERT 的 setter,生产与测试行为一致。
                e.setPartitionKey(partitionedImportService.generatePartitionKey(batchDate));
                LocalDateTime now = LocalDateTime.now();
                e.setCreatedAt(now);
                e.setUpdatedAt(now);
                buffer.add(e);
                if (buffer.size() >= 1000) {
                    imported += partitionedImportService.batchImportIdempotent(buffer);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                imported += partitionedImportService.batchImportIdempotent(buffer);
            }
            log.info(
                    "partitionedImportJob completed: batchDate={}, sourceRecords={}, imported={}",
                    batchDate,
                    records.size(),
                    imported);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step partitionedImportStep(Tasklet partitionedImportTasklet) {
        return new StepBuilder("partitionedImportStep", jobRepository)
                .tasklet(partitionedImportTasklet, transactionManager)
                .build();
    }

    @Bean("partitionedImportJob")
    public Job partitionedImportJob(Step partitionedImportStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("partitionedImportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(partitionedImportStep)
                .build();
    }

    @Bean
    public Tasklet fileExportTasklet(
            ImportedRecordPartitionedRepository importedRecordPartitionedRepository,
            FileExportService fileExportService) {
        return (contribution, chunkContext) -> {
            var params = chunkContext.getStepContext().getJobParameters();
            String batchDate = resolveBatchDate(params.get("batchDate"));
            String format = resolveString(params.get("format"), "csv").toLowerCase(Locale.ROOT);
            String outputDir = resolveString(params.get("outputDir"), "");
            String extension =
                    switch (format) {
                        case "csv", "json", "excel" -> format;
                        default -> throw new IllegalArgumentException("Unsupported export format: " + format);
                    };
            String fileName = "file_export_" + sanitizeBatchDate(batchDate) + "." + extension;

            List<ImportedRecordPartitioned> records = importedRecordPartitionedRepository.findByBatchDate(batchDate);
            // 该 tasklet 把整批结果一次性物化进内存数组,大批量会 OOM。设置上界保护;
            // 超过上界应改用流式的 dataExportJob(JdbcCursorItemReader + fetchSize)。
            if (fileExportMaxRows > 0 && records.size() > fileExportMaxRows) {
                throw new IllegalStateException("file export rows " + records.size() + " exceed cap "
                        + fileExportMaxRows + "; use the streaming dataExportJob for large exports");
            }
            String[] headers = {"business_key", "name", "description", "batch_date"};
            String[][] rows = records.stream()
                    .map(record -> new String[] {
                        nullSafe(record.getBusinessKey()),
                        nullSafe(record.getName()),
                        nullSafe(record.getDescription()),
                        nullSafe(record.getBatchDate())
                    })
                    .toArray(String[][]::new);

            String exportedPath =
                    switch (format) {
                        case "csv" -> fileExportService.exportToCSV(outputDir, fileName, rows, headers);
                        case "json" -> fileExportService.exportToJSON(outputDir, fileName, records);
                        case "excel" -> fileExportService.exportToExcel(outputDir, fileName, rows, headers);
                        default -> throw new IllegalArgumentException("Unsupported export format: " + format);
                    };
            log.info(
                    "fileExportJob completed: batchDate={}, records={}, output={}",
                    batchDate,
                    records.size(),
                    exportedPath);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step fileExportStep(Tasklet fileExportTasklet) {
        return new StepBuilder("fileExportStep", jobRepository)
                .tasklet(fileExportTasklet, transactionManager)
                .build();
    }

    @Bean(BatchJobNames.FILE_EXPORT_JOB)
    public Job fileExportJob(Step fileExportStep, JobCompletionNotificationListener listener) {
        return new JobBuilder(BatchJobNames.FILE_EXPORT_JOB, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(fileExportStep)
                .build();
    }

    @Bean
    public Tasklet fileReceptionTasklet(FileReceptionService fileReceptionService) {
        return (contribution, chunkContext) -> {
            List<FileReceptionQueue> pendingFiles = fileReceptionService.findPendingFiles();
            int completed = 0;
            int waiting = 0;
            for (FileReceptionQueue file : pendingFiles) {
                try {
                    if (fileReceptionService.verifyFileIntegrity(file.getId())) {
                        fileReceptionService.markAsReady(file.getId());
                        fileReceptionService.markAsProcessing(file.getId());
                        fileReceptionService.markAsCompleted(file.getId());
                        completed++;
                    } else {
                        fileReceptionService.markAsWaiting(file.getId(), "File integrity check failed");
                        waiting++;
                    }
                } catch (Exception ex) {
                    fileReceptionService.markAsFailed(
                            file.getId(), "File reception monitor failed: " + ex.getMessage());
                }
            }
            log.info(
                    "fileReceptionJob completed: pendingFiles={}, completed={}, waiting={}",
                    pendingFiles.size(),
                    completed,
                    waiting);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step fileReceptionStep(Tasklet fileReceptionTasklet) {
        return new StepBuilder("fileReceptionStep", jobRepository)
                .tasklet(fileReceptionTasklet, transactionManager)
                .build();
    }

    @Bean("fileReceptionJob")
    public Job fileReceptionJob(Step fileReceptionStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("fileReceptionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(fileReceptionStep)
                .build();
    }

    @Bean
    public Tasklet receptionGroupTasklet(
            ReceptionGroupRepository groupRepo, ReceptionGroupCompletionService completionService) {
        return (contribution, chunkContext) -> {
            List<ReceptionGroup> waitingGroups = groupRepo.findByStatus("WAITING_FILES");
            int evaluated = 0;
            for (ReceptionGroup group : waitingGroups) {
                completionService.evaluate(group.getId());
                evaluated++;
            }
            log.info("receptionGroupJob completed: waitingGroups={}, evaluated={}", waitingGroups.size(), evaluated);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step receptionGroupStep(Tasklet receptionGroupTasklet) {
        return new StepBuilder("receptionGroupStep", jobRepository)
                .tasklet(receptionGroupTasklet, transactionManager)
                .build();
    }

    @Bean("receptionGroupJob")
    public Job receptionGroupJob(Step receptionGroupStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("receptionGroupJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(receptionGroupStep)
                .build();
    }

    @Bean
    public Tasklet fileReceptionTimeoutTasklet(FileReceptionService fileReceptionService) {
        return (contribution, chunkContext) -> {
            var params = chunkContext.getStepContext().getJobParameters();
            int timeoutMinutes = resolveInt(params.get("timeoutMinutes"), DEFAULT_RECEPTION_TIMEOUT_MINUTES);
            List<FileReceptionQueue> overdueFiles = fileReceptionService.findOverdueFiles(timeoutMinutes);
            for (FileReceptionQueue file : overdueFiles) {
                fileReceptionService.markAsFailed(
                        file.getId(), "File reception timeout exceeded " + timeoutMinutes + " minutes");
            }
            log.info(
                    "fileReceptionTimeoutJob completed: timeoutMinutes={}, overdueFiles={}",
                    timeoutMinutes,
                    overdueFiles.size());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step fileReceptionTimeoutStep(Tasklet fileReceptionTimeoutTasklet) {
        return new StepBuilder("fileReceptionTimeoutStep", jobRepository)
                .tasklet(fileReceptionTimeoutTasklet, transactionManager)
                .build();
    }

    @Bean("fileReceptionTimeoutJob")
    public Job fileReceptionTimeoutJob(Step fileReceptionTimeoutStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("fileReceptionTimeoutJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(fileReceptionTimeoutStep)
                .build();
    }

    @Bean
    public Tasklet fileDistributionTasklet(
            FileDistributionService fileDistributionService, FileDistributorDispatcher fileDistributorDispatcher) {
        return (contribution, chunkContext) -> {
            Long jobInstanceId = resolveLong(chunkContext
                    .getStepContext()
                    .getJobParameters()
                    .get(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID));
            List<FileDistributionTask> pendingTasks = fileDistributionService.findPendingTasks();
            for (FileDistributionTask task : pendingTasks) {
                fileDistributorDispatcher.distribute(task, jobInstanceId);
            }
            log.info("fileDistributionJob completed: pendingTasks={}", pendingTasks.size());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step fileDistributionStep(Tasklet fileDistributionTasklet) {
        return new StepBuilder("fileDistributionStep", jobRepository)
                .tasklet(fileDistributionTasklet, transactionManager)
                .build();
    }

    @Bean("fileDistributionJob")
    public Job fileDistributionJob(Step fileDistributionStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("fileDistributionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(fileDistributionStep)
                .build();
    }

    @Bean
    public Tasklet fileDistributionRetryTasklet(
            FileDistributionService fileDistributionService,
            FileDistributionTaskRepository fileDistributionTaskRepository,
            FileDistributorDispatcher fileDistributorDispatcher) {
        return (contribution, chunkContext) -> {
            var params = chunkContext.getStepContext().getJobParameters();
            Long jobInstanceId = resolveLong(params.get(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID));
            int retryMinutes = resolveInt(params.get("retryMinutes"), DEFAULT_DISTRIBUTION_RETRY_MINUTES);
            List<FileDistributionTask> retryableTasks = fileDistributionService.findRetryableTasks(retryMinutes);
            int retried = 0;
            for (FileDistributionTask task : retryableTasks) {
                fileDistributionService.retryFailedTask(
                        task.getId(), "SYSTEM", "Automatic distribution retry", jobInstanceId);
                fileDistributionTaskRepository
                        .findById(task.getId())
                        .ifPresent(refreshed -> fileDistributorDispatcher.distribute(refreshed, jobInstanceId));
                retried++;
            }
            log.info("fileDistributionRetryJob completed: retryMinutes={}, retriedTasks={}", retryMinutes, retried);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step fileDistributionRetryStep(Tasklet fileDistributionRetryTasklet) {
        return new StepBuilder("fileDistributionRetryStep", jobRepository)
                .tasklet(fileDistributionRetryTasklet, transactionManager)
                .build();
    }

    @Bean("fileDistributionRetryJob")
    public Job fileDistributionRetryJob(Step fileDistributionRetryStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("fileDistributionRetryJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(fileDistributionRetryStep)
                .build();
    }

    @Bean
    public Tasklet fileDistributionTimeoutTasklet(FileDistributionService fileDistributionService) {
        return (contribution, chunkContext) -> {
            var params = chunkContext.getStepContext().getJobParameters();
            Long jobInstanceId = resolveLong(params.get(JobInstanceParameters.BUSINESS_JOB_INSTANCE_ID));
            int timeoutMinutes = resolveInt(params.get("timeoutMinutes"), DEFAULT_DISTRIBUTION_TIMEOUT_MINUTES);
            int ackTimeoutMinutes =
                    resolveInt(params.get("ackTimeoutMinutes"), DEFAULT_DISTRIBUTION_ACK_TIMEOUT_MINUTES);
            List<FileDistributionTask> timeoutTasks = fileDistributionService.findTimeoutTasks(timeoutMinutes);
            for (FileDistributionTask task : timeoutTasks) {
                fileDistributionService.markAsFailed(
                        task.getId(),
                        "File distribution timeout exceeded " + timeoutMinutes + " minutes",
                        jobInstanceId);
            }
            List<FileDistributionTask> ackTimeoutTasks = fileDistributionService.findAckTimeoutTasks(ackTimeoutMinutes);
            for (FileDistributionTask task : ackTimeoutTasks) {
                fileDistributionService.markAckTimedOut(
                        task.getId(), "Dispatch ack timeout exceeded " + ackTimeoutMinutes + " minutes", jobInstanceId);
            }
            log.info(
                    "fileDistributionTimeoutJob completed: timeoutMinutes={}, timeoutTasks={}, ackTimeoutMinutes={}, ackTimeoutTasks={}",
                    timeoutMinutes,
                    timeoutTasks.size(),
                    ackTimeoutMinutes,
                    ackTimeoutTasks.size());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step fileDistributionTimeoutStep(Tasklet fileDistributionTimeoutTasklet) {
        return new StepBuilder("fileDistributionTimeoutStep", jobRepository)
                .tasklet(fileDistributionTimeoutTasklet, transactionManager)
                .build();
    }

    @Bean("fileDistributionTimeoutJob")
    public Job fileDistributionTimeoutJob(
            Step fileDistributionTimeoutStep, JobCompletionNotificationListener listener) {
        return new JobBuilder("fileDistributionTimeoutJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(fileDistributionTimeoutStep)
                .build();
    }

    private static String resolveBatchDate(Object raw) {
        String value = resolveString(raw, "");
        return value.isBlank() ? LocalDate.now().format(BATCH_DATE_FORMATTER) : value;
    }

    private static String resolveString(Object raw, String defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private static int resolveInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static Long resolveLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String sanitizeBatchDate(String batchDate) {
        return batchDate.replaceAll("[^0-9A-Za-z_-]", "_");
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
