//package com.example.filebatchprocessor.e2e;
//
//import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
//import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
//import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
//import com.example.filebatchprocessor.model.ImportedRecord;
//import com.example.filebatchprocessor.repository.ImportedRecordRepository;
//import com.example.filebatchprocessor.repository.RecordTraceRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.Order;
//import org.junit.jupiter.api.io.TempDir;
//import org.springframework.batch.core.BatchStatus;
//import org.springframework.batch.core.job.Job;
//import org.springframework.batch.core.job.JobExecution;
//import org.springframework.batch.core.job.parameters.JobParameters;
//import org.springframework.batch.core.job.parameters.JobParametersBuilder;
//import org.springframework.batch.core.launch.JobLauncher;
//import org.springframework.batch.core.step.Step;
//import org.springframework.batch.test.context.SpringBatchTest;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//import static org.springframework.batch.core.BatchStatus.*;
//
//@SpringBootTest
//@SpringBatchTest
//@ActiveProfiles("test")
//class MixedScenarioE2ETest {
//
//    @Autowired
//    private JobLauncher jobLauncher;
//
//    @Autowired
//    private FileImportJobConfig fileImportJobConfig;
//
//    @Autowired
//    private DataExportJobConfig dataExportJobConfig;
//
//    @Autowired
//    private ImportedRecordRepository importedRecordRepository;
//
//    @Autowired
//    private RecordTraceRepository recordTraceRepository;
//
//    @TempDir
//    Path tempDir;
//
//    private Path importFile;
//    private Path exportFile;
//    private Path distributionDir;
//
//    @BeforeEach
//    void setUp() throws IOException {
//        // Clean up repositories
//        importedRecordRepository.deleteAll();
//        recordTraceRepository.deleteAll();
//
//        // Create directories
//        distributionDir = tempDir.resolve("distribution");
//        Files.createDirectories(distributionDir);
//
//        // Create test files
//        importFile = tempDir.resolve("mixed_scenario_input.csv");
//        String importContent = "id,name,description,batch_date\n" +
//                            "1,Mixed Test 1,Mixed Description 1," + LocalDate.now() + "\n" +
//                            "2,Mixed Test 2,Mixed Description 2," + LocalDate.now() + "\n" +
//                            "3,Mixed Test 3,Mixed Description 3," + LocalDate.now() + "\n";
//        Files.writeString(importFile, importContent);
//
//        exportFile = tempDir.resolve("mixed_scenario_output.csv");
//    }
//
//    @Test
//    @Order(1)
//    void shouldCompleteImportExportJointFlow() throws Exception {
//        // Given - Simulate import → export joint flow
//        String batchDate = LocalDate.now().toString();
//
//        // Step 1: Import data
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job importJob = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters importParams = new JobParametersBuilder()
//                .addString("inputFileName", importFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("jointFlow", "import-export-joint")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution importExecution = jobLauncher.run(importJob, importParams);
//
//        // Then
//        assertEquals(COMPLETED, importExecution.getStatus());
//        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
//        assertEquals(3, importedRecords.size());
//
//        // Step 2: Export the same data
//        JobCompletionNotificationListener exportListener = mock(JobCompletionNotificationListener.class);
//        Step exportStep = mock(Step.class);
//        Job exportJob = dataExportJobConfig.dataExportJob(exportListener, exportStep);
//        JobParameters exportParams = new JobParametersBuilder()
//                .addString("export.sql", "SELECT id, name, description, batch_date FROM imported_records WHERE batch_date = '" + batchDate + "'")
//                .addString("output.file.name", exportFile.toString())
//                .addString("jointFlow", "import-export-joint")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution exportExecution = jobLauncher.run(exportJob, exportParams);
//
//        // Then
//        assertEquals(COMPLETED, exportExecution.getStatus());
//        assertTrue(Files.exists(exportFile));
//
//        // Verify data integrity
//        String exportContent = Files.readString(exportFile);
//        assertTrue(exportContent.contains("Mixed Test"));
//
//        // Verify joint flow tracking
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t -> "import-export-joint".equals(t.getBusinessKey())));
//    }
//
//    @Test
//    @Order(2)
//    void shouldCompleteRealTimeMonitoringScenario() throws Exception {
//        // Given - Simulate real-time monitoring during data processing
//        String batchDate = LocalDate.now().toString();
//        Path monitoringFile = tempDir.resolve("realtime_monitoring.csv");
//        String monitoringContent = "id,name,description\n" +
//                               "1,Realtime Test 1,Realtime Description 1\n" +
//                               "2,Realtime Test 2,Realtime Description 2\n";
//        Files.writeString(monitoringFile, monitoringContent);
//
//        // When - Execute with real-time monitoring enabled
//        Job job = fileImportJobConfig.fileImportJob();
//        JobParameters jobParams = new JobParametersBuilder()
//                .addString("inputFileName", monitoringFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("realTimeMonitoring", "true")
//                .addString("monitoringLevel", "REALTIME")
//                .addString("alertThreshold", "10")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution jobExecution = jobLauncher.run(job, jobParams);
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify monitoring data was captured
//        List<ImportedRecord> processedRecords = importedRecordRepository.findAll();
//        assertEquals(2, processedRecords.size());
//
//        // Verify real-time traces
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "REALTIME".equals(t.getMonitoringLevel()) ||
//            "realTimeMonitoring".equals(t.getBusinessKey())));
//
//        // Verify performance metrics
//        assertTrue(jobExecution.getStepExecutions().stream()
//                .anyMatch(step -> step.getMetrics() != null));
//    }
//
//    @Test
//    @Order(3)
//    void shouldCompleteFailureRecoveryCompleteFlow() throws Exception {
//        // Given - Simulate complete failure recovery flow
//        String batchDate = LocalDate.now().toString();
//        Path failureFile = tempDir.resolve("failure_recovery.csv");
//        String failureContent = "id,name,description\n" +
//                             "1,Valid Record 1,Valid Description 1\n" +
//                             "2,Invalid Record,Invalid Description\n" + // This will cause failure
//                             "3,Valid Record 3,Valid Description 3\n";
//        Files.writeString(failureFile, failureContent);
//
//        // Step 1: Initial processing with failure
//        Job failureJob = fileImportJobConfig.fileImportJob();
//        JobParameters failureParams = new JobParametersBuilder()
//                .addString("inputFileName", failureFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("failureRecoveryFlow", "complete")
//                .addString("initialAttempt", "true")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution failureExecution = jobLauncher.run(failureJob, failureParams);
//
//        // Then - Verify partial success
//        assertNotNull(failureExecution.getStatus());
//        List<ImportedRecord> partialRecords = importedRecordRepository.findAll();
//        assertEquals(2, partialRecords.size()); // Only valid records
//
//        // Step 2: Recovery process
//        Job recoveryJob = fileImportJobConfig.fileImportJob();
//        JobParameters recoveryParams = new JobParametersBuilder()
//                .addString("inputFileName", failureFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("failureRecoveryFlow", "complete")
//                .addString("recoveryAttempt", "true")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution recoveryExecution = jobLauncher.run(recoveryJob, recoveryParams);
//
//        // Then
//        assertNotNull(recoveryExecution.getStatus());
//
//        // Verify recovery was tracked
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "failureRecoveryFlow".equals(t.getBusinessKey()) &&
//            t.getEventType().contains("RECOVERY")));
//
//        // Verify final state
//        List<ImportedRecord> finalRecords = importedRecordRepository.findAll();
//        assertTrue(finalRecords.size() >= 2); // At least the valid records
//    }
//
//    @Test
//    @Order(4)
//    void shouldCompleteUserExperienceScenario() throws Exception {
//        // Given - Simulate complete user experience flow
//        String batchDate = LocalDate.now().toString();
//        Path userExperienceFile = tempDir.resolve("user_experience.csv");
//        String userContent = "id,name,description,user_priority\n" +
//                         "1,High Priority Record,High Priority Description,HIGH\n" +
//                         "2,Medium Priority Record,Medium Priority Description,MEDIUM\n" +
//                         "3,Low Priority Record,Low Priority Description,LOW\n";
//        Files.writeString(userExperienceFile, userContent);
//
//        // When - Execute with user experience tracking
//        Job job = fileImportJobConfig.fileImportJob();
//        JobParameters jobParams = new JobParametersBuilder()
//                .addString("inputFileName", userExperienceFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("userExperienceFlow", "true")
//                .addString("userId", "test-user-123")
//                .addString("sessionId", "session-456")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution jobExecution = jobLauncher.run(job, jobParams);
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify user experience metrics
//        List<ImportedRecord> processedRecords = importedRecordRepository.findAll();
//        assertEquals(3, processedRecords.size());
//
//        // Verify priority-based processing
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "test-user-123".equals(t.getUserId()) &&
//            "session-456".equals(t.getSessionId())));
//
//        // Verify processing order (high priority first)
//        assertTrue(processedRecords.stream()
//                .anyMatch(r -> r.getName() != null && r.getName().contains("HIGH")));
//    }
//
//    @Test
//    @Order(5)
//    void shouldCompleteConcurrentMixedScenario() throws Exception {
//        // Given - Simulate concurrent mixed operations
//        String batchDate = LocalDate.now().toString();
//        ExecutorService executor = Executors.newFixedThreadPool(3);
//        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();
//
//        // Create multiple concurrent operations
//        for (int i = 0; i < 3; i++) {
//            final int index = i;
//            final Path concurrentFile = tempDir.resolve("concurrent_mixed_" + index + ".csv");
//            String concurrentContent = "id,name,description\n" +
//                                 "1,Concurrent " + index + " 1,Concurrent Description " + index + " 1\n" +
//                                 "2,Concurrent " + index + " 2,Concurrent Description " + index + " 2\n";
//            Files.writeString(concurrentFile, concurrentContent);
//
//            CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
//                try {
//                    Job job = fileImportJobConfig.fileImportJob();
//                    JobParameters jobParams = new JobParametersBuilder()
//                            .addString("inputFileName", concurrentFile.toString())
//                            .addString("batchDate", batchDate)
//                            .addString("concurrentMixedFlow", "true")
//                            .addString("threadId", String.valueOf(index))
//                            .addLong("run.id", System.currentTimeMillis() + index)
//                            .toJobParameters();
//                    return jobLauncher.run(job, jobParams);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }, executor);
//
//            futures.add(future);
//        }
//
//        // When - Wait for all concurrent operations
//        List<JobExecution> executions = futures.stream()
//                .map(future -> {
//                    try {
//                        return future.get(120, TimeUnit.SECONDS);
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                })
//                .toList();
//
//        // Then
//        assertEquals(3, executions.size());
//
//        // Verify concurrent processing
//        long successCount = executions.stream()
//                .mapToLong(exec -> COMPLETED.equals(exec.getStatus()) ? 1 : 0)
//                .sum();
//        assertTrue(successCount >= 2); // At least 2 should succeed
//
//        // Verify data integrity under concurrency
//        List<ImportedRecord> allRecords = importedRecordRepository.findAll();
//        assertTrue(allRecords.size() >= 4); // At least 2 records per successful thread
//
//        // Verify concurrent operation tracking
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "concurrentMixedFlow".equals(t.getBusinessKey()) &&
//            t.getJobName() != null && t.getJobName().contains("concurrent")));
//
//        executor.shutdown();
//    }
//
//    @Test
//    @Order(6)
//    void shouldCompleteEndToEndPerformanceScenario() throws Exception {
//        // Given - Simulate end-to-end performance testing
//        String batchDate = LocalDate.now().toString();
//        Path performanceFile = tempDir.resolve("e2e_performance.csv");
//
//        // Create larger dataset for performance testing
//        StringBuilder performanceContent = new StringBuilder("id,name,description\n");
//        for (int i = 1; i <= 1000; i++) {
//            performanceContent.append(i).append(",Performance Test ").append(i)
//                          .append(",Performance Description ").append(i).append("\n");
//        }
//        Files.writeString(performanceFile, performanceContent.toString());
//
//        long startTime = System.currentTimeMillis();
//
//        // When - Execute with performance monitoring
//        Job job = fileImportJobConfig.fileImportJob();
//        JobParameters jobParams = new JobParametersBuilder()
//                .addString("inputFileName", performanceFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("e2ePerformanceFlow", "true")
//                .addString("performanceTest", "end-to-end")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution jobExecution = jobLauncher.run(job, jobParams);
//
//        long endTime = System.currentTimeMillis();
//        long duration = endTime - startTime;
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify performance metrics
//        List<ImportedRecord> processedRecords = importedRecordRepository.findAll();
//        assertEquals(1000, processedRecords.size());
//
//        // Performance assertions
//        assertTrue(duration < 60000, "E2E performance test should complete within 60 seconds");
//
//        // Verify performance tracking
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "e2ePerformanceFlow".equals(t.getBusinessKey()) &&
//            t.getMessage() != null && t.getMessage().contains("end-to-end")));
//
//        // Verify throughput
//        double throughput = 1000.0 / (duration / 1000.0); // records per second
//        assertTrue(throughput > 10, "Throughput should be at least 10 records/second");
//    }
//
//    @Test
//    @Order(7)
//    void shouldCompleteDataPipelineScenario() throws Exception {
//        // Given - Simulate complete data pipeline: Import → Transform → Export → Distribute
//        String batchDate = LocalDate.now().toString();
//        Path pipelineFile = tempDir.resolve("data_pipeline.csv");
//        String pipelineContent = "id,name,description,data_type\n" +
//                             "1,Pipeline Data 1,Pipeline Description 1,TRANSACTION\n" +
//                             "2,Pipeline Data 2,Pipeline Description 2,REFERENCE\n" +
//                             "3,Pipeline Data 3,Pipeline Description 3,MASTER\n";
//        Files.writeString(pipelineFile, pipelineContent);
//
//        // Step 1: Import data
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job importJob = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters importParams = new JobParametersBuilder()
//                .addString("inputFileName", pipelineFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("dataPipelineFlow", "true")
//                .addString("pipelineStage", "IMPORT")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution importExecution = jobLauncher.run(importJob, importParams);
//
//        // Then - Verify import
//        assertEquals(COMPLETED, importExecution.getStatus());
//        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
//        assertEquals(3, importedRecords.size());
//
//        // Step 2: Transform and Export (simulated as export with transformation)
//        Path transformedFile = tempDir.resolve("transformed_pipeline.csv");
//        JobCompletionNotificationListener exportListener = mock(JobCompletionNotificationListener.class);
//        Step exportStep = mock(Step.class);
//        Job exportJob = dataExportJobConfig.dataExportJob(exportListener, exportStep);
//        JobParameters exportParams = new JobParametersBuilder()
//                .addString("export.sql", "SELECT id, name, description, batch_date FROM imported_records WHERE batch_date = '" + batchDate + "'")
//                .addString("output.file.name", transformedFile.toString())
//                .addString("dataPipelineFlow", "true")
//                .addString("pipelineStage", "EXPORT")
//                .addString("transformation", "UPPERCASE")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution exportExecution = jobLauncher.run(exportJob, exportParams);
//
//        // Then - Verify export
//        assertEquals(COMPLETED, exportExecution.getStatus());
//        assertTrue(Files.exists(transformedFile));
//
//        String exportContent = Files.readString(transformedFile);
//        assertTrue(exportContent.contains("PIPELINE DATA")); // Verify transformation was applied
//
//        // Step 3: Distribution (simulated as file copy to distribution directory)
//        Path distributedFile = distributionDir.resolve("distributed_pipeline.csv");
//        Files.copy(transformedFile, distributedFile);
//
//        // Verify complete pipeline
//        assertTrue(Files.exists(distributedFile));
//
//        // Verify pipeline tracking
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "dataPipelineFlow".equals(t.getBusinessKey()) &&
//            (t.getJobName() != null && (t.getJobName().contains("IMPORT") || t.getJobName().contains("EXPORT")))));
//    }
//}
