package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.batch.config.OperationalTaskJobConfig;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.manifest.JsonManifestParser;
import com.example.filebatchprocessor.model.FileDistributionTask;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.repository.FileDistributionTaskRepository;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.FileDispatchRecordService;
import com.example.filebatchprocessor.service.FileDistributionService;
import com.example.filebatchprocessor.service.FileExportService;
import com.example.filebatchprocessor.service.FileProcessLogService;
import com.example.filebatchprocessor.service.FileReceptionGuardService;
import com.example.filebatchprocessor.service.FileReceptionService;
import com.example.filebatchprocessor.service.PartitionedImportService;
import com.example.filebatchprocessor.service.ReceptionGroupService;
import com.example.filebatchprocessor.service.RetryCompensationService;
import com.example.filebatchprocessor.service.distribution.FileDistributorDispatcher;
import com.example.filebatchprocessor.service.distribution.HttpFileDistributor;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

class OperationalTaskJobConfigIT {

    @TempDir
    Path tempDir;

    private final AtomicLong receptionIds = new AtomicLong(1);
    private final AtomicLong distributionIds = new AtomicLong(1);
    private final AtomicLong importedIds = new AtomicLong(1);
    private final AtomicLong partitionedIds = new AtomicLong(1);

    private final Map<Long, FileReceptionQueue> receptionStore = new LinkedHashMap<>();
    private final Map<Long, FileDistributionTask> distributionStore = new LinkedHashMap<>();
    private final Map<Long, ImportedRecord> importedStore = new LinkedHashMap<>();
    private final Map<Long, ImportedRecordPartitioned> partitionedStore = new LinkedHashMap<>();
    private final List<RecordTrace> traceStore = new ArrayList<>();

    private FileReceptionService fileReceptionService;
    private FileDistributionService fileDistributionService;
    private ImportedRecordRepository importedRecordRepository;
    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository;
    private JobOperatorBundle jobs;
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        FileReceptionQueueRepository fileReceptionQueueRepository = fileReceptionQueueRepository();
        FileDistributionTaskRepository fileDistributionTaskRepository = fileDistributionTaskRepository();
        importedRecordRepository = importedRecordRepository();
        importedRecordPartitionedRepository = importedRecordPartitionedRepository();
        RecordTraceRepository recordTraceRepository = recordTraceRepository();

        fileReceptionService = new FileReceptionService(
                fileReceptionQueueRepository,
                mock(FileAssetService.class),
                mock(FileProcessLogService.class),
                FileReceptionGuardService.testingDefaults(),
                mock(JsonManifestParser.class),
                mock(ReceptionGroupService.class),
                ".manifest.json",
                false);
        fileDistributionService = new FileDistributionService(
                fileDistributionTaskRepository,
                recordTraceRepository,
                mock(FileAssetService.class),
                mock(FileDispatchRecordService.class),
                mock(FileProcessLogService.class),
                mock(RetryCompensationService.class),
                mock(BatchMetrics.class));
        // importRecord 现走 jdbcTemplate 的 INSERT ... ON CONFLICT(并发安全),
        // 此处用 fake batchUpdate 把记录落到内存 partitionedStore,模拟真实落库
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbcTemplate.batchUpdate(
                        anyString(),
                        org.mockito.ArgumentMatchers.<ImportedRecordPartitioned>anyList(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers
                                .<org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<
                                                ImportedRecordPartitioned>>
                                        any()))
                .thenAnswer(invocation -> {
                    List<ImportedRecordPartitioned> rows = invocation.getArgument(1);
                    for (ImportedRecordPartitioned row : rows) {
                        boolean exists = partitionedStore.values().stream()
                                .anyMatch(r -> r.getBusinessKey().equals(row.getBusinessKey())
                                        && r.getBatchDate().equals(row.getBatchDate()));
                        if (!exists) {
                            if (row.getId() == null) {
                                row.setId(partitionedIds.getAndIncrement());
                            }
                            partitionedStore.put(row.getId(), row);
                        }
                    }
                    return new int[][] {};
                });
        PartitionedImportService partitionedImportService =
                new PartitionedImportService(importedRecordPartitionedRepository, jdbcTemplate);
        FileExportService fileExportService = new FileExportService(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(FileAssetService.class),
                mock(FileProcessLogService.class));
        FileDistributorDispatcher fileDistributorDispatcher =
                new FileDistributorDispatcher(List.of(new HttpFileDistributor(
                        fileDistributionService,
                        new com.example.filebatchprocessor.service.distribution.DistributionTargetValidator(
                                "127.0.0.1", false),
                        5000L,
                        30000L)));

        JobRepository jobRepository = new ResourcelessJobRepository();
        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();
        OperationalTaskJobConfig config = new OperationalTaskJobConfig(jobRepository, transactionManager);
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);

        TaskExecutorJobOperator jobOperator = new TaskExecutorJobOperator();
        jobOperator.setJobRepository(jobRepository);
        jobOperator.setTaskExecutor(new SyncTaskExecutor());

        Tasklet partitionedImportTasklet =
                config.partitionedImportTasklet(importedRecordRepository, partitionedImportService);
        Tasklet fileExportTasklet = config.fileExportTasklet(importedRecordPartitionedRepository, fileExportService);
        Tasklet fileReceptionTasklet = config.fileReceptionTasklet(fileReceptionService);
        Tasklet fileDistributionTasklet =
                config.fileDistributionTasklet(fileDistributionService, fileDistributorDispatcher);

        jobs = new JobOperatorBundle(
                jobOperator,
                config.partitionedImportJob(config.partitionedImportStep(partitionedImportTasklet), listener),
                config.fileExportJob(config.fileExportStep(fileExportTasklet), listener),
                config.fileReceptionJob(config.fileReceptionStep(fileReceptionTasklet), listener),
                config.fileDistributionJob(config.fileDistributionStep(fileDistributionTasklet), listener));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void shouldRunFileReceptionJobAndMarkReceivedFileCompleted() throws Exception {
        Path source = tempDir.resolve("reception.csv");
        Files.writeString(source, "id,name\n1,Alice\n", StandardCharsets.UTF_8);

        FileReceptionQueue queue = fileReceptionService.receiveFile("reception.csv", source.toString(), "ERP");

        JobExecution execution = jobs.jobOperator.run(
                jobs.fileReceptionJob,
                new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        FileReceptionQueue refreshed = receptionStore.get(queue.getId());
        assertEquals("COMPLETED", refreshed.getStatus());
        assertNotNull(refreshed.getUpdatedAt());
    }

    @Test
    void shouldRunFileDistributionJobAndPushPendingTaskOverHttp() throws Exception {
        Path payload = tempDir.resolve("distribution.csv");
        Files.writeString(payload, "payload", StandardCharsets.UTF_8);

        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/upload", exchange -> {
            requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        String targetUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/upload";
        FileDistributionTask task = fileDistributionService.createDistributionTask(
                "distribution.csv", payload.toString(), "HTTP", targetUrl);

        JobExecution execution = jobs.jobOperator.run(
                jobs.fileDistributionJob,
                new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(1, requestCount.get());

        FileDistributionTask refreshed = distributionStore.get(task.getId());
        assertEquals("SUCCESS", refreshed.getStatus());
        assertNotNull(refreshed.getLastAttemptTime());
        assertNotNull(refreshed.getCompletedTime());
        assertEquals(2, traceStore.size());
    }

    @Test
    void shouldRunPartitionedImportJobAndPersistPartitionRows() throws Exception {
        String batchDate = "2026-03-14";

        ImportedRecord record = new ImportedRecord();
        record.setBusinessKey("bk-" + UUID.randomUUID());
        record.setName("Alice");
        record.setDescription("from imported_records");
        record.setBatchDate(batchDate);
        importedRecordRepository.save(record);

        JobExecution execution = jobs.jobOperator.run(
                jobs.partitionedImportJob,
                new JobParametersBuilder()
                        .addString("batchDate", batchDate)
                        .addLong("run.id", System.nanoTime())
                        .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        ImportedRecordPartitioned imported = partitionedStore.values().stream()
                .filter(row ->
                        record.getBusinessKey().equals(row.getBusinessKey()) && batchDate.equals(row.getBatchDate()))
                .findFirst()
                .orElseThrow();
        assertEquals("Alice", imported.getName());
        assertEquals("from imported_records", imported.getDescription());
        assertEquals("2026_03", imported.getPartitionKey());
    }

    @Test
    void shouldRunFileExportJobAndWriteCsvOutput() throws Exception {
        String batchDate = "2026-03-14";

        ImportedRecordPartitioned row = new ImportedRecordPartitioned();
        row.setBusinessKey("export-" + UUID.randomUUID());
        row.setName("ExportName");
        row.setDescription("ExportDescription");
        row.setBatchDate(batchDate);
        row.setPartitionKey("2026_03");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        importedRecordPartitionedRepository.save(row);

        JobExecution execution = jobs.jobOperator.run(
                jobs.fileExportJob,
                new JobParametersBuilder()
                        .addString("batchDate", batchDate)
                        .addString("format", "csv")
                        .addString("outputDir", tempDir.toString())
                        .addLong("run.id", System.nanoTime())
                        .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Path exported = tempDir.resolve("file_export_" + batchDate + ".csv");
        assertTrue(Files.exists(exported));
        String content = Files.readString(exported);
        assertTrue(content.contains("business_key"));
        assertTrue(content.contains("batch_date"));
        assertTrue(content.contains(row.getBusinessKey()));
        assertTrue(content.contains("ExportName"));
    }

    private FileReceptionQueueRepository fileReceptionQueueRepository() {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        when(repository.findByFileName(anyString()))
                .thenAnswer(invocation -> receptionStore.values().stream()
                        .filter(queue -> invocation.getArgument(0).equals(queue.getFileName()))
                        .findFirst());
        when(repository.findById(any(Long.class)))
                .thenAnswer(invocation -> Optional.ofNullable(receptionStore.get(invocation.getArgument(0))));
        when(repository.findByStatusOrderByCreatedAtAsc(anyString()))
                .thenAnswer(invocation -> receptionStore.values().stream()
                        .filter(queue -> invocation.getArgument(0).equals(queue.getStatus()))
                        .sorted(Comparator.comparing(FileReceptionQueue::getCreatedAt))
                        .toList());
        when(repository.save(any(FileReceptionQueue.class))).thenAnswer(invocation -> {
            FileReceptionQueue queue = invocation.getArgument(0);
            if (queue.getId() == null) {
                queue.setId(receptionIds.getAndIncrement());
            }
            receptionStore.put(queue.getId(), queue);
            return queue;
        });
        return repository;
    }

    private FileDistributionTaskRepository fileDistributionTaskRepository() {
        FileDistributionTaskRepository repository = mock(FileDistributionTaskRepository.class);
        when(repository.findById(any(Long.class)))
                .thenAnswer(invocation -> Optional.ofNullable(distributionStore.get(invocation.getArgument(0))));
        when(repository.findByStatus(anyString()))
                .thenAnswer(invocation -> distributionStore.values().stream()
                        .filter(task -> invocation.getArgument(0).equals(task.getStatus()))
                        .toList());
        when(repository.save(any(FileDistributionTask.class))).thenAnswer(invocation -> {
            FileDistributionTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(distributionIds.getAndIncrement());
            }
            distributionStore.put(task.getId(), task);
            return task;
        });
        return repository;
    }

    private ImportedRecordRepository importedRecordRepository() {
        ImportedRecordRepository repository = mock(ImportedRecordRepository.class);
        when(repository.findByBatchDateOrderByIdAsc(anyString()))
                .thenAnswer(invocation -> importedStore.values().stream()
                        .filter(record -> invocation.getArgument(0).equals(record.getBatchDate()))
                        .sorted(Comparator.comparing(ImportedRecord::getId))
                        .toList());
        when(repository.save(any(ImportedRecord.class))).thenAnswer(invocation -> {
            ImportedRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(importedIds.getAndIncrement());
            }
            importedStore.put(record.getId(), record);
            return record;
        });
        return repository;
    }

    private ImportedRecordPartitionedRepository importedRecordPartitionedRepository() {
        ImportedRecordPartitionedRepository repository = mock(ImportedRecordPartitionedRepository.class);
        when(repository.findByBusinessKeyAndBatchDate(anyString(), anyString()))
                .thenAnswer(invocation -> partitionedStore.values().stream()
                        .filter(record -> invocation.getArgument(0).equals(record.getBusinessKey())
                                && invocation.getArgument(1).equals(record.getBatchDate()))
                        .findFirst());
        when(repository.findByBatchDate(anyString()))
                .thenAnswer(invocation -> partitionedStore.values().stream()
                        .filter(record -> invocation.getArgument(0).equals(record.getBatchDate()))
                        .toList());
        when(repository.save(any(ImportedRecordPartitioned.class))).thenAnswer(invocation -> {
            ImportedRecordPartitioned record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(partitionedIds.getAndIncrement());
            }
            partitionedStore.put(record.getId(), record);
            return record;
        });
        return repository;
    }

    private RecordTraceRepository recordTraceRepository() {
        RecordTraceRepository repository = mock(RecordTraceRepository.class);
        when(repository.save(any(RecordTrace.class))).thenAnswer(invocation -> {
            RecordTrace trace = invocation.getArgument(0);
            traceStore.add(trace);
            return trace;
        });
        return repository;
    }

    private record JobOperatorBundle(
            TaskExecutorJobOperator jobOperator,
            Job partitionedImportJob,
            Job fileExportJob,
            Job fileReceptionJob,
            Job fileDistributionJob) {}
}
