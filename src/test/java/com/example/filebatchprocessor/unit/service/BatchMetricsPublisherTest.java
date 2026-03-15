package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.service.BatchMetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchMetricsPublisherTest {

    @Mock
    private BatchRunRecordRepository batchRunRecordRepository;
    @Mock
    private DlqRecordRepository dlqRecordRepository;
    @Mock
    private TaskExecutionStateRepository taskExecutionStateRepository;
    @Mock
    private TaskDefinitionRepository taskDefinitionRepository;

    private BatchMetricsPublisher publisher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new BatchMetricsPublisher(
                meterRegistry,
                batchRunRecordRepository,
                dlqRecordRepository,
                taskExecutionStateRepository,
                taskDefinitionRepository
        );
        publisher.init();
    }

    @Test
    void shouldRefreshMetricsAndPublishGauges() {
        when(batchRunRecordRepository.countByStatusAndCreatedAtAfter(eq("FAILED"), any(LocalDateTime.class))).thenReturn(2L);
        when(batchRunRecordRepository.countByStatusAndCreatedAtAfter(eq("COMPLETED"), any(LocalDateTime.class))).thenReturn(8L);
        when(dlqRecordRepository.countByHandledFalse()).thenReturn(3L);
        when(dlqRecordRepository.countByHandledFalseAndManualRequiredTrue()).thenReturn(1L);
        when(dlqRecordRepository.countByHandledFalseAndCompensationStatus("RETRY_PENDING")).thenReturn(2L);
        when(taskExecutionStateRepository.countByStatusIn(List.of("BLOCKED", "READY", "RUNNING"))).thenReturn(4L);

        BatchRunRecord runRecord = new BatchRunRecord();
        runRecord.setJobName("importJob");
        runRecord.setDurationMs(2500L);
        runRecord.setThroughputRps(6.5);
        when(batchRunRecordRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(runRecord));

        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setJobName("importJob");
        taskDefinition.setSlaMaxDurationMs(2000L);
        taskDefinition.setEnabled(true);
        when(taskDefinitionRepository.findByEnabledTrue()).thenReturn(List.of(taskDefinition));

        publisher.refresh();

        assertNotNull(meterRegistry.find("batch_recent_failure_count").gauge());
        assertNotNull(meterRegistry.find("batch_dlq_backlog").gauge());
        assertNotNull(meterRegistry.find("batch_sla_duration_breach_count").gauge());
    }
}

