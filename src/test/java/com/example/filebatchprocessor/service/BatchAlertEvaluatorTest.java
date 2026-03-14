package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchAlertEvaluatorTest {

    @Mock
    private BatchRunRecordRepository batchRunRecordRepository;
    @Mock
    private DlqRecordRepository dlqRecordRepository;

    private BatchAlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BatchAlertEvaluator(batchRunRecordRepository, dlqRecordRepository);
        ReflectionTestUtils.setField(evaluator, "enabled", true);
        ReflectionTestUtils.setField(evaluator, "failureRateThreshold", 0.2d);
        ReflectionTestUtils.setField(evaluator, "dlqBacklogThreshold", 100L);
        ReflectionTestUtils.setField(evaluator, "dlqManualThreshold", 20L);
        ReflectionTestUtils.setField(evaluator, "minThroughputRpsThreshold", 5.0d);
        ReflectionTestUtils.setField(evaluator, "webhookEnabled", false);
    }

    @Test
    void shouldSkipWhenAlertDisabled() {
        ReflectionTestUtils.setField(evaluator, "enabled", false);

        evaluator.evaluate();

        verify(batchRunRecordRepository, never()).countByStatusAndCreatedAtAfter(any(), any(LocalDateTime.class));
    }

    @Test
    void shouldEvaluateAllThresholds() {
        when(batchRunRecordRepository.countByStatusAndCreatedAtAfter(eq("FAILED"), any(LocalDateTime.class))).thenReturn(5L);
        when(batchRunRecordRepository.countByStatusAndCreatedAtAfter(eq("COMPLETED"), any(LocalDateTime.class))).thenReturn(10L);
        when(batchRunRecordRepository.countByStatusAndCreatedAtAfter(eq("PARTIAL"), any(LocalDateTime.class))).thenReturn(0L);
        when(dlqRecordRepository.countByHandledFalse()).thenReturn(120L);
        when(dlqRecordRepository.countByHandledFalseAndManualRequiredTrue()).thenReturn(25L);
        BatchRunRecord record = new BatchRunRecord();
        record.setThroughputRps(1.0);
        when(batchRunRecordRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(record));

        evaluator.evaluate();

        verify(dlqRecordRepository).countByHandledFalse();
        verify(dlqRecordRepository).countByHandledFalseAndManualRequiredTrue();
        verify(batchRunRecordRepository).findTop200ByOrderByCreatedAtDesc();
    }
}

