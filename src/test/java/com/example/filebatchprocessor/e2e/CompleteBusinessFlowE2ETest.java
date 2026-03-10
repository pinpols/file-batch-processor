//package com.example.filebatchprocessor.e2e;
//
//import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
//import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
//import com.example.filebatchprocessor.model.FileRecord;
//import com.example.filebatchprocessor.model.ImportedRecord;
//import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
//import com.example.filebatchprocessor.repository.ImportedRecordRepository;
//import com.example.filebatchprocessor.repository.RecordTraceRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.Order;
//import org.junit.jupiter.api.io.TempDir;
//import org.springframework.batch.core.BatchStatus;
//
//import org.springframework.batch.core.job.Job;
//import org.springframework.batch.core.job.JobExecution;
//import org.springframework.batch.core.job.parameters.JobParameters;
//import org.springframework.batch.core.job.parameters.JobParametersBuilder;
//import org.springframework.batch.core.launch.JobLauncher;
//
//
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
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.mock;
//import static org.springframework.batch.core.BatchStatus.*;
//
//@SpringBootTest
//@SpringBatchTest
//@ActiveProfiles("test")
//class CompleteBusinessFlowE2ETest {
//
//    @Autowired
//    private JobLauncher jobLauncher;
//
//    @Autowired
//    private JobCompletionNotificationListener jobCompletionNotificationListener;
//
//    @Autowired
//    private org.springframework.batch.core.step.Step importStep;
//
//    @Autowired
//    private org.springframework.batch.core.step.Step exportStep;
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
//    private Path inputFile;
//    private Path outputFile;
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
//        inputFile = tempDir.resolve("business_flow_input.csv");
//        String inputContent = "id,name,description,batch_date\n" +
//                           "1,Flow Test 1,Business Description 1," + LocalDate.now() + "\n" +
//                           "2,Flow Test 2,Business Description 2," + LocalDate.now() + "\n" +
//                           "3,Flow Test 3,Business Description 3," + LocalDate.now() + "\n";
//        Files.writeString(inputFile, inputContent);
//
//        outputFile = tempDir.resolve("business_flow_output.csv");
//    }
//
//    @Test
//    @Order(1)
//    void shouldCompleteFileReceiveToProcessToExportFlow() throws Exception {
//        // Given - Simulate complete business flow: File Receive → Process → Export
//        String batchDate = LocalDate.now().toString();
//
//        // Step 1: File Import
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job importJob = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters importParams = new JobParametersBuilder()
//                .addString("inputFileName", inputFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("businessFlow", "file-receive-process-export")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        // When
//        JobExecution importExecution = jobLauncher.run(importJob, importParams);
//
//        // Then
//        assertEquals(COMPLETED, importExecution.getStatus());
//        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
//        assertEquals(3, importedRecords.size());
//
//        // Step 2: Data Export
//        Job exportJob = dataExportJobConfig.dataExportJob(jobCompletionNotificationListener, null);
//        JobParameters exportParams = new JobParametersBuilder()
//                .addString("export.sql", "SELECT id, name, description, batch_date FROM imported_records WHERE batch_date = '" + batchDate + "'")
//                .addString("output.file.name", outputFile.toString())
//                .addString("businessFlow", "file-receive-process-export")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution exportExecution = jobLauncher.run(exportJob, exportParams);
//
//        // Then
//        assertEquals(COMPLETED, exportExecution.getStatus());
//        assertTrue(Files.exists(outputFile));
//
//        String exportContent = Files.readString(outputFile);
//        assertTrue(exportContent.contains("Flow Test"));
//
//        // Verify audit trail
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.size() >= 2); // Import + Export traces
//    }
//
//    @Test
//    @Order(2)
//    void shouldCompleteUserUploadToApprovalToExecutionFlow() throws Exception {
//        // Given - Simulate user upload flow: Upload → Review → Approve → Execute
//        String batchDate = LocalDate.now().toString();
//        Path uploadFile = tempDir.resolve("user_upload.csv");
//        String uploadContent = "id,name,description,status\n" +
//                            "1,User Upload 1,User Description 1,PENDING\n" +
//                            "2,User Upload 2,User Description 2,PENDING\n";
//        Files.writeString(uploadFile, uploadContent);
//
//        // Step 1: User Upload (simulated as import with pending status)
//        Job uploadJob = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, null);
//        JobParameters uploadParams = new JobParametersBuilder()
//                .addString("inputFileName", uploadFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("userFlow", "upload-approval-execution")
//                .addString("initialStatus", "PENDING")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution uploadExecution = jobLauncher.run(uploadJob, uploadParams);
//
//        // Then
//        assertEquals(COMPLETED, uploadExecution.getStatus());
//        List<ImportedRecord> uploadedRecords = importedRecordRepository.findAll();
//        assertEquals(2, uploadedRecords.size());
//
//        // Step 2: Approval Process (simulated - in real system this would be separate service)
//        // For this test, we'll simulate approval by updating status
//        uploadedRecords.forEach(record -> {
//            record.setName(record.getName().replace("PENDING", "APPROVED"));
//            importedRecordRepository.save(record);
//        });
//
//        // Step 3: Execution Process (simulated as separate job)
//        Job executionJob = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, null);
//        JobParameters executionParams = new JobParametersBuilder()
//                .addString("inputFileName", uploadFile.toString()) // Re-use same file
//                .addString("batchDate", batchDate)
//                .addString("userFlow", "upload-approval-execution")
//                .addString("executionPhase", "APPROVED")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution executionExecution = jobLauncher.run(executionJob, executionParams);
//
//        // Then
//        assertEquals(COMPLETED, executionExecution.getStatus());
//
//        // Verify final state
//        List<ImportedRecord> finalRecords = importedRecordRepository.findAll();
//        assertEquals(2, finalRecords.size());
//        assertTrue(finalRecords.stream().allMatch(r -> r.getName().contains("APPROVED")));
//    }
//
//    @Test
//    @Order(3)
//    void shouldCompleteErrorHandlingToNotificationToRecoveryFlow() throws Exception {
//        // Given - Simulate error handling flow: Error → Notification → Recovery → Retry
//        String batchDate = LocalDate.now().toString();
//        Path errorFile = tempDir.resolve("error_flow.csv");
//        String errorContent = "id,name,description\n" +
//                            "1,Valid Record 1,Valid Description 1\n" +
//                            "2,Invalid Record,Invalid Description\n" + // This will cause error
//                            "3,Valid Record 3,Valid Description 3\n";
//        Files.writeString(errorFile, errorContent);
//
//        // Step 1: Process with errors
//        Job errorJob = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, null);
//        JobParameters errorParams = new JobParametersBuilder()
//                .addString("inputFileName", errorFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("errorFlow", "error-notification-recovery")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution errorExecution = jobLauncher.run(errorJob, errorParams);
//
//        // Then
//        assertNotNull(errorExecution.getStatus());
//        // Job might complete with skips or failures
//
//        // Verify error records in DLQ
//        List<ImportedRecord> processedRecords = importedRecordRepository.findAll();
//        // Should have 2 valid records (1 and 3)
//        long validRecords = processedRecords.stream()
//                .filter(r -> r.getName() != null && !r.getName().equals("Invalid Record"))
//                .count();
//        assertEquals(2, validRecords);
//
//        // Step 2: Notification (simulated - in real system this would be notification service)
//        // For this test, we'll verify the error was handled appropriately
//
//        // Step 3: Recovery (simulated as DLQ replay)
//        Job recoveryJob = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, null);
//        JobParameters recoveryParams = new JobParametersBuilder()
//                .addString("inputFileName", errorFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("errorFlow", "error-notification-recovery")
//                .addString("recoveryPhase", "RECOVERY")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution recoveryExecution = jobLauncher.run(recoveryJob, recoveryParams);
//
//        // Then
//        assertNotNull(recoveryExecution.getStatus());
//
//        // Verify recovery was attempted
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t -> t.getEventType().contains("RECOVERY")));
//    }
//
//    @Test
//    @Order(4)
//    void shouldCompleteMultiTenantDataFlow() throws Exception {
//        // Given - Simulate multi-tenant data flow
//        String tenant1 = "tenant-a";
//        String tenant2 = "tenant-b";
//        String batchDate = LocalDate.now().toString();
//
//        Path tenant1File = tempDir.resolve("tenant1_data.csv");
//        String tenant1Content = "id,name,description,tenant_id\n" +
//                             "1,Tenant1 Data 1,Tenant1 Description 1," + tenant1 + "\n" +
//                             "2,Tenant1 Data 2,Tenant1 Description 2," + tenant1 + "\n";
//        Files.writeString(tenant1File, tenant1Content);
//
//        Path tenant2File = tempDir.resolve("tenant2_data.csv");
//        String tenant2Content = "id,name,description,tenant_id\n" +
//                             "3,Tenant2 Data 1,Tenant2 Description 1," + tenant2 + "\n" +
//                             "4,Tenant2 Data 2,Tenant2 Description 2," + tenant2 + "\n";
//        Files.writeString(tenant2File, tenant2Content);
//
//        // When - Process both tenant data
//        Job tenant1Job = fileImportJobConfig.fileImportJob();
//        JobParameters tenant1Params = new JobParametersBuilder()
//                .addString("inputFileName", tenant1File.toString())
//                .addString("batchDate", batchDate)
//                .addString("tenantId", tenant1)
//                .addString("multiTenantFlow", "true")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        Job tenant2Job = fileImportJobConfig.fileImportJob();
//        JobParameters tenant2Params = new JobParametersBuilder()
//                .addString("inputFileName", tenant2File.toString())
//                .addString("batchDate", batchDate)
//                .addString("tenantId", tenant2)
//                .addString("multiTenantFlow", "true")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        // Execute tenant jobs
//        JobExecution tenant1Execution = jobLauncher.run(tenant1Job, tenant1Params);
//        JobExecution tenant2Execution = jobLauncher.run(tenant2Job, tenant2Params);
//
//        // Then
//        assertEquals(COMPLETED, tenant1Execution.getStatus());
//        assertEquals(COMPLETED, tenant2Execution.getStatus());
//
//        // Verify tenant isolation
//        List<FileRecord> allRecords = importedRecordRepository.findAll();
//        assertEquals(4, allRecords.size());
//
//        // Verify tenant data is properly segregated
//        List<FileRecord> tenant1Records = allRecords.stream()
//                .filter(r -> "tenant1".equals(r.getTenantId()))
//                .toList();
//        List<FileRecord> tenant2Records = allRecords.stream()
//                .filter(r -> "tenant2".equals(r.getTenantId()))
//                .toList();
//
//        assertEquals(2, tenant1Records.size());
//        assertEquals(2, tenant2Records.size());
//
//        // Verify no cross-tenant data leakage
//        assertTrue(tenant1Records.stream().allMatch(r -> r.getTenantId().equals("tenant1")));
//        assertTrue(tenant2Records.stream().allMatch(r -> r.getTenantId().equals("tenant2")));
//    }
//
//    @Test
//    @Order(5)
//    void shouldCompleteCrossSystemDataFlow() throws Exception {
//        // Given - Simulate cross-system data flow: System A → Export → System B Import
//        String batchDate = LocalDate.now().toString();
//
//        // Step 1: Export from System A
//        Path exportFile = tempDir.resolve("cross_system_export.csv");
//        Job exportJob = dataExportJobConfig.dataExportJob();
//        JobParameters exportParams = new JobParametersBuilder()
//                .addString("export.sql", "SELECT id, name, description, batch_date FROM source_system_data WHERE created_date >= '" + batchDate + "'")
//                .addString("output.file.name", exportFile.toString())
//                .addString("crossSystemFlow", "system-a-to-system-b")
//                .addString("sourceSystem", "A")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution exportExecution = jobLauncher.run(exportJob, exportParams);
//
//        // Then
//        assertEquals(COMPLETED, exportExecution.getStatus());
//        assertTrue(Files.exists(exportFile));
//
//        // Wait a moment to simulate file transfer
//        TimeUnit.MILLISECONDS.sleep(100);
//
//        // Step 2: Import to System B
//        Path importFile = exportFile; // Use exported file as input
//        Job importJob = fileImportJobConfig.fileImportJob();
//        JobParameters importParams = new JobParametersBuilder()
//                .addString("inputFileName", importFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("crossSystemFlow", "system-a-to-system-b")
//                .addString("targetSystem", "B")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution importExecution = jobLauncher.run(importJob, importParams);
//
//        // Then
//        assertEquals(BatchStatus.COMPLETED, importExecution.getStatus());
//
//        // Verify imported records
//        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
//        assertTrue(importedRecords.size() > 0);
//
//        // Verify cross-system metadata
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "system-a-to-system-b".equals(t.getBusinessKey()) ||
//            t.getJobParameters().contains("crossSystemFlow")));
//    }
//
//    @Test
//    @Order(6)
//    void shouldCompleteRealTimeMonitoringFlow() throws Exception {
//        // Given - Simulate real-time monitoring during business flow
//        String batchDate = LocalDate.now().toString();
//        Path monitoringFile = tempDir.resolve("monitored_flow.csv");
//        String monitoringContent = "id,name,description\n" +
//                               "1,Monitor Test 1,Monitoring Description 1\n" +
//                               "2,Monitor Test 2,Monitoring Description 2\n";
//        Files.writeString(monitoringFile, monitoringContent);
//
//        // When - Execute with monitoring enabled
//        Job job = fileImportJobConfig.fileImportJob();
//        JobParameters jobParams = new JobParametersBuilder()
//                .addString("inputFileName", monitoringFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("realTimeMonitoring", "true")
//                .addString("monitoringLevel", "DETAILED")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution jobExecution = jobLauncher.run(job, jobParams);
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify monitoring data was captured
//        List<FileRecord> processedRecords = importedRecordRepository.findAll();
//        assertEquals(2, processedRecords.size());
//
//        // Verify detailed traces were created
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "DETAILED".equals(t.getMonitoringLevel()) ||
//            "realTimeMonitoring".equals(t.getBusinessKey())));
//
//        // Verify performance metrics were recorded
//        assertTrue(jobExecution.getStepExecutions().stream()
//                .anyMatch(step -> step.getMetrics() != null));
//    }
//}
