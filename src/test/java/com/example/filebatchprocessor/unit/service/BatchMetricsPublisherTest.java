package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import com.example.filebatchprocessor.service.BatchMetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                taskDefinitionRepository);
        publisher.init();
    }

    @Test
    void shouldRefreshMetricsAndPublishGauges() {
        when(batchRunRecordRepository.countByStatusAndCreatedAtAfter(eq("FAILED"), any(LocalDateTime.class)))
                .thenReturn(2L);
        when(batchRunRecordRepository.countByStatusAndCreatedAtAfter(eq("COMPLETED"), any(LocalDateTime.class)))
                .thenReturn(8L);
        when(dlqRecordRepository.countByHandledFalse()).thenReturn(3L);
        when(dlqRecordRepository.countByHandledFalseAndManualRequiredTrue()).thenReturn(1L);
        when(dlqRecordRepository.countByHandledFalseAndCompensationStatus("RETRY_PENDING"))
                .thenReturn(2L);
        when(taskExecutionStateRepository.countByStatusIn(List.of("BLOCKED", "READY", "RUNNING")))
                .thenReturn(4L);

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

        // 断言 refresh 后每个 gauge 的实际数值与 mock 输入一致(而非仅断言 gauge 存在)
        assertEquals(
                2.0,
                meterRegistry.find("batch_recent_failure_count").gauge().value(),
                "FAILED 计数应等于 repository 返回的 2");
        assertEquals(
                8.0,
                meterRegistry.find("batch_recent_completed_count").gauge().value(),
                "COMPLETED 计数应等于 repository 返回的 8");
        assertEquals(
                3.0, meterRegistry.find("batch_dlq_backlog").gauge().value(), "DLQ 积压应等于 3");
        assertEquals(
                1.0,
                meterRegistry.find("batch_dlq_manual_backlog").gauge().value(),
                "需人工处理的 DLQ 积压应等于 1");
        assertEquals(
                2.0,
                meterRegistry.find("batch_dlq_retry_pending").gauge().value(),
                "待重试 DLQ 应等于 2");
        assertEquals(
                4.0,
                meterRegistry.find("batch_blocked_task_count").gauge().value(),
                "阻塞/就绪/运行中任务计数应等于 4");
        // recent run throughputRps = 6.5,均值即 6.5(以 milli 精度还原)
        assertEquals(
                6.5,
                meterRegistry.find("batch_avg_throughput_rps").gauge().value(),
                0.0005,
                "平均吞吐应等于唯一一条记录的 6.5 rps");
        // importJob 实际耗时 2500ms > SLA 2000ms → 恰好 1 次 SLA 违约
        assertEquals(
                1.0,
                meterRegistry.find("batch_sla_duration_breach_count").gauge().value(),
                "importJob 2500ms 超过 SLA 2000ms,应计 1 次违约");
    }
}
