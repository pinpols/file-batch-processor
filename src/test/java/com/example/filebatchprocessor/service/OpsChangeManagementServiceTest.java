package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.OpsChangeRequest;
import com.example.filebatchprocessor.model.TaskDefinition;
import com.example.filebatchprocessor.model.TaskParameter;
import com.example.filebatchprocessor.model.TaskTrigger;
import com.example.filebatchprocessor.repository.OpsChangeRequestRepository;
import com.example.filebatchprocessor.repository.TaskDefinitionRepository;
import com.example.filebatchprocessor.repository.TaskParameterRepository;
import com.example.filebatchprocessor.repository.TaskTriggerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsChangeManagementServiceTest {

    @Mock
    private OpsChangeRequestRepository opsChangeRequestRepository;
    @Mock
    private TaskDefinitionRepository taskDefinitionRepository;
    @Mock
    private TaskTriggerRepository taskTriggerRepository;
    @Mock
    private TaskParameterRepository taskParameterRepository;
    @Mock
    private OpsAuditService opsAuditService;

    private OpsChangeManagementService service;

    @BeforeEach
    void setUp() {
        service = new OpsChangeManagementService(
                opsChangeRequestRepository,
                taskDefinitionRepository,
                taskTriggerRepository,
                taskParameterRepository,
                opsAuditService
        );
    }

    @Test
    void shouldCreatePendingRequest() {
        TaskDefinition definition = new TaskDefinition();
        definition.setTaskId("task-1");
        definition.setEnabled(true);
        when(taskDefinitionRepository.findByTaskId("task-1")).thenReturn(Optional.of(definition));
        when(opsChangeRequestRepository.save(any(OpsChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpsChangeRequest request = service.createRequest(
                "operator",
                "task_definition",
                "task-1",
                "enabled",
                "false",
                "disable temporarily",
                null,
                null,
                "low impact",
                "LOW",
                "enable=true"
        );

        assertEquals("PENDING_APPROVAL", request.getStatus());
        assertEquals("TASK_DEFINITION", request.getTargetType());
        verify(opsAuditService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldApprovePendingRequest() {
        OpsChangeRequest request = new OpsChangeRequest();
        request.setId(1L);
        request.setRequestNo("CR-0001");
        request.setStatus("PENDING_APPROVAL");
        when(opsChangeRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(opsChangeRequestRepository.save(any(OpsChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpsChangeRequest approved = service.approve(1L, "admin");

        assertEquals("APPROVED", approved.getStatus());
        assertEquals("admin", approved.getApprovedBy());
    }

    @Test
    void shouldRejectPendingRequest() {
        OpsChangeRequest request = new OpsChangeRequest();
        request.setId(2L);
        request.setRequestNo("CR-0002");
        request.setStatus("PENDING_APPROVAL");
        when(opsChangeRequestRepository.findById(2L)).thenReturn(Optional.of(request));
        when(opsChangeRequestRepository.save(any(OpsChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpsChangeRequest rejected = service.reject(2L, "admin", "risk too high");

        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("risk too high", rejected.getRejectReason());
    }

    @Test
    void shouldApplyApprovedTaskDefinitionChangeWithinWindow() {
        OpsChangeRequest request = new OpsChangeRequest();
        request.setId(3L);
        request.setRequestNo("CR-0003");
        request.setStatus("APPROVED");
        request.setTargetType("TASK_DEFINITION");
        request.setTaskId("task-3");
        request.setFieldName("priority");
        request.setNewValue("HIGH");
        request.setWindowStart(LocalDateTime.now().minusMinutes(5));
        request.setWindowEnd(LocalDateTime.now().plusMinutes(5));

        TaskDefinition def = new TaskDefinition();
        def.setTaskId("task-3");
        def.setPriority("LOW");

        when(opsChangeRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(taskDefinitionRepository.findByTaskId("task-3")).thenReturn(Optional.of(def));
        when(taskDefinitionRepository.save(any(TaskDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(opsChangeRequestRepository.save(any(OpsChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpsChangeRequest applied = service.apply(3L, "admin");

        assertEquals("APPLIED", applied.getStatus());
        assertEquals("admin", applied.getAppliedBy());
        assertEquals("HIGH", def.getPriority());
    }

    @Test
    void shouldRejectApplyOutsideWindow() {
        OpsChangeRequest request = new OpsChangeRequest();
        request.setId(4L);
        request.setStatus("APPROVED");
        request.setTargetType("TASK_PARAMETER");
        request.setTaskId("task-4");
        request.setFieldName("batchDate");
        request.setNewValue("2026-03-14");
        request.setWindowStart(LocalDateTime.now().plusHours(1));

        when(opsChangeRequestRepository.findById(4L)).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.apply(4L, "admin"));
    }

    @Test
    void shouldListRecentRequests() {
        OpsChangeRequest request = new OpsChangeRequest();
        request.setRequestNo("CR-0005");
        when(opsChangeRequestRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(request));

        List<OpsChangeRequest> list = service.listRecent();

        assertEquals(1, list.size());
        assertEquals("CR-0005", list.get(0).getRequestNo());
    }

    @Test
    void shouldCreateTaskParameterWhenApplyingParameterChange() {
        OpsChangeRequest request = new OpsChangeRequest();
        request.setId(5L);
        request.setStatus("APPROVED");
        request.setTargetType("TASK_PARAMETER");
        request.setTaskId("task-5");
        request.setFieldName("format");
        request.setNewValue("csv");

        when(opsChangeRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(taskParameterRepository.findByTaskIdAndParamName("task-5", "format")).thenReturn(Optional.empty());
        when(taskParameterRepository.save(any(TaskParameter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(opsChangeRequestRepository.save(any(OpsChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.apply(5L, "admin");

        verify(taskParameterRepository).save(any(TaskParameter.class));
    }

    @Test
    void shouldThrowOnUnsupportedTargetType() {
        assertThrows(IllegalArgumentException.class, () -> service.createRequest(
                "op", "unknown", "task", "enabled", "true", "reason", null, null, null, null, null
        ));
    }

    @Test
    void shouldThrowWhenApproveNonPending() {
        OpsChangeRequest request = new OpsChangeRequest();
        request.setId(6L);
        request.setStatus("REJECTED");
        when(opsChangeRequestRepository.findById(6L)).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.approve(6L, "admin"));
    }
}

