package com.example.filebatchprocessor.unit.controller;

import com.example.filebatchprocessor.controller.TraceController;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.JobLogRecord;
import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.JobLogRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class TraceControllerTest {

    private final RecordTraceRepository recordTraceRepository = org.mockito.Mockito.mock(RecordTraceRepository.class);
    private final ImportedRecordPartitionedRepository importedRecordPartitionedRepository = org.mockito.Mockito.mock(ImportedRecordPartitionedRepository.class);
    private final DlqRecordRepository dlqRecordRepository = org.mockito.Mockito.mock(DlqRecordRepository.class);
    private final JobLogRecordRepository jobLogRecordRepository = org.mockito.Mockito.mock(JobLogRecordRepository.class);
    private final TaskExecutionStateRepository taskExecutionStateRepository = org.mockito.Mockito.mock(TaskExecutionStateRepository.class);

    @Test
    void shouldAggregateTraceData() throws Exception {
        String businessKey = "Alice:2026-03-01";

        RecordTrace trace = new RecordTrace();
        trace.setBusinessKey(businessKey);
        trace.setEventType("IMPORT");

        ImportedRecordPartitioned imported = new ImportedRecordPartitioned();
        imported.setBusinessKey(businessKey);

        DlqRecord dlq = new DlqRecord();
        dlq.setParams("businessKey=" + businessKey + "&x=y");

        JobLogRecord log = new JobLogRecord();
        log.setParams("businessKey=" + businessKey);

        TaskExecutionState state = new TaskExecutionState();
        state.setTaskId("t1");

        org.mockito.Mockito.when(recordTraceRepository.findTop200ByBusinessKeyOrderByCreatedAtDesc(businessKey)).thenReturn(List.of(trace));
        org.mockito.Mockito.when(importedRecordPartitionedRepository.findTop50ByBusinessKeyOrderByCreatedAtDesc(businessKey)).thenReturn(List.of(imported));
        org.mockito.Mockito.when(dlqRecordRepository.findTop50ByParamsContainingOrderByCreatedAtDesc("businessKey=" + businessKey)).thenReturn(List.of(dlq));
        org.mockito.Mockito.when(jobLogRecordRepository.findTop50ByParamsContainingOrderByCreatedAtDesc(businessKey)).thenReturn(List.of(log));
        org.mockito.Mockito.when(taskExecutionStateRepository.findTop200ByOrderByUpdatedAtDesc()).thenReturn(List.of(state));

        TraceController controller = new TraceController(
                recordTraceRepository,
                importedRecordPartitionedRepository,
                dlqRecordRepository,
                jobLogRecordRepository,
                taskExecutionStateRepository
        );

        Map<String, Object> result = controller.trace(businessKey);
        org.junit.jupiter.api.Assertions.assertEquals(businessKey, result.get("businessKey"));
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) result.get("traces")).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) result.get("importedRecords")).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) result.get("dlqRecords")).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) result.get("jobLogs")).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) result.get("taskExecutionStates")).size());
    }
}
