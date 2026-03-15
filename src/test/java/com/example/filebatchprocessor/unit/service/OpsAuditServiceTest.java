package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.OpsAuditLog;
import com.example.filebatchprocessor.repository.OpsAuditLogRepository;
import com.example.filebatchprocessor.service.OpsAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpsAuditServiceTest {

    @Mock
    private OpsAuditLogRepository auditLogRepository;

    @InjectMocks
    private OpsAuditService opsAuditService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void shouldLogAuditEvent() {
        when(auditLogRepository.save(any(OpsAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        opsAuditService.log("FILE_VIEW", "operator", "FILE", "1", "SUCCESS", "View file detail");

        verify(auditLogRepository).save(any(OpsAuditLog.class));
    }

    @Test
    void shouldReturnRecentLogs() {
        OpsAuditLog log = new OpsAuditLog();
        log.setId(1L);
        
        when(auditLogRepository.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        var result = opsAuditService.recentLogs();

        assertFalse(result.isEmpty());
    }
}
