package com.example.filebatchprocessor.controller;

import com.example.filebatchprocessor.model.ReconcileDiffRecord;
import com.example.filebatchprocessor.model.ReconcileRunRecord;
import com.example.filebatchprocessor.repository.ReconcileDiffRecordRepository;
import com.example.filebatchprocessor.repository.ReconcileRunRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ReconcileControllerTest {

    private final ReconcileRunRecordRepository reconcileRunRecordRepository = org.mockito.Mockito.mock(ReconcileRunRecordRepository.class);
    private final ReconcileDiffRecordRepository reconcileDiffRecordRepository = org.mockito.Mockito.mock(ReconcileDiffRecordRepository.class);

    @Test
    void shouldReturnRuns() throws Exception {
        ReconcileRunRecord run = new ReconcileRunRecord();
        run.setId(1L);
        run.setStatus("FAIL");

        org.mockito.Mockito.when(reconcileRunRecordRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(run));

        ReconcileController controller = new ReconcileController(reconcileRunRecordRepository, reconcileDiffRecordRepository);
        List<ReconcileRunRecord> runs = controller.runs();
        org.junit.jupiter.api.Assertions.assertEquals(1, runs.size());
        org.junit.jupiter.api.Assertions.assertEquals(1L, runs.get(0).getId());
        org.junit.jupiter.api.Assertions.assertEquals("FAIL", runs.get(0).getStatus());
    }

    @Test
    void shouldReturnDiffs() throws Exception {
        ReconcileDiffRecord diff = new ReconcileDiffRecord();
        diff.setId(10L);
        diff.setReconcileRunId(1L);
        diff.setDiffType("SOURCE_ONLY");
        diff.setBusinessKey("Alice:2026-03-01");

        org.mockito.Mockito.when(reconcileDiffRecordRepository.findTop200ByReconcileRunIdOrderByIdAsc(1L)).thenReturn(List.of(diff));

        ReconcileController controller = new ReconcileController(reconcileRunRecordRepository, reconcileDiffRecordRepository);
        Map<String, Object> result = controller.diffs(1L);
        org.junit.jupiter.api.Assertions.assertEquals(1L, result.get("reconcileRunId"));
        org.junit.jupiter.api.Assertions.assertEquals(1, ((List<?>) result.get("diffs")).size());
    }
}
