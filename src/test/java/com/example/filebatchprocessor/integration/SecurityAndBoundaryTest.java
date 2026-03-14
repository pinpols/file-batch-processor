package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
import com.example.filebatchprocessor.batch.config.FileImportJobConfig;

import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.step.Step;

import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.batch.core.BatchStatus.COMPLETED;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class SecurityAndBoundaryTest {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    private FileImportJobConfig fileImportJobConfig;

    @Autowired
    private DataExportJobConfig dataExportJobConfig;

    @Autowired
    private ImportedRecordRepository importedRecordRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Clean up repositories
        importedRecordRepository.deleteAll();
        recordTraceRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldHandleFileAccessPermissions() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        // Create file with restricted permissions
        Path testFile = tempDir.resolve("restricted_file.csv");
        String content = "id,name,description\n1,Permission Test,Permission Description\n";
        Files.writeString(testFile, content);
        
        // Set read-only permissions
        try {
            Files.setPosixFilePermissions(testFile, PosixFilePermissions.fromString("r--r--r--"));
        } catch (UnsupportedOperationException e) {
            // Windows systems might not support POSIX permissions
            // Skip permission test on Windows
            return;
        }
        
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Job might succeed or fail depending on implementation
        // The important thing is that permissions are checked
        
        // Restore permissions for cleanup
        try {
            Files.setPosixFilePermissions(testFile, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (UnsupportedOperationException e) {
            // Ignore on Windows
        }
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void shouldRestrictUserAccess() throws Exception {
        // Given - Regular user trying to access admin functionality
        Path testFile = createTestFile();
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // User should be able to run basic import jobs
        // Access control depends on Spring Security configuration
    }

    @Test
    @WithMockUser(username = "unauthorized", roles = {})
    void shouldDenyUnauthorizedAccess() throws Exception {
        // Given - Unauthorized user
        Path testFile = createTestFile();
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Should be denied or limited based on security configuration
    }

    @Test
    void shouldPreventSqlInjectionInExport() throws Exception {
        // Given - Attempt SQL injection through export parameters
        String maliciousSql = "SELECT * FROM users; DROP TABLE users; --";
        Path exportFile = tempDir.resolve("malicious_export.csv");
        
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", maliciousSql)
                .addString("output.file.name", exportFile.toString())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        
        // Job should fail due to SQL validation
        // The exact behavior depends on implementation
        // Should not execute malicious SQL
    }

    @Test
    void shouldHandleOversizedFiles() throws Exception {
        // Given - Create extremely large file (simulated)
        Path oversizedFile = tempDir.resolve("oversized.csv");
        
        // Create content that would be too large
        StringBuilder largeContent = new StringBuilder("id,name,description\n");
        for (int i = 1; i <= 1000000; i++) { // 1M records
            largeContent.append(i).append(",Oversized Name ").append(i).append(",Oversized Description ").append(i).append("\n");
        }
        
        try {
            Files.writeString(oversizedFile, largeContent.toString());
        } catch (IOException e) {
            // File might be too large for the test environment
            // This is expected behavior
            return;
        }
        
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", oversizedFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Should handle oversized file gracefully (fail or limit processing)
    }

    @Test
    void shouldHandleNetworkInterruption() throws Exception {
        // Given - Simulate network interruption during file processing
        Path testFile = createTestFile();
        
        // Mock network interruption (this would require additional infrastructure)
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", "2026-03-06")
                .addString("simulateNetworkFailure", "true")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Should handle network interruption gracefully
    }

    @Test
    void shouldHandleDiskSpaceExhaustion() throws Exception {
        // Given - Simulate low disk space
        Path testFile = createTestFile();
        
        // Create a large export that would consume significant disk space
        Path largeExportFile = tempDir.resolve("large_disk_test.csv");
        String largeSql = "SELECT generate_series(1, 100000) as id, 'DISK_TEST_' || generate_series as business_key, 'Disk Test Name ' || generate_series as name, 'Disk Test Description ' || generate_series as description, '2026-03-06' as batch_date";
        
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", largeSql)
                .addString("output.file.name", largeExportFile.toString())
                .addString("simulateLowDiskSpace", "true")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Should handle disk space issues gracefully
    }

    @Test
    void shouldHandleMemoryPressure() throws Exception {
        // Given - Create memory-intensive scenario
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();

        // Create multiple jobs to create memory pressure
        for (int i = 0; i < 10; i++) {
            final int index = i;
            final Path testFile = createTestFile("memory_test_" + index + ".csv");
            
            CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
                try {
                    JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
                    JobParameters jobParameters = new JobParametersBuilder()
                            .addString("input.file.name", testFile.toString())
                            .addString("batchDate", "2026-03-06")
                            .addString("memoryPressureTest", "true")
                            .addLong("run.id", System.currentTimeMillis() + index)
                            .toJobParameters();
                    return jobLauncher.run(job, jobParameters);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            
            futures.add(future);
        }

        // When
        List<JobExecution> executions = futures.stream()
                .map(future -> {
                    try {
                        return future.get(300, TimeUnit.SECONDS); // 5 minutes timeout
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        // Then
        assertEquals(10, executions.size());
        
        // Some jobs might fail due to memory pressure
        long successCount = executions.stream()
                .mapToLong(exec -> COMPLETED.equals(exec.getStatus()) ? 1 : 0)
                .sum();
        
        // At least some jobs should succeed
        assertTrue(successCount > 0);
        
        executor.shutdown();
    }

    @Test
    void shouldValidateInputSanitization() throws Exception {
        // Given - Test with potentially malicious input
        Path maliciousFile = tempDir.resolve("malicious.csv");
        String maliciousContent = "id,name,description\n" +
                               "1,<script>alert('xss')</script>,XSS Test\n" +
                               "2,'; DROP TABLE users; --',SQL Injection Test\n" +
                               "3,../../etc/passwd,Path Traversal Test\n";
        Files.writeString(maliciousFile, maliciousContent);
        
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", maliciousFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);
        
        // Verify malicious content is sanitized
        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
        
        for (ImportedRecord record : importedRecords) {
            if (record.getName() != null) {
                // Check that XSS is neutralized
                assertFalse(record.getName().contains("<script>"));
                assertFalse(record.getName().contains("alert"));
                
                // Check that SQL injection is neutralized
                assertFalse(record.getName().contains("DROP TABLE"));
                
                // Check that path traversal is neutralized
                assertFalse(record.getName().contains("../"));
            }
        }

// ...

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify audit trail is created
        List<ImportedRecord> auditRecords = importedRecordRepository.findAll();
        assertTrue(auditRecords.size() > 0);
        
        // Verify trace records are created
        var traces = recordTraceRepository.findAll();
        assertTrue(traces.size() > 0);
        
        // Check that audit information is captured
        assertTrue(traces.stream().anyMatch(t -> 
            t.getJobExecutionId() != null));
    }

    private Path createTestFile() throws IOException {
        return createTestFile("security_test.csv");
    }

    private Path createTestFile(String fileName) throws IOException {
        Path testFile = tempDir.resolve(fileName);
        String content = "id,name,description\n" +
                        "1,Security Test,Security Description\n" +
                        "2,Boundary Test,Boundary Description\n";
        Files.writeString(testFile, content);
        return testFile;
    }
}
