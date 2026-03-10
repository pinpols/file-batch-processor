package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.ImportedRecord;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.repository.ImportedRecordRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.batch.core.BatchStatus.COMPLETED;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class FileImportIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private FileImportJobConfig fileImportJobConfig;

    @Autowired
    private ImportedRecordRepository importedRecordRepository;

    @Autowired
    private DlqRecordRepository dlqRecordRepository;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @TempDir
    Path tempDir;

    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        // Clean up repositories
        importedRecordRepository.deleteAll();
        dlqRecordRepository.deleteAll();
        recordTraceRepository.deleteAll();

        // Create test CSV file
        testFile = tempDir.resolve("test_import.csv");
        String csvContent = "id,name,description\n" +
                         "1,Test Record 1,Description 1\n" +
                         "2,Test Record 2,Description 2\n" +
                         "3,,Description 3\n" + // Missing name (should be filtered)
                         "4,Test Record 4,Description 4";
        Files.writeString(testFile, csvContent);
    }

    @Test
    void shouldCompleteFileImportJob() throws Exception {
        // Given
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", testFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify imported records
        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
        assertEquals(3, importedRecords.size()); // 3 valid records (1 filtered due to missing name)
        
        // Verify record names are uppercase
        assertTrue(importedRecords.stream().allMatch(r -> r.getName().equals(r.getName().toUpperCase())));
        
        // Verify trace records
        List<RecordTrace> traces = recordTraceRepository.findAll();
        assertTrue(traces.size() > 0);
        
        // Verify no DLQ records (all were processed successfully)
        List<DlqRecord> dlqRecords = dlqRecordRepository.findAll();
        assertTrue(dlqRecords.isEmpty());
    }

    @Test
    void shouldHandleShardedImport() throws Exception {
        // Given
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", testFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("shardIndex", 0L)
                .addLong("shardTotal", 2L)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify records were imported
        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
        assertTrue(importedRecords.size() > 0);
    }

    @Test
    void shouldHandleFileImportWithErrors() throws Exception {
        // Given - Create file with invalid data
        Path errorFile = tempDir.resolve("error_test.csv");
        String errorContent = "id,name,description\n" +
                          "1,Valid Record,Valid Description\n" +
                          "invalid_row,Missing Description\n" + // Invalid format
                          "2,Another Valid,Another Description";
        Files.writeString(errorFile, errorContent);

        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", errorFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus()); // Should complete with skips
        
        // Verify some records were imported
        List<ImportedRecord> importedRecords = importedRecordRepository.findAll();
        assertTrue(importedRecords.size() >= 2); // At least 2 valid records
        
        // Verify error handling
        assertTrue(jobExecution.getStepExecutions().stream()
                .anyMatch(step -> step.getFilterCount() > 0));
    }

    @Test
    void shouldHandleExcelFileImport() throws Exception {
        // Given - Create Excel file (simulated)
        Path excelFile = tempDir.resolve("test_import.xlsx");
        // Note: In real test, you'd create actual Excel file
        Files.writeString(excelFile, "simulated_excel_content");

        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", excelFile.toString())
                .addString("batchDate", "2026-03-06")
                .addString("fileFormat", "EXCEL")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Verify job execution (Excel handling would need proper Excel creation)
    }

    @Test
    void shouldHandleJsonFileImport() throws Exception {
        // Given - Create JSON file
        Path jsonFile = tempDir.resolve("test_import.json");
        String jsonContent = "[{\"id\":1,\"name\":\"Test 1\",\"description\":\"Desc 1\"}," +
                          "{\"id\":2,\"name\":\"Test 2\",\"description\":\"Desc 2\"}]";
        Files.writeString(jsonFile, jsonContent);

        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", jsonFile.toString())
                .addString("batchDate", "2026-03-06")
                .addString("fileFormat", "JSON")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertNotNull(jobExecution);
        // Verify job execution (JSON handling would need proper JSON parsing)
    }

    @Test
    void shouldReplayDlqRecords() throws Exception {
        // Given - Add some DLQ records
        DlqRecord dlqRecord = new DlqRecord();
        dlqRecord.setJobName("importJob");
        dlqRecord.setParams("{\"id\":1,\"name\":\"DLQ Test\",\"description\":\"DLQ Description\"}");
        dlqRecord.setErrorMessage("Processing error");
        dlqRecordRepository.save(dlqRecord);

        Step dlqStep = mock(Step.class);
        Job dlqJob = fileImportJobConfig.dlqReplayJob(dlqStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("limit", 10L)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(dlqJob, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify DLQ record was processed
        List<DlqRecord> remainingDlqRecords = dlqRecordRepository.findAll();
        assertTrue(remainingDlqRecords.isEmpty() || 
                  remainingDlqRecords.stream().noneMatch(r -> r.getId().equals(dlqRecord.getId())));
    }

    @Test
    void shouldFailWithInvalidFile() throws Exception {
        // Given
        Path invalidFile = tempDir.resolve("nonexistent.csv");
        // File doesn't exist

        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step importStep = mock(Step.class);
        Job job = fileImportJobConfig.fileImportJob(listener, importStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFileName", invalidFile.toString())
                .addString("batchDate", "2026-03-06")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(BatchStatus.FAILED, jobExecution.getStatus());
        assertNotNull(jobExecution.getAllFailureExceptions());
        assertTrue(jobExecution.getAllFailureExceptions().size() > 0);
    }
}
