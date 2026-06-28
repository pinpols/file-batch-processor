package com.example.filebatchprocessor.unit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.service.BatchAlertEvaluator;
import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BatchAlertEvaluatorDispatchTest {

    @Test
    void dispatchesWhenDlqBacklogExceedsThreshold() {
        BatchRunRecordRepository batchRepo = mock(BatchRunRecordRepository.class);
        DlqRecordRepository dlqRepo = mock(DlqRecordRepository.class);
        AlertDispatcher dispatcher = mock(AlertDispatcher.class);

        when(batchRepo.countByStatusAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(batchRepo.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(dlqRepo.countByHandledFalse()).thenReturn(9999L);
        when(dlqRepo.countByHandledFalseAndManualRequiredTrue()).thenReturn(0L);

        BatchAlertEvaluator evaluator = new BatchAlertEvaluator(batchRepo, dlqRepo, dispatcher, Optional.empty());
        ReflectionTestUtils.setField(evaluator, "enabled", true);
        ReflectionTestUtils.setField(evaluator, "dlqBacklogThreshold", 100L);
        ReflectionTestUtils.setField(evaluator, "failureRateThreshold", 0.2);
        ReflectionTestUtils.setField(evaluator, "dlqManualThreshold", 20L);
        ReflectionTestUtils.setField(evaluator, "minThroughputRpsThreshold", 5.0);

        evaluator.evaluate();

        verify(dispatcher, atLeastOnce()).dispatch(any(AlertEvent.class));
    }
}
