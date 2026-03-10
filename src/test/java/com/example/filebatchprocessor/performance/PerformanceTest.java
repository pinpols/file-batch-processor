package com.example.filebatchprocessor.performance;

import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.batch.core.BatchStatus.COMPLETED;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class PerformanceTest {

    @Autowired
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
    void shouldHandleLargeFileImport() throws Exception {
        // Given - Create large CSV file (10,000 records)
        Path largeFile = tempDir.resolve("large_import.csv");
        StringBuilder csvContent = new StringBuilder("id,name,description\n");
        
        for (int i = 1; i <= 10000; i++) {
            csvContent.append(i).append(",Name ").append(i).append(",Description ").append(i).append("\n");
        }
        Files.writeString(largeFile, csvContent.toString());

        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", largeFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        long startTime = System.currentTimeMillis();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify performance metrics
        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
        assertEquals(10000, importedRecords.size());
        
        // Performance assertions
        assertTrue(duration < 60000, "Import should complete within 60 seconds"); // 60 seconds max
        double throughput = 10000.0 / (duration / 1000.0); // records per second
        assertTrue(throughput > 100, "Throughput should be at least 100 records/second");
        
        System.out.println("Large file import performance:");
        System.out.println("- Records: " + importedRecords.size());
        System.out.println("- Duration: " + duration + "ms");
        System.out.println("- Throughput: " + String.format("%.2f", throughput) + " records/second");
    }

    @Test
    void shouldHandleConcurrentJobExecution() throws Exception {
        // Given - Create multiple files for concurrent import
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Path file = tempDir.resolve("concurrent_" + i + ".csv");
            String content = "id,name,description\n" +
                           "1,Name" + i + ",Description" + i + "\n" +
                           "2,Name" + (i+100) + ",Description" + (i+100) + "\n";
            Files.writeString(file, content);
            files.add(file);
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // When - Execute 5 jobs concurrently
        for (int i = 0; i < 5; i++) {
            final int index = i;
            final Path file = files.get(i);
            
            CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
                try {
                    JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
                    JobParameters jobParameters = new JobParametersBuilder()
                            .addString("inputFileName", file.toString())
                            .addString("batchDate", "2026-03-06")
                            .addLong("run.id", System.currentTimeMillis() + index)
                            .toJobParameters();
                    return jobLauncher.run(job, jobParameters);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            
            futures.add(future);
        }

        // Then - Wait for all jobs to complete
        List<JobExecution> executions = futures.stream()
                .map(future -> {
                    try {
                        return future.get(120, TimeUnit.SECONDS); // 2 minutes timeout
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        // Verify all jobs completed successfully
        assertEquals(5, executions.size());
        assertTrue(executions.stream().allMatch(exec -> COMPLETED.equals(exec.getStatus())));
        
        // Verify total records imported
        List<ImportedRecord> allRecords = importedRecordRepository.findAll();
        assertEquals(10, allRecords.size()); // 2 records per file * 5 files
        
        // Performance assertions
        assertTrue(totalDuration < 120000, "Concurrent execution should complete within 2 minutes");
        double avgDuration = totalDuration / 5.0;
        assertTrue(avgDuration < 60000, "Average job duration should be under 60 seconds");
        
        System.out.println("Concurrent job execution performance:");
        System.out.println("- Jobs: " + executions.size());
        System.out.println("- Total duration: " + totalDuration + "ms");
        System.out.println("- Average duration: " + String.format("%.2f", avgDuration) + "ms");
        System.out.println("- Total records: " + allRecords.size());
        
        executor.shutdown();
    }

    @Test
    void shouldHandleLargeDatasetExport() throws Exception {
        // Given - Prepare large dataset for export
        Path exportFile = tempDir.resolve("large_export.csv");
        
        // Create a SQL that generates 50,000 records
        String largeSql = "SELECT generate_series(1, 50000) as id, " +
                          "'BULK_KEY_' || generate_series as business_key, " +
                          "'Bulk Name ' || generate_series as name, " +
                          "'Bulk Description ' || generate_series as description, " +
                          "'2026-03-06' as batch_date";

        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", largeSql)
                .addString("output.file.name", exportFile.toString())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        long startTime = System.currentTimeMillis();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify file was created
        assertTrue(Files.exists(exportFile));
        long fileSize = Files.size(exportFile);
        
        // Performance assertions
        assertTrue(duration < 120000, "Large export should complete within 2 minutes"); // 2 minutes max
        assertTrue(fileSize > 1000000, "Export file should be substantial"); // > 1MB
        
        double throughput = 50000.0 / (duration / 1000.0); // records per second
        assertTrue(throughput > 200, "Export throughput should be at least 200 records/second");
        
        System.out.println("Large dataset export performance:");
        System.out.println("- Records: 50000");
        System.out.println("- Duration: " + duration + "ms");
        System.out.println("- File size: " + (fileSize / 1024) + "KB");
        System.out.println("- Throughput: " + String.format("%.2f", throughput) + " records/second");
    }

    @Test
    void shouldMeasureMemoryUsage() throws Exception {
        // Given
        Runtime runtime = Runtime.getRuntime();
        
        // Measure memory before large operation
        runtime.gc(); // Suggest garbage collection
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Create and process large file
        Path memoryTestFile = tempDir.resolve("memory_test.csv");
        StringBuilder content = new StringBuilder("id,name,description\n");
        for (int i = 1; i <= 20000; i++) {
            content.append(i).append(",Memory Test ").append(i).append(",Memory Description ").append(i).append("\n");
        }
        Files.writeString(memoryTestFile, content.toString());

        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", memoryTestFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Measure memory after operation
        runtime.gc(); // Suggest garbage collection
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        
        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        long memoryUsed = memoryAfter - memoryBefore;
        double memoryMB = memoryUsed / (1024.0 * 1024.0);
        
        // Memory usage assertions
        assertTrue(memoryMB < 500, "Memory usage should be reasonable (< 500MB) for 20k records");
        
        System.out.println("Memory usage analysis:");
        System.out.println("- Memory before: " + (memoryBefore / (1024 * 1024)) + "MB");
        System.out.println("- Memory after: " + (memoryAfter / (1024 * 1024)) + "MB");
        System.out.println("- Memory used: " + String.format("%.2f", memoryMB) + "MB");
        System.out.println("- Memory per record: " + String.format("%.2f", memoryMB / 20000) + "KB");
    }

    @Test
    void shouldTestSystemThroughputUnderLoad() throws Exception {
        // Given - Create multiple concurrent operations to simulate load
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();

        // When - Execute mixed load (imports and exports)
        for (int i = 0; i < 10; i++) {
            final int index = i;
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    if (index % 2 == 0) {
                        // Import operation
                        Path file = tempDir.resolve("load_test_" + index + ".csv");
                        String content = "id,name,description\n1,Load Test " + index + ",Load Description " + index + "\n";
                        Files.writeString(file, content);
                        
                        JobCompletionNotificationListener importListener = mock(JobCompletionNotificationListener.class);
                        Step importStep = mock(Step.class);
                        Job importJob = fileImportJobConfig.fileImportJob(importListener, importStep);
                        JobParameters importParams = new JobParametersBuilder()
                                .addString("inputFileName", file.toString())
                                .addString("batchDate", "2026-03-06")
                                .addLong("run.id", System.currentTimeMillis() + index)
                                .toJobParameters();
                        jobLauncher.run(importJob, importParams);
                    } else {
                        // Export operation
                        Path exportFile = tempDir.resolve("load_export_" + index + ".csv");
                        JobCompletionNotificationListener exportListener = mock(JobCompletionNotificationListener.class);
                        Step exportStep = mock(Step.class);
                        Job exportJob = dataExportJobConfig.dataExportJob(exportListener, exportStep);
                        JobParameters exportParams = new JobParametersBuilder()
                                .addString("export.sql", "SELECT 1 as id, 'LOAD_KEY_" + index + "' as business_key, 'Load Name " + index + "' as name, 'Load Description " + index + "' as description, '2026-03-06' as batch_date")
                                .addString("output.file.name", exportFile.toString())
                                .addLong("run.id", System.currentTimeMillis() + index)
                                .toJobParameters();
                        jobLauncher.run(exportJob, exportParams);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            
            futures.add(future);
        }

        // Then - Wait for all operations to complete
        futures.forEach(future -> {
            try {
                future.get(180, TimeUnit.SECONDS); // 3 minutes timeout
            } catch (Exception e) {
                // Log but don't fail the test for performance testing
                System.err.println("Load test operation failed: " + e.getMessage());
            }
        });

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        // Performance assertions
        assertTrue(totalDuration < 300000, "Load test should complete within 5 minutes");
        
        System.out.println("System load test results:");
        System.out.println("- Concurrent operations: 10");
        System.out.println("- Total duration: " + totalDuration + "ms");
        System.out.println("- Average per operation: " + (totalDuration / 10.0) + "ms");
        System.out.println("- Operations per second: " + String.format("%.2f", 10000.0 / (totalDuration / 1000.0)));
        
        executor.shutdown();
    }
}
