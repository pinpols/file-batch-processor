package com.example.filebatchprocessor.unit.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.config.DataExportJobConfig;
import com.example.filebatchprocessor.batch.processor.ExportRecordProcessor;
import com.example.filebatchprocessor.batch.writer.ExportRecordTraceWriter;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.ExportRecord;
import com.example.filebatchprocessor.params.ExportJobParams;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;

@ExtendWith(MockitoExtension.class)
class DataExportJobConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private RecordTraceRepository recordTraceRepository;

    @Mock
    private JobCompletionNotificationListener jobCompletionNotificationListener;

    private DataExportJobConfig dataExportJobConfig;

    @BeforeEach
    void setUp() {
        dataExportJobConfig = new DataExportJobConfig(
                jobRepository, mock(org.springframework.transaction.PlatformTransactionManager.class), dataSource);
    }

    @Test
    void shouldCreateExportReader() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("export.sql", "SELECT id, business_key, name, description, batch_date FROM imported_records");
        jobParameters.put("output.file.name", "test.csv");

        // When
        ItemReader<ExportRecord> reader = dataExportJobConfig.exportReader(jobParameters, 500);

        // Then
        assertNotNull(reader);
    }

    @Test
    void shouldCreateExportReaderWithDefaultSql() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("output.file.name", "test.csv");
        // No export.sql parameter

        // When
        ItemReader<ExportRecord> reader = dataExportJobConfig.exportReader(jobParameters, 500);

        // Then
        assertNotNull(reader);
        // Should use default SQL
    }

    @Test
    void shouldRejectInvalidSql() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("export.sql", "DROP TABLE test_table;"); // Invalid SQL
        jobParameters.put("output.file.name", "test.csv");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            dataExportJobConfig.exportReader(jobParameters, 500);
        });
    }

    @Test
    void shouldRejectSelectFromTableOutsideAllowList() {
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("export.sql", "SELECT id, username FROM app_users");
        jobParameters.put("output.file.name", "test.csv");

        assertThrows(IllegalArgumentException.class, () -> dataExportJobConfig.exportReader(jobParameters, 500));
    }

    @Test
    void shouldAllowCteAliasWhenUnderlyingTablesAreAllowListed() {
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put(
                "export.sql",
                "WITH recent AS (SELECT id, business_key, name, description, batch_date FROM imported_records) "
                        + "SELECT id, business_key, name, description, batch_date FROM recent");
        jobParameters.put("output.file.name", "test.csv");

        assertNotNull(dataExportJobConfig.exportReader(jobParameters, 500));
    }

    @Test
    void shouldRejectCteWhenUnderlyingTableIsNotAllowListed() {
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put(
                "export.sql",
                "WITH recent AS (SELECT id, username FROM app_users) "
                        + "SELECT id, username, username AS name, username AS description, username AS batch_date FROM recent");
        jobParameters.put("output.file.name", "test.csv");

        assertThrows(IllegalArgumentException.class, () -> dataExportJobConfig.exportReader(jobParameters, 500));
    }

    @Test
    void shouldCreateExportWriter() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("output.file.name", "test.csv");

        // When
        ItemWriter<ExportRecord> writer = dataExportJobConfig.exportWriter(jobParameters);

        // Then
        assertNotNull(writer);
    }

    @Test
    void shouldCreateExportWriterWithDefaultFileName() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        // No output.file.name parameter

        // When
        ItemWriter<ExportRecord> writer = dataExportJobConfig.exportWriter(jobParameters);

        // Then
        assertNotNull(writer);
        // Should use default file name
    }

    @Test
    void shouldCreateExportTraceWriter() {
        // Given
        FlatFileItemWriter<ExportRecord> delegateWriter = mock(FlatFileItemWriter.class);

        // When
        ExportRecordTraceWriter traceWriter =
                dataExportJobConfig.exportTraceWriter(delegateWriter, recordTraceRepository);

        // Then
        assertNotNull(traceWriter);
    }

    @Test
    void shouldCreateExportStep() {
        // Given
        JdbcCursorItemReader<ExportRecord> reader = mock(JdbcCursorItemReader.class);
        ExportRecordTraceWriter writer = mock(ExportRecordTraceWriter.class);
        ExportRecordProcessor processor = mock(ExportRecordProcessor.class);

        // When
        Step step = dataExportJobConfig.exportStep(reader, processor, writer);

        // Then
        assertNotNull(step);
        assertEquals("exportStep", step.getName());
    }

    @Test
    void shouldCreateDataExportJob() {
        // Given
        Step exportStep = mock(Step.class);

        // When
        Job job = dataExportJobConfig.dataExportJob(jobCompletionNotificationListener, exportStep);

        // Then
        assertNotNull(job);
        assertEquals("dataExportJob", job.getName());
    }

    @Test
    void shouldValidateExportJobParams() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("export.sql", "SELECT id, name FROM test_table");
        jobParameters.put("output.file.name", "output.csv");

        // When
        ExportJobParams params = ExportJobParams.from(jobParameters);

        // Then
        assertNotNull(params);
        assertEquals("SELECT id, name FROM test_table", params.getExportSql());
        assertEquals("output.csv", params.getOutputFileName());
    }

    @Test
    void shouldHandleEmptyExportJobParams() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();

        // When
        ExportJobParams params = ExportJobParams.from(jobParameters);

        // Then
        assertNotNull(params);
        assertNull(params.getExportSql());
        assertNull(params.getOutputFileName());
    }
}
