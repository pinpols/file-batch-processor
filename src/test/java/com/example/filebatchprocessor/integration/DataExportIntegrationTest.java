package com.example.filebatchprocessor.integration;


import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
import com.example.filebatchprocessor.model.RecordTrace;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.batch.core.BatchStatus.COMPLETED;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataExportIntegrationTest {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    private DataExportJobConfig dataExportJobConfig;

    @Autowired
    private RecordTraceRepository recordTraceRepository;

    @TempDir
    Path tempDir;

    private Path outputFile;

    @BeforeEach
    void setUp() throws IOException {
        // Clean up trace repository
        recordTraceRepository.deleteAll();

        // Create output file path
        outputFile = tempDir.resolve("test_export.csv");
    }

    @Test
    void shouldCompleteDataExportJob() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", "SELECT 1 as id, 'TEST_KEY_001' as business_key, 'Test Name' as name, 'Test Description' as description, '" + uniqueBatchDate + "' as batch_date")
                .addString("output.file.name", outputFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify output file was created
        assertTrue(Files.exists(outputFile));
        
        // Verify file content
        String content = Files.readString(outputFile);
        assertTrue(content.contains("id,business_key,name,description,batch_date"));
        assertTrue(content.contains("1,TEST_KEY_001,Test Name,Test Description,2026-03-06"));
        
        // Verify trace records
        List<RecordTrace> traces = recordTraceRepository.findAll();
        assertTrue(traces.size() > 0);
        assertTrue(traces.stream().anyMatch(t -> "EXPORT".equals(t.getEventType())));
    }

    @Test
    void shouldHandleCustomSqlExport() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        String customSql = "SELECT 100 as id, 'CUSTOM_KEY' as business_key, 'Custom Name' as name, 'Custom Description' as description, '" + uniqueBatchDate + "' as batch_date WHERE 1=1";
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", customSql)
                .addString("output.file.name", outputFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify output file contains custom data
        String content = Files.readString(outputFile);
        assertTrue(content.contains("100,CUSTOM_KEY,Custom Name,Custom Description," + uniqueBatchDate));
    }

    @Test
    void shouldHandleLargeDatasetExport() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        String largeSql = "SELECT generate_series(1, 1000) as id, 'BULK_KEY_' || generate_series as business_key, 'Bulk Name ' || generate_series as name, 'Bulk Description ' || generate_series as description, '" + uniqueBatchDate + "' as batch_date";
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", largeSql)
                .addString("output.file.name", outputFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify large file was created
        assertTrue(Files.exists(outputFile));
        long fileSize = Files.size(outputFile);
        assertTrue(fileSize > 50000); // Should be substantial file
        
        // Verify file has header + 1000 data rows
        String content = Files.readString(outputFile);
        String[] lines = content.split("\n");
        assertEquals(1001, lines.length); // Header + 1000 data rows
    }

    @Test
    void shouldHandleEmptyResultSet() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        String emptySql = "SELECT 1 as id, 'TEST_KEY' as business_key, 'Test Name' as name, 'Test Description' as description, '" + uniqueBatchDate + "' as batch_date WHERE 1=0"; // Empty result
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", emptySql)
                .addString("output.file.name", outputFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify output file was created with only header
        String content = Files.readString(outputFile);
        assertTrue(content.contains("id,business_key,name,description,batch_date"));
        
        // Should only have header line
        String[] lines = content.split("\n");
        assertEquals(1, lines.length);
    }

    @Test
    void shouldFailWithInvalidSql() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        String invalidSql = "DROP TABLE test_table;"; // Invalid SQL
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", invalidSql)
                .addString("output.file.name", outputFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(BatchStatus.FAILED, jobExecution.getStatus());
        assertNotNull(jobExecution.getAllFailureExceptions());
        assertTrue(jobExecution.getAllFailureExceptions().size() > 0);
        
        // Verify exception message
        String exceptionMessage = jobExecution.getAllFailureExceptions().get(0).getMessage();
        assertTrue(exceptionMessage.contains("Unsupported export.sql"));
    }

    @Test
    void shouldHandleDefaultOutputFileName() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        // No output file name specified
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", "SELECT 1 as id, 'TEST_KEY' as business_key, 'Test Name' as name, 'Test Description' as description, '" + uniqueBatchDate + "' as batch_date")
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify default file was created (export/output.csv)
        // Note: This would need to check the default location based on implementation
    }

    @Test
    void shouldHandleSqlWithSpecialCharacters() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        String sqlWithSpecialChars = "SELECT 1 as id, 'SPECIAL_KEY_@#$%' as business_key, 'Name with \"quotes\"' as name, 'Description with \\n newlines' as description, '" + uniqueBatchDate + "' as batch_date";
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", sqlWithSpecialChars)
                .addString("output.file.name", outputFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify special characters are properly escaped in CSV
        String content = Files.readString(outputFile);
        assertTrue(content.contains("\"SPECIAL_KEY_@#$%\"")); // Should be quoted in CSV
        assertTrue(content.contains("\"Name with \"\"\"quotes\"\"\"")); // Should be escaped
    }

    @Test
    void shouldRecordExportStatistics() throws Exception {
        // Given - use unique batchDate
        String uniqueBatchDate = "2026-03-14-" + UUID.randomUUID().toString().substring(0, 8);
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);
        Step exportStep = mock(Step.class);
        Job job = dataExportJobConfig.dataExportJob(listener, exportStep);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("export.sql", "SELECT 1 as id, 'STATS_TEST' as business_key, 'Stats Name' as name, 'Stats Description' as description, '" + uniqueBatchDate + "' as batch_date")
                .addString("output.file.name", outputFile.toString())
                .addString("batchDate", uniqueBatchDate)
                .toJobParameters();

        // When
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);

        // Then
        assertEquals(COMPLETED, jobExecution.getStatus());
        
        // Verify statistics are recorded
        assertTrue(jobExecution.getStepExecutions().stream()
                .anyMatch(step -> step.getReadCount() > 0));
        assertTrue(jobExecution.getStepExecutions().stream()
                .anyMatch(step -> step.getWriteCount() > 0));
        
        // Verify trace records contain statistics
        List<RecordTrace> traces = recordTraceRepository.findAll();
        assertTrue(traces.stream().anyMatch(t -> "STATS_TEST".equals(t.getBusinessKey())));
    }
}
