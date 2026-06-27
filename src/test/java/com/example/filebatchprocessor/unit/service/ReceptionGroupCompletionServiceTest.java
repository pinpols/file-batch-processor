package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.model.ReceptionGroup;
import com.example.filebatchprocessor.model.ReceptionGroupMember;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupMemberRepository;
import com.example.filebatchprocessor.repository.ReceptionGroupRepository;
import com.example.filebatchprocessor.service.ManifestReconcileService;
import com.example.filebatchprocessor.service.ManifestReconcileService.ReconcileResult;
import com.example.filebatchprocessor.service.ReceptionGroupCompletionService;
import com.example.filebatchprocessor.service.ReceptionImportTrigger;
import com.example.filebatchprocessor.service.FileAlertService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReceptionGroupCompletionServiceTest {

    private final ReceptionGroupRepository groupRepo = mock(ReceptionGroupRepository.class);
    private final ReceptionGroupMemberRepository memberRepo = mock(ReceptionGroupMemberRepository.class);
    private final FileReceptionQueueRepository queueRepo = mock(FileReceptionQueueRepository.class);
    private final ManifestReconcileService reconcileService = mock(ManifestReconcileService.class);
    private final FileAlertService fileAlertService = mock(FileAlertService.class);
    private final ReceptionImportTrigger importTrigger = mock(ReceptionImportTrigger.class);

    private final ReceptionGroupCompletionService service =
            new ReceptionGroupCompletionService(
                    groupRepo, memberRepo, queueRepo, reconcileService, fileAlertService, importTrigger);

    private ReceptionGroup group(Long id) {
        ReceptionGroup g = new ReceptionGroup();
        g.setId(id);
        g.setManifestId("M-" + id);
        g.setSourceSystem("CORE");
        g.setBizDate("2026-06-27");
        g.setStatus("WAITING_FILES");
        g.setTotalMembers(2);
        g.setArrivedMembers(2);
        return g;
    }

    private ReceptionGroupMember member(Long id, Long groupId, Long actualQueueId, boolean required) {
        ReceptionGroupMember m = new ReceptionGroupMember();
        m.setId(id);
        m.setGroupId(groupId);
        m.setExpectedFileName("f-" + id + ".csv");
        m.setRequired(required);
        m.setActualQueueId(actualQueueId);
        return m;
    }

    @Test
    void completesAndTriggersWhenAllArrivedAndReconcilePass() {
        ReceptionGroup g = group(1L);
        ReceptionGroupMember m1 = member(11L, 1L, 101L, true);
        ReceptionGroupMember m2 = member(12L, 1L, 102L, true);

        when(groupRepo.findById(1L)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupId(1L)).thenReturn(List.of(m1, m2));
        when(queueRepo.findById(101L)).thenReturn(Optional.of(new FileReceptionQueue()));
        when(queueRepo.findById(102L)).thenReturn(Optional.of(new FileReceptionQueue()));
        when(reconcileService.reconcile(any(), any()))
                .thenReturn(new ReconcileResult(true, List.of()));

        service.evaluate(1L);

        assertEquals("DISPATCHED", g.getStatus());
        verify(importTrigger).triggerImport(101L);
        verify(importTrigger).triggerImport(102L);
        verify(importTrigger, times(2)).triggerImport(anyLong());
        verify(fileAlertService, never())
                .createAlert(
                        anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void expiresWhenRequiredMissingAndDeadlinePassed() {
        ReceptionGroup g = group(2L);
        g.setDeadline(LocalDateTime.now().minusMinutes(1));
        ReceptionGroupMember m1 = member(21L, 2L, 201L, true);
        ReceptionGroupMember m2 = member(22L, 2L, null, true); // missing required

        when(groupRepo.findById(2L)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupId(2L)).thenReturn(List.of(m1, m2));

        service.evaluate(2L);

        assertEquals("EXPIRED", g.getStatus());
        verify(fileAlertService, times(1))
                .createAlert(
                        eq("GROUP_INCOMPLETE"),
                        eq("GROUP_INCOMPLETE"),
                        eq("CRITICAL"),
                        anyString(),
                        any(),
                        eq("CORE"),
                        eq("2026-06-27"),
                        any(),
                        any());
        verify(importTrigger, never()).triggerImport(anyLong());
    }

    @Test
    void failsWhenReconcileFails() {
        ReceptionGroup g = group(3L);
        ReceptionGroupMember m1 = member(31L, 3L, 301L, true);
        ReceptionGroupMember m2 = member(32L, 3L, 302L, true);

        when(groupRepo.findById(3L)).thenReturn(Optional.of(g));
        when(memberRepo.findByGroupId(3L)).thenReturn(List.of(m1, m2));
        when(queueRepo.findById(301L)).thenReturn(Optional.of(new FileReceptionQueue()));
        when(queueRepo.findById(302L)).thenReturn(Optional.of(new FileReceptionQueue()));
        when(reconcileService.reconcile(eq(m1), any()))
                .thenReturn(new ReconcileResult(true, List.of()));
        when(reconcileService.reconcile(eq(m2), any()))
                .thenReturn(new ReconcileResult(false, List.of("checksum mismatch")));

        service.evaluate(3L);

        assertEquals("FAILED", g.getStatus());
        verify(fileAlertService, times(1))
                .createAlert(
                        eq("GROUP_RECONCILE_FAIL"),
                        eq("GROUP_RECONCILE_FAIL"),
                        eq("CRITICAL"),
                        anyString(),
                        any(),
                        eq("CORE"),
                        eq("2026-06-27"),
                        any(),
                        any());
        verify(importTrigger, never()).triggerImport(anyLong());
    }
}
