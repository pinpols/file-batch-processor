package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.model.TaskExecutionState;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.OpsChangeRequestRepository;
import com.example.filebatchprocessor.repository.TaskExecutionStateRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 轻量运维看板接口，可直接被 Grafana/前端查询。
 */
@RestController
@RequestMapping("/ops")
public class OpsDashboardController {

    private final BatchRunRecordRepository batchRunRecordRepository;
    private final DlqRecordRepository dlqRecordRepository;
    private final TaskExecutionStateRepository taskExecutionStateRepository;
    private final OpsChangeRequestRepository opsChangeRequestRepository;

    public OpsDashboardController(
            BatchRunRecordRepository batchRunRecordRepository,
            DlqRecordRepository dlqRecordRepository,
            TaskExecutionStateRepository taskExecutionStateRepository,
            OpsChangeRequestRepository opsChangeRequestRepository) {
        this.batchRunRecordRepository = batchRunRecordRepository;
        this.dlqRecordRepository = dlqRecordRepository;
        this.taskExecutionStateRepository = taskExecutionStateRepository;
        this.opsChangeRequestRepository = opsChangeRequestRepository;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new HashMap<>();

        LocalDateTime since = LocalDateTime.now().minusHours(1);
        long failed1h = batchRunRecordRepository.countByStatusAndCreatedAtAfter("FAILED", since);
        long completed1h = batchRunRecordRepository.countByStatusAndCreatedAtAfter("COMPLETED", since);
        long partial1h = batchRunRecordRepository.countByStatusAndCreatedAtAfter("PARTIAL", since);
        long total1h = failed1h + completed1h + partial1h;
        double failureRate1h = total1h == 0 ? 0.0 : (double) failed1h / total1h;

        long dlqBacklog = dlqRecordRepository.countByHandledFalse();
        long blockedCount = taskExecutionStateRepository.countByStatusIn(List.of("BLOCKED"));
        long runningCount = taskExecutionStateRepository.countByStatusIn(List.of("RUNNING"));
        long readyCount = taskExecutionStateRepository.countByStatusIn(List.of("READY"));
        long pendingChangeRequests = opsChangeRequestRepository.countByStatus("PENDING_APPROVAL");

        List<BatchRunRecord> recentRuns = batchRunRecordRepository.findTop200ByOrderByCreatedAtDesc();
        double avgThroughputRps = recentRuns.stream()
                .mapToDouble(v -> v.getThroughputRps() == null ? 0.0 : v.getThroughputRps())
                .average()
                .orElse(0.0);

        List<TaskExecutionState> latestTaskStates = taskExecutionStateRepository.findTop200ByOrderByUpdatedAtDesc();

        result.put("window", "1h");
        result.put("failed1h", failed1h);
        result.put("completed1h", completed1h);
        result.put("partial1h", partial1h);
        result.put("failureRate1h", failureRate1h);
        result.put("dlqBacklog", dlqBacklog);
        result.put("blockedTaskCount", blockedCount);
        result.put("runningTaskCount", runningCount);
        result.put("readyTaskCount", readyCount);
        result.put("pendingChangeRequests", pendingChangeRequests);
        result.put("avgThroughputRps", avgThroughputRps);
        result.put("recentTaskStates", latestTaskStates);
        return result;
    }
}
