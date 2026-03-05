package com.example.filebatchprocessor.batch.listener;

import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.config.ImportParseErrorGateProperties;
import com.example.filebatchprocessor.repository.QualityGateResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParseErrorRateGateListenerTest {

    @Test
    void shouldNotFailWhenTotalBelowMinLines() {
        BatchMetrics batchMetrics = mock(BatchMetrics.class);
        QualityGateResultRepository qualityGateResultRepository = mock(QualityGateResultRepository.class);
        ImportParseErrorGateProperties properties = new ImportParseErrorGateProperties();
        properties.setMinLines(50L);
        properties.setMaxRate(0.2);
        ParseErrorRateGateListener listener = new ParseErrorRateGateListener(batchMetrics, properties, qualityGateResultRepository);

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        stepExecution.getExecutionContext().putLong("read.count", 10L);
        stepExecution.getExecutionContext().putLong("parse.error.count", 10L);

        ExitStatus out = listener.afterStep(stepExecution);
        assertEquals(stepExecution.getExitStatus().getExitCode(), out.getExitCode());
        verify(batchMetrics, never()).counter(any());
        verify(qualityGateResultRepository, times(1)).save(any());
    }

    @Test
    void shouldFailWhenErrorRateTooHigh() {
        BatchMetrics batchMetrics = mock(BatchMetrics.class);
        QualityGateResultRepository qualityGateResultRepository = mock(QualityGateResultRepository.class);
        ImportParseErrorGateProperties properties = new ImportParseErrorGateProperties();
        properties.setMinLines(1L);
        properties.setMaxRate(0.2);
        ParseErrorRateGateListener listener = new ParseErrorRateGateListener(batchMetrics, properties, qualityGateResultRepository);

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        stepExecution.getExecutionContext().putLong("read.count", 1L);
        stepExecution.getExecutionContext().putLong("parse.error.count", 9L);

        ExitStatus out = listener.afterStep(stepExecution);
        assertEquals("PARSE_ERROR_RATE_TOO_HIGH", out.getExitCode());
        verify(batchMetrics, times(1)).counter("import_parse_error_gate_failed_total");
        verify(qualityGateResultRepository, times(1)).save(any());
    }

    @Test
    void shouldApplyPerJobOverride() {
        BatchMetrics batchMetrics = mock(BatchMetrics.class);
        QualityGateResultRepository qualityGateResultRepository = mock(QualityGateResultRepository.class);
        ImportParseErrorGateProperties properties = new ImportParseErrorGateProperties();
        properties.setMinLines(50L);
        properties.setMaxRate(0.9);

        ImportParseErrorGateProperties.Rule rule = new ImportParseErrorGateProperties.Rule();
        rule.setMinLines(1L);
        rule.setMaxRate(0.1);
        properties.getRules().put("job", rule);

        ParseErrorRateGateListener listener = new ParseErrorRateGateListener(batchMetrics, properties, qualityGateResultRepository);

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        stepExecution.getExecutionContext().putLong("read.count", 1L);
        stepExecution.getExecutionContext().putLong("parse.error.count", 1L);

        ExitStatus out = listener.afterStep(stepExecution);
        assertEquals("PARSE_ERROR_RATE_TOO_HIGH", out.getExitCode());
        verify(batchMetrics, times(1)).counter("import_parse_error_gate_failed_total");
        verify(qualityGateResultRepository, times(1)).save(any());
    }
}
