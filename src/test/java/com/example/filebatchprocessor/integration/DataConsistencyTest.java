//package com.example.filebatchprocessor.integration;
//
//import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
//import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
//import com.example.filebatchprocessor.model.ImportedRecord;
//import com.example.filebatchprocessor.repository.ImportedRecordRepository;
//import com.example.filebatchprocessor.repository.RecordTraceRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//import org.springframework.batch.core.BatchStatus;
//import org.springframework.batch.core.job.Job;
//import org.springframework.batch.core.job.JobExecution;
//import org.springframework.batch.core.job.parameters.JobParameters;
//import org.springframework.batch.core.job.parameters.JobParametersBuilder;
//import org.springframework.batch.core.launch.JobLauncher;
//import org.springframework.batch.core.step.Step;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.mock;
//import static org.springframework.batch.core.BatchStatus.*;
//
//@SpringBootTest
//@ActiveProfiles("test")
//class DataConsistencyTest {
//
//    @Autowired
//    @Qualifier("asyncJobLauncher")
//    private JobLauncher jobLauncher;
//
//    @Autowired
//    private FileImportJobConfig fileImportJobConfig;
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
//    @BeforeEach
//    void setUp() throws IOException {
//        // Clean up repositories
//        importedRecordRepository.deleteAll();
//        recordTraceRepository.deleteAll();
//    }
//
//    @Test
//    void shouldMaintainDataConsistencyUnderConcurrentLoad() throws Exception {
//        // Given - Create test data with unique IDs
//        Path testFile1 = createTestFile("concurrent_1.csv", 1000, 1);
//        Path testFile2 = createTestFile("concurrent_2.csv", 1000, 1001);
//
//        ExecutorService executor = Executors.newFixedThreadPool(2);
//        AtomicInteger successCount = new AtomicInteger(0);
//        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();
//
//        // When - Execute two concurrent jobs with overlapping ID ranges
//        for (int i = 0; i < 2; i++) {
//            final int index = i;
//            final Path testFile = index == 0 ? testFile1 : testFile2;
//
//            CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
//                try {
//                    JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
//                    JobParameters jobParameters = new JobParametersBuilder()
//                            .addString("input.file.name", testFile.toString())
//                            .addString("batchDate", "2026-03-06")
//                            .addString("nodeId", "node-" + index)
//                            .addLong("run.id", System.nanoTime() + index)
//                            .toJobParameters();
//                    return jobLauncher.run(job, jobParameters);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }, executor);
//
//            futures.add(future);
//        }
//
//        // Then - Wait for both jobs to complete
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
//        // Verify both jobs completed
//        assertEquals(2, executions.size());
//        assertTrue(executions.stream().allMatch(exec -> COMPLETED.equals(exec.getStatus())));
//
//        // Verify data consistency - no duplicate IDs
//        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
//        assertEquals(2000, importedRecords.size());
//
//        // Check for ID uniqueness
//        List<Long> ids = importedRecords.stream()
//                .map(ImportedRecord::getId)
//                .sorted()
//                .toList();
//
//        // Verify no duplicates
//        for (int i = 1; i < ids.size(); i++) {
//            if (i > 1 && ids.get(i).equals(ids.get(i-1))) {
//                fail("Found duplicate ID: " + ids.get(i));
//            }
//        }
//
//        executor.shutdown();
//    }
//
//    @Test
//    void shouldHandleTransactionRollbackOnFailure() throws Exception {
//        // Given - Create a file that will cause processing failure
//        Path testFile = createTestFileWithInvalidData("rollback_test.csv", 1000, 1);
//
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters jobParameters = new JobParametersBuilder()
//                .addString("input.file.name", testFile.toString())
//                .addString("batchDate", "2026-03-06")
//                .addLong("run.id", System.nanoTime())
//                .toJobParameters();
//
//        // When
//        JobExecution jobExecution = jobLauncher.run(job, jobParameters);
//
//        // Then - Job should complete with some failures but maintain consistency
//        assertNotNull(jobExecution);
//
//        // Verify that failed records are not persisted
//        List<ImportedRecord> records = importedRecordRepository.findAll();
//
//        // Only valid records should be imported
//        long validRecords = records.stream()
//                .filter(r -> r.getName() != null && !r.getName().startsWith("INVALID"))
//                .count();
//
//        // Invalid records should not be in database
//        long invalidRecords = records.stream()
//                .filter(r -> r.getName() != null && r.getName().startsWith("INVALID"))
//                .count();
//
//        assertEquals(0, invalidRecords);
//        assertTrue(validRecords > 0);
//    }
//
//    @Test
//    void shouldEnsureIdempotencyOnRetry() throws Exception {
//        // Given - Create test file and run same job multiple times
//        Path testFile = createTestFile("idempotency_test.csv", 100, 1);
//
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters jobParameters = new JobParametersBuilder()
//                .addString("input.file.name", testFile.toString())
//                .addString("batchDate", "2026-03-06")
//                .addString("idempotencyKey", "unique-key-2026-03-06")
//                .addLong("run.id", System.nanoTime())
//                .toJobParameters();
//
//        // When - Run same job twice
//        JobExecution firstExecution = jobLauncher.run(job, jobParameters);
//        JobExecution secondExecution = jobLauncher.run(job, jobParameters);
//
//        // Then
//        assertEquals(COMPLETED, firstExecution.getStatus());
//        assertEquals(COMPLETED, secondExecution.getStatus());
//
//        // Verify idempotency - same number of records both times
//        List<ImportedRecord> recordsAfterFirst = importedRecordRepository.findAll();
//        long firstCount = recordsAfterFirst.size();
//
//        // Clear and run again to test idempotency
//        importedRecordRepository.deleteAll();
//        JobExecution thirdExecution = jobLauncher.run(job, jobParameters);
//
//        List<ImportedRecord> recordsAfterThird = importedRecordRepository.findAll();
//        long thirdCount = recordsAfterThird.size();
//
//        // Should have same number of records (idempotent behavior)
//        assertEquals(firstCount, thirdCount);
//    }
//
//    @Test
//    void shouldMaintainReferentialIntegrity() throws Exception {
//        // Given - Create test data with relationships
//        Path testFile = createTestFileWithRelationships("referential_test.csv", 500, 1);
//
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters jobParameters = new JobParametersBuilder()
//                .addString("input.file.name", testFile.toString())
//                .addString("batchDate", "2026-03-06")
//                .addLong("run.id", System.nanoTime())
//                .toJobParameters();
//
//        // When
//        JobExecution jobExecution = jobLauncher.run(job, jobParameters);
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify referential integrity
//        List<ImportedRecord> records = importedRecordRepository.findAll();
//        assertEquals(500, records.size());
//
//        // Check that all records follow expected pattern
//        for (ImportedRecord record : records) {
//            assertNotNull(record.getId());
//            assertNotNull(record.getName());
//            assertTrue(record.getName().startsWith("REL_"));
//            assertTrue(record.getId() >= 1 && record.getId() <= 500);
//        }
//    }
//
//    @Test
//    void shouldHandleConcurrentWriteConflicts() throws Exception {
//        // Given - Simulate concurrent write conflicts
//        ExecutorService executor = Executors.newFixedThreadPool(5);
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//        AtomicInteger conflictCount = new AtomicInteger(0);
//
//        // When - Multiple threads trying to write same records
//        for (int i = 0; i < 5; i++) {
//            final int threadId = i;
//
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                try {
//                    Path testFile = createTestFile("conflict_" + threadId + ".csv", 100, threadId * 200);
//                    JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
//                    JobParameters jobParameters = new JobParametersBuilder()
//                            .addString("input.file.name", testFile.toString())
//                            .addString("batchDate", "2026-03-06")
//                            .addString("threadId", String.valueOf(threadId))
//                            .addLong("run.id", System.nanoTime() + threadId)
//                            .toJobParameters();
//
//                    JobExecution jobExecution = jobLauncher.run(job, jobParameters);
//
//                    if (!COMPLETED.equals(jobExecution.getStatus())) {
//                        conflictCount.incrementAndGet();
//                    }
//                } catch (Exception e) {
//                    conflictCount.incrementAndGet();
//                }
//            }, executor);
//
//            futures.add(future);
//        }
//
//        // Then - Wait for all threads to complete
//        futures.forEach(future -> {
//            try {
//                future.get(180, TimeUnit.SECONDS);
//            } catch (Exception e) {
//                // Ignore for this test
//            }
//        });
//
//        // Verify data consistency despite conflicts
//        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
//
//        // Should have records from successful threads
//        assertTrue(importedRecords.size() > 0);
//
//        // Verify no duplicate primary keys
//        List<Long> ids = importedRecords.stream()
//                .map(ImportedRecord::getId)
//                .sorted()
//                .toList();
//
//        for (int i = 1; i < ids.size(); i++) {
//            if (i > 0 && ids.get(i).equals(ids.get(i-1))) {
//                fail("Found duplicate ID from concurrent writes: " + ids.get(i));
//            }
//        }
//
//        executor.shutdown();
//    }
//
//    @Test
//    void shouldValidateDataIntegrityAfterFailure() throws Exception {
//        // Given - Create file with some invalid records
//        Path testFile = createTestFileWithMixedData("integrity_test.csv", 1000, 1);
//
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters jobParameters = new JobParametersBuilder()
//                .addString("input.file.name", testFile.toString())
//                .addString("batchDate", "2026-03-06")
//                .addLong("run.id", System.nanoTime())
//                .toJobParameters();
//
//        // When
//        JobExecution jobExecution = jobLauncher.run(job, jobParameters);
//
//        // Then
//        assertNotNull(jobExecution);
//
//        // Verify only valid records are persisted
//        List<ImportedRecord> records = importedRecordRepository.findAll();
//
//        long validRecords = records.stream()
//                .filter(r -> r.getName() != null && !r.getName().contains("INVALID"))
//                .count();
//
//        long invalidRecords = records.stream()
//                .filter(r -> r.getName() != null && r.getName().contains("INVALID"))
//                .count();
//
//        // Should have only valid records
//        assertEquals(validRecords, records.size());
//        assertEquals(0, invalidRecords);
//
//        // Verify data integrity of valid records
//        for (ImportedRecord record : records) {
//            if (record.getName() != null && !record.getName().contains("INVALID")) {
//                assertNotNull(record.getId());
//                assertTrue(record.getId() >= 1 && record.getId() <= 1000);
//                assertTrue(record.getName().startsWith("VALID_"));
//            }
//        }
//    }
//
//    @Test
//    void shouldHandleLargeDatasetConsistency() throws Exception {
//        // Given - Process large dataset to test consistency
//        Path testFile = createTestFile("large_consistency_test.csv", 10000, 1);
//
//        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
//        Step importStep = mock(Step.class);
//        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
//        JobParameters jobParameters = new JobParametersBuilder()
//                .addString("input.file.name", testFile.toString())
//                .addString("batchDate", "2026-03-06")
//                .addLong("run.id", System.nanoTime())
//                .toJobParameters();
//
//        // When
//        JobExecution jobExecution = jobLauncher.run(job, jobParameters);
//
//        // Then
//        assertEquals(COMPLETED, jobExecution.getStatus());
//
//        // Verify all records are processed
//        List<ImportedRecord> records = importedRecordRepository.findAll();
//        assertEquals(10000, records.size());
//
//        // Verify no missing records in sequence
//        List<Long> ids = records.stream()
//                .map(ImportedRecord::getId)
//                .sorted()
//                .toList();
//
//        // Should have continuous sequence from 1 to 10000
//        for (int i = 0; i < ids.size(); i++) {
//            assertEquals((long) i + 1, ids.get(i));
//        }
//
//        // Verify no duplicates in large dataset
//        long uniqueIds = ids.stream().distinct().count();
//        assertEquals(10000, uniqueIds);
//    }
//
//    private Path createTestFile(String fileName, int recordCount, int startId) throws IOException {
//        Path testFile = tempDir.resolve(fileName);
//        StringBuilder content = new StringBuilder("id,name,description\n");
//
//        for (int i = 0; i < recordCount; i++) {
//            long id = startId + i;
//            content.append(id).append(",VALID_").append(id).append(",Valid Description ").append(id).append("\n");
//        }
//
//        Files.writeString(testFile, content.toString());
//        return testFile;
//    }
//
//    private Path createTestFileWithInvalidData(String fileName, int recordCount, int startId) throws IOException {
//        Path testFile = tempDir.resolve(fileName);
//        StringBuilder content = new StringBuilder("id,name,description\n");
//
//        for (int i = 0; i < recordCount; i++) {
//            long id = startId + i;
//            if (i % 10 == 0) {
//                // Every 10th record is invalid
//                content.append(id).append(",INVALID_").append(id).append(",Invalid Description\n");
//            } else {
//                content.append(id).append(",VALID_").append(id).append(",Valid Description ").append(id).append("\n");
//            }
//        }
//
//        Files.writeString(testFile, content.toString());
//        return testFile;
//    }
//
//    private Path createTestFileWithRelationships(String fileName, int recordCount, int startId) throws IOException {
//        Path testFile = tempDir.resolve(fileName);
//        StringBuilder content = new StringBuilder("id,name,description,related_id\n");
//
//        for (int i = 0; i < recordCount; i++) {
//            long id = startId + i;
//            long relatedId = (i % 2 == 0) ? id - 1 : id + 1; // Create relationships
//            content.append(id).append(",REL_").append(id).append(",Description ").append(id).append(",").append(relatedId).append("\n");
//        }
//
//        Files.writeString(testFile, content.toString());
//        return testFile;
//    }
//
//    private Path createTestFileWithMixedData(String fileName, int recordCount, int startId) throws IOException {
//        Path testFile = tempDir.resolve(fileName);
//        StringBuilder content = new StringBuilder("id,name,description\n");
//
//        for (int i = 0; i < recordCount; i++) {
//            long id = startId + i;
//            if (i % 5 == 0) {
//                // Every 5th record has null name (will be filtered)
//                content.append(id).append(",,Description ").append(id).append("\n");
//            } else {
//                content.append(id).append(",VALID_").append(id).append(",Valid Description ").append(id).append("\n");
//            }
//        }
//
//        Files.writeString(testFile, content.toString());
//        return testFile;
//    }
//}
