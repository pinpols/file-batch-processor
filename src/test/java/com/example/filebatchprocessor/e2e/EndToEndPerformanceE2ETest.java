//package com.example.filebatchprocessor.e2e;
//
//import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
//import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
//import com.example.filebatchprocessor.model.FileRecord;
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
//import org.springframework.batch.infrastructure.item.ItemReader;
//import org.springframework.batch.infrastructure.item.ItemWriter;
//import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
//import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
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
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.springframework.batch.core.BatchStatus.*;
//
//@SpringBootTest
//@SpringBatchTest
//@ActiveProfiles("test")
//class EndToEndPerformanceE2ETest {
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
//    private List<Path> testFiles;
//    private List<Path> outputFiles;
//
//    @BeforeEach
//    void setUp() throws IOException {
//        // Clean up repositories
//        importedRecordRepository.deleteAll();
//        recordTraceRepository.deleteAll();
//
//        testFiles = new ArrayList<>();
//        outputFiles = new ArrayList<>();
//
//        // Create multiple test files for E2E performance testing
//        for (int i = 0; i < 5; i++) {
//            Path testFile = tempDir.resolve("e2e_performance_" + i + ".csv");
//            StringBuilder content = new StringBuilder("id,name,description,performance_category\n");
//
//            // Create 2000 records per file
//            for (int j = 1; j <= 2000; j++) {
//                String category = switch ((j % 4)) {
//                    case 0 -> "HIGH";
//                    case 1 -> "MEDIUM";
//                    case 2 -> "LOW";
//                    default -> "BULK";
//                };
//                content.append(j).append(",Performance Test ").append(i).append("-").append(j)
//                          .append(",Performance Description ").append(i).append("-").append(j)
//                          .append(",").append(category).append("\n");
//            }
//            Files.writeString(testFile, content.toString());
//            testFiles.add(testFile);
//
//            Path outputFile = tempDir.resolve("e2e_output_" + i + ".csv");
//            outputFiles.add(outputFile);
//        }
//    }
//
//    @Test
//    @Order(1)
//    void shouldCompleteHighVolumeEndToEndFlow() throws Exception {
//        // Given - High volume E2E test: 10,000 records total
//        String batchDate = LocalDate.now().toString();
//        long startTime = System.currentTimeMillis();
//
//        // When - Process all files in sequence
//        List<JobExecution> executions = new ArrayList<>();
//
//        for (int i = 0; i < testFiles.size(); i++) {
//            Job job = fileImportJobConfig.fileImportJob();
//            JobParameters jobParams = new JobParametersBuilder()
//                    .addString("inputFileName", testFiles.get(i).toString())
//                    .addString("batchDate", batchDate)
//                    .addString("e2ePerformanceFlow", "high-volume")
//                    .addString("fileIndex", String.valueOf(i))
//                    .addLong("run.id", System.currentTimeMillis() + i)
//                    .toJobParameters();
//
//            JobExecution execution = jobLauncher.run(job, jobParams);
//            executions.add(execution);
//        }
//
//        long endTime = System.currentTimeMillis();
//        long totalDuration = endTime - startTime;
//
//        // Then
//        assertEquals(5, executions.size());
//        assertTrue(executions.stream().allMatch(exec -> COMPLETED.equals(exec.getStatus())));
//
//        // Verify all records were processed
//        List<FileRecord> allRecords = importedRecordRepository.findAll();
//        assertEquals(10000, allRecords.size()); // 2000 records × 5 files
//
//        // Performance assertions
//        assertTrue(totalDuration < 300000, "High volume E2E should complete within 5 minutes");
//
//        // Verify throughput
//        double throughput = 10000.0 / (totalDuration / 1000.0); // records per second
//        assertTrue(throughput > 50, "Throughput should be at least 50 records/second");
//
//        // Verify E2E tracking
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "high-volume".equals(t.getBusinessKey()) &&
//            "e2ePerformanceFlow".equals(t.getBusinessKey())));
//    }
//
//    @Test
//    @Order(2)
//    void shouldCompleteConcurrentEndToEndFlow() throws Exception {
//        // Given - Concurrent E2E test: Multiple jobs running simultaneously
//        String batchDate = LocalDate.now().toString();
//        ExecutorService executor = Executors.newFixedThreadPool(3);
//        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();
//
//        long startTime = System.currentTimeMillis();
//
//        // When - Run 3 jobs concurrently
//        for (int i = 0; i < 3; i++) {
//            final int index = i;
//            final Path concurrentFile = tempDir.resolve("concurrent_e2e_" + index + ".csv");
//            String concurrentContent = "id,name,description,concurrent_group\n" +
//                                 "1,Concurrent Test " + index + " 1,Concurrent Description " + index + " 1,GROUP_" + index + "\n" +
//                                 "2,Concurrent Test " + index + " 2,Concurrent Description " + index + " 2,GROUP_" + index + "\n";
//            Files.writeString(concurrentFile, concurrentContent);
//
//            CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
//                try {
//                    Job job = fileImportJobConfig.fileImportJob();
//                    JobParameters jobParams = new JobParametersBuilder()
//                            .addString("inputFileName", concurrentFile.toString())
//                            .addString("batchDate", batchDate)
//                            .addString("concurrentE2eFlow", "true")
//                            .addString("concurrentIndex", String.valueOf(index))
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
//        // Wait for all concurrent jobs
//        List<JobExecution> executions = futures.stream()
//                .map(future -> {
//                    try {
//                        return future.get(180, TimeUnit.SECONDS); // 3 minutes timeout
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                })
//                .toList();
//
//        long endTime = System.currentTimeMillis();
//        long totalDuration = endTime - startTime;
//
//        // Then
//        assertEquals(3, executions.size());
//
//        // Verify concurrent execution
//        long successCount = executions.stream()
//                .mapToLong(exec -> COMPLETED.equals(exec.getStatus()) ? 1 : 0)
//                .sum();
//        assertTrue(successCount >= 2); // At least 2 should succeed
//
//        // Performance assertions for concurrent execution
//        assertTrue(totalDuration < 240000, "Concurrent E2E should complete within 4 minutes");
//
//        // Verify concurrent tracking
//        List<FileRecord> allRecords = importedRecordRepository.findAll();
//        assertTrue(allRecords.size() >= 4); // At least 2 records per successful job
//
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "concurrentE2eFlow".equals(t.getBusinessKey()) &&
//            t.getJobParameters().contains("concurrentIndex")));
//
//        executor.shutdown();
//    }
//
//    @Test
//    @Order(3)
//    void shouldCompleteMixedLoadEndToEndFlow() throws Exception {
//        // Given - Mixed load E2E test: Different data sizes and complexities
//        String batchDate = LocalDate.now().toString();
//        long startTime = System.currentTimeMillis();
//
//        // Create files with different characteristics
//        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();
//
//        // Small file (500 records)
//        Path smallFile = tempDir.resolve("mixed_load_small.csv");
//        StringBuilder smallContent = new StringBuilder("id,name,description,load_type\n");
//        for (int i = 1; i <= 500; i++) {
//            smallContent.append(i).append(",Small Load Test ").append(i)
//                         .append(",Small Load Description ").append(i).append(",SMALL\n");
//        }
//        Files.writeString(smallFile, smallContent.toString());
//
//        // Medium file (1500 records)
//        Path mediumFile = tempDir.resolve("mixed_load_medium.csv");
//        StringBuilder mediumContent = new StringBuilder("id,name,description,load_type\n");
//        for (int i = 1; i <= 1500; i++) {
//            mediumContent.append(i).append(",Medium Load Test ").append(i)
//                          .append(",Medium Load Description ").append(i).append(",MEDIUM\n");
//        }
//        Files.writeString(mediumFile, mediumContent.toString());
//
//        // Large file (3000 records)
//        Path largeFile = tempDir.resolve("mixed_load_large.csv");
//        StringBuilder largeContent = new StringBuilder("id,name,description,load_type\n");
//        for (int i = 1; i <= 3000; i++) {
//            largeContent.append(i).append(",Large Load Test ").append(i)
//                         .append(",Large Load Description ").append(i).append(",LARGE\n");
//        }
//        Files.writeString(largeFile, largeContent.toString());
//
//        // When - Execute mixed load test
//        ExecutorService executor = Executors.newFixedThreadPool(3);
//
//        // Small file job
//        CompletableFuture<JobExecution> smallFuture = CompletableFuture.supplyAsync(() -> {
//            try {
//                Job job = fileImportJobConfig.fileImportJob();
//                JobParameters jobParams = new JobParametersBuilder()
//                        .addString("inputFileName", smallFile.toString())
//                        .addString("batchDate", batchDate)
//                        .addString("mixedLoadE2eFlow", "true")
//                        .addString("loadType", "SMALL")
//                        .addLong("run.id", System.currentTimeMillis())
//                        .toJobParameters();
//                return jobLauncher.run(job, jobParams);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        }, executor);
//
//        // Medium file job
//        CompletableFuture<JobExecution> mediumFuture = CompletableFuture.supplyAsync(() -> {
//            try {
//                Job job = fileImportJobConfig.fileImportJob();
//                JobParameters jobParams = new JobParametersBuilder()
//                        .addString("inputFileName", mediumFile.toString())
//                        .addString("batchDate", batchDate)
//                        .addString("mixedLoadE2eFlow", "true")
//                        .addString("loadType", "MEDIUM")
//                        .addLong("run.id", System.currentTimeMillis())
//                        .toJobParameters();
//                return jobLauncher.run(job, jobParams);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        }, executor);
//
//        // Large file job
//        CompletableFuture<JobExecution> largeFuture = CompletableFuture.supplyAsync(() -> {
//            try {
//                Job job = fileImportJobConfig.fileImportJob();
//                JobParameters jobParams = new JobParametersBuilder()
//                        .addString("inputFileName", largeFile.toString())
//                        .addString("batchDate", batchDate)
//                        .addString("mixedLoadE2eFlow", "true")
//                        .addString("loadType", "LARGE")
//                        .addLong("run.id", System.currentTimeMillis())
//                        .toJobParameters();
//                return jobLauncher.run(job, jobParams);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        }, executor);
//
//        // Wait for all jobs
//        List<JobExecution> executions = List.of(
//            smallFuture.get(300, TimeUnit.SECONDS),
//            mediumFuture.get(300, TimeUnit.SECONDS),
//            largeFuture.get(300, TimeUnit.SECONDS)
//        );
//
//        long endTime = System.currentTimeMillis();
//        long totalDuration = endTime - startTime;
//
//        // Then
//        assertEquals(3, executions.size());
//        assertTrue(executions.stream().allMatch(exec -> COMPLETED.equals(exec.getStatus())));
//
//        // Verify mixed load processing
//        List<FileRecord> allRecords = importedRecordRepository.findAll();
//        assertEquals(5000, allRecords.size()); // 500 + 1500 + 3000
//
//        // Performance assertions for mixed load
//        assertTrue(totalDuration < 420000, "Mixed load E2E should complete within 7 minutes");
//
//        // Verify different load types were handled
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "mixedLoadE2eFlow".equals(t.getBusinessKey()) &&
//            t.getJobParameters().contains("loadType")));
//
//        executor.shutdown();
//    }
//
//    @Test
//    @Order(4)
//    void shouldCompleteStressTestEndToEndFlow() throws Exception {
//        // Given - Stress test E2E: Maximum system load
//        String batchDate = LocalDate.now().toString();
//        long startTime = System.currentTimeMillis();
//
//        // Create stress test data
//        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();
//
//        // Create 5 concurrent jobs with maximum load
//        for (int i = 0; i < 5; i++) {
//            final int index = i;
//            final Path stressFile = tempDir.resolve("stress_test_" + index + ".csv");
//
//            // Create 1000 records per stress file
//            StringBuilder stressContent = new StringBuilder("id,name,description,stress_level\n");
//            for (int j = 1; j <= 1000; j++) {
//                String stressLevel = switch ((j % 5)) {
//                    case 0 -> "CRITICAL";
//                    case 1 -> "HIGH";
//                    case 2 -> "MEDIUM";
//                    case 3 -> "LOW";
//                    default -> "MINIMAL";
//                };
//                stressContent.append(j).append(",Stress Test ").append(index).append("-").append(j)
//                              .append(",Stress Description ").append(index).append("-").append(j)
//                              .append(",").append(stressLevel).append("\n");
//            }
//            Files.writeString(stressFile, stressContent.toString());
//
//            CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
//                try {
//                    Job job = fileImportJobConfig.fileImportJob();
//                    JobParameters jobParams = new JobParametersBuilder()
//                            .addString("inputFileName", stressFile.toString())
//                            .addString("batchDate", batchDate)
//                            .addString("stressTestE2eFlow", "true")
//                            .addString("stressLevel", "MAXIMUM")
//                            .addString("stressIndex", String.valueOf(index))
//                            .addLong("run.id", System.currentTimeMillis() + index)
//                            .toJobParameters();
//                    return jobLauncher.run(job, jobParams);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }, Executors.newCachedThreadPool());
//
//            futures.add(future);
//        }
//
//        // When - Execute stress test
//        List<JobExecution> executions = futures.stream()
//                .map(future -> {
//                    try {
//                        return future.get(600, TimeUnit.SECONDS); // 10 minutes timeout
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                })
//                .toList();
//
//        long endTime = System.currentTimeMillis();
//        long totalDuration = endTime - startTime;
//
//        // Then
//        assertEquals(5, executions.size());
//
//        // Verify stress test execution
//        long successCount = executions.stream()
//                .mapToLong(exec -> COMPLETED.equals(exec.getStatus()) ? 1 : 0)
//                .sum();
//        assertTrue(successCount >= 3); // At least 3 should succeed under stress
//
//        // Verify stress test data
//        List<FileRecord> allRecords = importedRecordRepository.findAll();
//        assertTrue(allRecords.size() >= 3000); // At least 3 files × 1000 records
//
//        // Performance assertions for stress test
//        assertTrue(totalDuration < 720000, "Stress test E2E should complete within 12 minutes");
//
//        // Verify stress test tracking
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "stressTestE2eFlow".equals(t.getBusinessKey()) &&
//            "MAXIMUM".equals(t.getStressLevel())));
//    }
//
//    @Test
//    @Order(5)
//    void shouldCompleteResourceUtilizationEndToEndFlow() throws Exception {
//        // Given - Resource utilization E2E test
//        String batchDate = LocalDate.now().toString();
//
//        // Monitor initial resource usage
//        Runtime runtime = Runtime.getRuntime();
//        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
//
//        // Create resource-intensive test
//        Path resourceFile = tempDir.resolve("resource_utilization.csv");
//        StringBuilder resourceContent = new StringBuilder("id,name,description,resource_intensity\n");
//
//        // Create 5000 records to stress resources
//        for (int i = 1; i <= 5000; i++) {
//            String intensity = switch ((i % 3)) {
//                case 0 -> "HIGH";
//                case 1 -> "MEDIUM";
//                default -> "LOW";
//            };
//            resourceContent.append(i).append(",Resource Test ").append(i)
//                         .append(",Resource Description ").append(i)
//                         .append(",").append(intensity).append("\n");
//        }
//        Files.writeString(resourceFile, resourceContent.toString());
//
//        long startTime = System.currentTimeMillis();
//
//        // When - Execute resource utilization test
//        Job job = fileImportJobConfig.fileImportJob();
//        JobParameters jobParams = new JobParametersBuilder()
//                .addString("inputFileName", resourceFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("resourceUtilizationE2eFlow", "true")
//                .addString("resourceMonitoring", "DETAILED")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution jobExecution = jobLauncher.run(job, jobParams);
//
//        long endTime = System.currentTimeMillis();
//        long duration = endTime - startTime;
//
//        // Monitor resource usage after test
//        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
//        long memoryUsed = memoryAfter - memoryBefore;
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify resource utilization
//        List<FileRecord> processedRecords = importedRecordRepository.findAll();
//        assertEquals(5000, processedRecords.size());
//
//        // Resource utilization assertions
//        assertTrue(duration < 180000, "Resource utilization test should complete within 3 minutes");
//        assertTrue(memoryUsed < 500 * 1024 * 1024, "Memory usage should be reasonable (< 500MB)");
//
//        // Verify resource monitoring
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "resourceUtilizationE2eFlow".equals(t.getBusinessKey()) &&
//            "DETAILED".equals(t.getResourceMonitoring())));
//    }
//
//    @Test
//    @Order(6)
//    void shouldCompleteEndToEndPerformanceMetricsValidation() throws Exception {
//        // Given - Comprehensive E2E performance metrics validation
//        String batchDate = LocalDate.now().toString();
//
//        // Create performance benchmark data
//        Path benchmarkFile = tempDir.resolve("performance_benchmark.csv");
//        StringBuilder benchmarkContent = new StringBuilder("id,name,description,benchmark_category,expected_time\n");
//
//        for (int i = 1; i <= 2000; i++) {
//            String category = switch ((i % 4)) {
//                case 0 -> "FAST";
//                case 1 -> "NORMAL";
//                case 2 -> "SLOW";
//                default -> "BULK";
//            };
//            int expectedTime = switch ((i % 4)) {
//                case 0 -> 10; // Fast: 10ms
//                case 1 -> 50; // Normal: 50ms
//                case 2 -> 100; // Slow: 100ms
//                default -> 200; // Bulk: 200ms
//            };
//            benchmarkContent.append(i).append(",Benchmark Test ").append(i)
//                          .append(",Benchmark Description ").append(i)
//                          .append(",").append(category).append(",").append(expectedTime).append("\n");
//        }
//        Files.writeString(benchmarkFile, benchmarkContent.toString());
//
//        long startTime = System.currentTimeMillis();
//
//        // When - Execute performance benchmark
//        Job job = fileImportJobConfig.fileImportJob();
//        JobParameters jobParams = new JobParametersBuilder()
//                .addString("inputFileName", benchmarkFile.toString())
//                .addString("batchDate", batchDate)
//                .addString("performanceBenchmarkE2eFlow", "true")
//                .addString("benchmarkType", "COMPREHENSIVE")
//                .addLong("run.id", System.currentTimeMillis())
//                .toJobParameters();
//
//        JobExecution jobExecution = jobLauncher.run(job, jobParams);
//
//        long endTime = System.currentTimeMillis();
//        long actualDuration = endTime - startTime;
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify benchmark execution
//        List<FileRecord> processedRecords = importedRecordRepository.findAll();
//        assertEquals(2000, processedRecords.size());
//
//        // Performance benchmark assertions
//        assertTrue(actualDuration < 120000, "Performance benchmark should complete within 2 minutes");
//
//        // Calculate actual vs expected performance
//        double actualTimePerRecord = (double) actualDuration / processedRecords.size();
//        assertTrue(actualTimePerRecord < 100, "Actual time per record should be less than 100ms");
//
//        // Verify performance benchmark tracking
//        var traces = recordTraceRepository.findAll();
//        assertTrue(traces.stream().anyMatch(t ->
//            "performanceBenchmarkE2eFlow".equals(t.getBusinessKey()) &&
//            "COMPREHENSIVE".equals(t.getBenchmarkType())));
//    }
//}
