package com.example.filebatchprocessor.unit.batch.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.batch.config.FileImportJobConfig;
import com.example.filebatchprocessor.batch.listener.ParseErrorRateGateListener;
import com.example.filebatchprocessor.batch.listener.ShardContextListener;
import com.example.filebatchprocessor.batch.processor.FileImportRecordProcessor;
import com.example.filebatchprocessor.batch.reader.FileImportRecordReader;
import com.example.filebatchprocessor.batch.writer.FileImportRecordWriter;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.service.DlqCompensationService;
import com.example.filebatchprocessor.service.PartitionedImportService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;

@ExtendWith(MockitoExtension.class)
class FileImportJobConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PartitionedImportService partitionedImportService;

    @Mock
    private DlqRecordRepository dlqRecordRepository;

    @Mock
    private RecordTraceRepository recordTraceRepository;

    @Mock
    private JobCompletionNotificationListener jobCompletionNotificationListener;

    @Mock
    private ParseErrorRateGateListener parseErrorRateGateListener;

    @Mock
    private ShardContextListener shardContextListener;

    private FileImportJobConfig fileImportJobConfig;

    @BeforeEach
    void setUp() {
        fileImportJobConfig = new FileImportJobConfig(
                jobRepository, mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    @Test
    void shouldCreateImportReader() throws Exception {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("input.file.name", "test.csv");
        jobParameters.put("shardIndex", 0);
        jobParameters.put("shardTotal", 4);
        jobParameters.put("fileFormat", "CSV");
        jobParameters.put("fileDelimiter", ",");

        // When
        FileImportRecordReader reader = fileImportJobConfig.importReader(
                jobParameters,
                mock(com.example.filebatchprocessor.batch.reader.spi.RecordLineParserFactory.class),
                mock(com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory.class));

        // Then
        assertNotNull(reader);
        // Reader initialization test - actual step context not needed for basic instantiation test
    }

    @Test
    void shouldCreateImportProcessor() {
        // When & Then
        // Since importProcessor is not a bean method, we test the processor directly
        FileImportRecordProcessor processor = new FileImportRecordProcessor();
        assertNotNull(processor);
    }

    @Test
    void shouldCreateImportWriter() throws Exception {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("batchDate", "2026-03-06");

        // When
        FileImportRecordWriter writer = fileImportJobConfig.importWriter(
                jobParameters, partitionedImportService, dlqRecordRepository, recordTraceRepository);

        // Then
        assertNotNull(writer);
        // Writer initialization test - actual step context not needed for basic instantiation test
    }

    @Test
    void shouldCreateImportStep() {
        // Given
        FileImportRecordReader reader = mock(FileImportRecordReader.class);
        FileImportRecordProcessor processor = mock(FileImportRecordProcessor.class);
        FileImportRecordWriter writer = mock(FileImportRecordWriter.class);

        // When
        Step step = fileImportJobConfig.importStep(
                jobRepository,
                mock(org.springframework.transaction.PlatformTransactionManager.class),
                reader,
                processor,
                writer,
                jobCompletionNotificationListener,
                parseErrorRateGateListener,
                shardContextListener,
                3,
                100,
                200);

        // Then
        assertNotNull(step);
        assertEquals("importStep", step.getName());
    }

    @Test
    void shouldCreateFileImportJob() {
        // Given
        Step importStep = mock(Step.class);

        // When
        Job job = fileImportJobConfig.fileImportJob(jobCompletionNotificationListener, importStep);

        // Then
        assertNotNull(job);
        assertEquals("importJob", job.getName());
    }

    @Test
    void shouldCreateDlqReplayTasklet() {
        // Given
        DlqCompensationService dlqCompensationService = mock(DlqCompensationService.class);

        // When
        org.springframework.batch.core.step.tasklet.Tasklet tasklet =
                fileImportJobConfig.dlqReplayTasklet(dlqCompensationService, 50);

        // Then
        assertNotNull(tasklet);
    }

    @Test
    void shouldCreateDlqReplayStep() {
        // Given
        org.springframework.batch.core.step.tasklet.Tasklet tasklet =
                mock(org.springframework.batch.core.step.tasklet.Tasklet.class);

        // When
        Step step = fileImportJobConfig.dlqReplayStep(tasklet);

        // Then
        assertNotNull(step);
        assertEquals("dlqReplayStep", step.getName());
    }

    @Test
    void shouldCreateDlqReplayJob() {
        // Given
        Step dlqReplayStep = mock(Step.class);
        JobCompletionNotificationListener listener = mock(JobCompletionNotificationListener.class);

        // When
        Job job = fileImportJobConfig.dlqReplayJob(dlqReplayStep, listener);

        // Then
        assertNotNull(job);
        assertEquals("dlqReplayJob", job.getName());
    }

    @Test
    void shouldHandleJobParametersValidation() {
        // Given
        Map<String, Object> jobParameters = new HashMap<>();
        jobParameters.put("input.file.name", ""); // Empty file name

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            fileImportJobConfig.importReader(
                    jobParameters,
                    mock(com.example.filebatchprocessor.batch.reader.spi.RecordLineParserFactory.class),
                    mock(com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory.class));
        });
    }
}
