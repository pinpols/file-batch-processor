package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.RecordTrace;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.model.JobLogRecord;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.JobLogRecordRepository;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TraceController {

    private final RecordTraceRepository recordTraceRepository;
    private final ImportedRecordPartitionedRepository importedRecordPartitionedRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final JobLogRecordRepository jobLogRecordRepository;
    private final TaskExecutionStateRepository taskExecutionStateRepository;

    public TraceController(RecordTraceRepository recordTraceRepository,
                           ImportedRecordPartitionedRepository importedRecordPartitionedRepository,
                           DlqRecordRepository dlqRecordRepository,
                           JobLogRecordRepository jobLogRecordRepository,
                           TaskExecutionStateRepository taskExecutionStateRepository) {
        this.recordTraceRepository = recordTraceRepository;
        this.importedRecordPartitionedRepository = importedRecordPartitionedRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.jobLogRecordRepository = jobLogRecordRepository;
        this.taskExecutionStateRepository = taskExecutionStateRepository;
    }

    @GetMapping("/trace/{businessKey}")
    public Map<String, Object> trace(@PathVariable String businessKey) {
        Map<String, Object> result = new HashMap<>();
        List<RecordTrace> traces = recordTraceRepository.findTop200ByBusinessKeyOrderByCreatedAtDesc(businessKey);
        List<ImportedRecordPartitioned> imported = importedRecordPartitionedRepository.findTop50ByBusinessKeyOrderByCreatedAtDesc(businessKey);
        List<DlqRecord> dlq = dlqRecordRepository.findTop50ByParamsContainingOrderByCreatedAtDesc("businessKey=" + businessKey);
        List<JobLogRecord> jobLogs = jobLogRecordRepository.findTop50ByParamsContainingOrderByCreatedAtDesc(businessKey);
        List<TaskExecutionState> taskStates = taskExecutionStateRepository.findTop200ByOrderByUpdatedAtDesc();
        result.put("businessKey", businessKey);
        result.put("traces", traces);
        result.put("importedRecords", imported);
        result.put("dlqRecords", dlq);
        result.put("jobLogs", jobLogs);
        result.put("taskExecutionStates", taskStates);
        return result;
    }
}
