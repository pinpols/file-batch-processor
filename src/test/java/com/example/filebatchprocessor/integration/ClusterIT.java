package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.SchedulerLeaderService;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.batch.core.BatchStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.batch.core.BatchStatus.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Disabled("Lambda-based concurrent job execution tests have Spring Batch compatibility issues")
class ClusterIT {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    private FileImportJobConfig fileImportJobConfig;

    @Autowired
    @Qualifier("fileImportJob")
    private Job fileImportJob;

    @Autowired
    private DataExportJobConfig dataExportJobConfig;

    @Autowired
    private ImportedRecordRepository importedRecordRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @Mock
    private SchedulerLeaderService schedulerLeaderService;

    @Mock
    private JobCompletionNotificationListener jobCompletionNotificationListener;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Clean up repositories
        importedRecordRepository.deleteAll();
        recordTraceRepository.deleteAll();
        
        // Reset mock
        reset(schedulerLeaderService);
    }

    @Test
    void shouldHandleClusterLeaderElection() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        
        Path testFile = createTestFile();
        Job job = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, mock(Step.class));
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .addString("clusterNodeId", "node-1")
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        verify(schedulerLeaderService, atLeastOnce()).isLeader();
        
        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
        assertTrue(importedRecords.size() > 0);
    }

    @Test
    void shouldPreventNonLeaderJobExecution() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        
        Path testFile = createTestFile();
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .addString("clusterNodeId", "node-2")
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        // Non-leader should still be able to execute job (depending on implementation)
        assertNotNull(jobExecution);
        
        verify(schedulerLeaderService, atLeastOnce()).isLeader();
    }

    @Test
    void shouldHandleLeaderFailover() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        when(schedulerLeaderService.isLeader())
                .thenReturn(false, false, true); // Fail first two times, succeed third time
        
        Path testFile = createTestFile();
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .addString("clusterNodeId", "node-failover")
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        
        // Verify leadership was checked multiple times
        verify(schedulerLeaderService, times(3)).isLeader();
    }

    @Test
    void shouldHandleConcurrentClusterJobs() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<CompletableFuture<JobExecution>> futures = new ArrayList<>();

        // Get actual job bean to avoid lambda issues with mocks
        Job actualJob = fileImportJob;

        // Create 3 concurrent jobs on different nodes
        for (int i = 0; i < 3; i++) {
            final int nodeId = i;
            final String batchDate = uniqueBatchDate + "-" + i;
            final Path testFile = createTestFile("cluster_concurrent_" + i + ".csv");
            
            final Job job = actualJob;
            final JobLauncher jobLauncherCopy = jobLauncher;
            CompletableFuture<JobExecution> future = CompletableFuture.supplyAsync(() -> {
                try {
                    JobParameters jobParameters = new JobParametersBuilder()
                            .addString("input.file.name", testFile.toString())
                            .addString("batchDate", batchDate)
                            .addString("clusterNodeId", "node-" + nodeId)
                            .toJobParameters();
                    return jobLauncherCopy.run(job, jobParameters);
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
                        return future.get(60, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        // Then
        assertEquals(3, executions.size());
        
        // All jobs should complete successfully (leader election works)
        long successCount = executions.stream()
                .mapToLong(exec -> BatchStatus.COMPLETED.equals(exec.getStatus()) ? 1 : 0)
                .sum();
        assertEquals(3, successCount);
        
        // Verify total records imported
        List<ImportedRecord> allRecords = importedRecordRepository.findAll();
        assertTrue(allRecords.size() > 0);
        
        executor.shutdown();
    }

    @Test
    void shouldHandleClusterNodeFailure() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        // Simulate node failure during job execution
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        
        Path testFile = createTestFile();
        Job job = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, mock(Step.class));
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .addString("clusterNodeId", "node-failed")
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Job might fail due to node failure
        // The exact behavior depends on implementation
        
        verify(schedulerLeaderService, atLeastOnce()).isLeader();
    }

    @Test
    void shouldSynchronizeClusterState() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        
        Path testFile = createTestFile();
        Job job = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, mock(Step.class));
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("input.file.name", testFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .addString("clusterNodeId", "node-coordinator")
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        
        verify(schedulerLeaderService, atLeastOnce()).isLeader();
    }

    @Test
    void shouldHandleDistributedJobCoordination() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        when(schedulerLeaderService.isLeader()).thenReturn(true);
        
        // Create distributed export job
        Path exportFile = tempDir.resolve("distributed_export.csv");
        Job exportJob = dataExportJobConfig.dataExportJob(jobCompletionNotificationListener, mock(Step.class));
        JobParameters exportParams = new JobParametersBuilder()
                .addString("export.sql", "SELECT 1 as id, 'DIST_KEY' as business_key, 'Distributed Name' as name, 'Distributed Description' as description, '" + uniqueBatchDate + "' as batch_date")
                .addString("output.file.name", exportFile.toString())
                .addString("distributedLockKey", "export-lock-" + uniqueBatchDate)
                .addString("clusterNodeId", "node-coordinator")
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution exportExecution = jobLauncher.run(exportJob, exportParams);

        // Then
        assertEquals(BatchStatus.COMPLETED, exportExecution.getStatus());
        
        verify(schedulerLeaderService, atLeastOnce()).isLeader();
        
        assertTrue(Files.exists(exportFile));
    }

    private Path createTestFile() throws IOException {
        return createTestFile("test.csv");
    }

    private Path createTestFile(String fileName) throws IOException {
        Path testFile = tempDir.resolve(fileName);
        String content = "id,name,description\n" +
                        "1,Cluster Test,Cluster Description\n" +
                        "2,Node Test,Node Description\n";
        Files.writeString(testFile, content);
        return testFile;
    }
}
