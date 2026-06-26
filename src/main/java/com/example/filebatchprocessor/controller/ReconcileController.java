package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.ReconcileDiffRecord;
import com.example.filebatchprocessor.model.ReconcileRunRecord;
import com.example.filebatchprocessor.repository.ReconcileDiffRecordRepository;
import com.example.filebatchprocessor.repository.ReconcileRunRecordRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconcile")
public class ReconcileController {

    private final ReconcileRunRecordRepository reconcileRunRecordRepository;
    private final ReconcileDiffRecordRepository reconcileDiffRecordRepository;

    public ReconcileController(
            ReconcileRunRecordRepository reconcileRunRecordRepository,
            ReconcileDiffRecordRepository reconcileDiffRecordRepository) {
        this.reconcileRunRecordRepository = reconcileRunRecordRepository;
        this.reconcileDiffRecordRepository = reconcileDiffRecordRepository;
    }

    @GetMapping("/runs")
    public List<ReconcileRunRecord> runs() {
        return reconcileRunRecordRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @GetMapping("/runs/{runId}/diffs")
    public Map<String, Object> diffs(@PathVariable Long runId) {
        List<ReconcileDiffRecord> diffs = reconcileDiffRecordRepository.findTop200ByReconcileRunIdOrderByIdAsc(runId);
        Map<String, Object> result = new HashMap<>();
        result.put("reconcileRunId", runId);
        result.put("diffs", diffs);
        return result;
    }
}
