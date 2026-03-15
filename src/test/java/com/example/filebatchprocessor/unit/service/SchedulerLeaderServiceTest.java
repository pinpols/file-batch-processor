package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.repository.SchedulerLeaderLockRepository;
import com.example.filebatchprocessor.service.SchedulerLeaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerLeaderServiceTest {

    @Mock
    private SchedulerLeaderLockRepository repository;

    @Mock
    private BatchMetrics batchMetrics;

    private SchedulerLeaderService service;

    @BeforeEach
    void setUp() {
        service = new SchedulerLeaderService(repository, batchMetrics);
        ReflectionTestUtils.setField(service, "lockName", "orchestration-scheduler");
        ReflectionTestUtils.setField(service, "ttlSeconds", 30L);
        ReflectionTestUtils.setField(service, "forceLeader", false);
    }

    @Test
    void shouldAcquireLeadership() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);

        service.refreshLeadership();

        assertTrue(service.isLeader());
        verify(batchMetrics, atLeastOnce()).gauge(anyString(), any(), anyString(), anyString());
    }

    @Test
    void shouldLoseLeadershipOnException() {
        when(repository.tryAcquireOrRenew(anyString(), anyString(), any(LocalDateTime.class))).thenThrow(new RuntimeException("boom"));

        service.refreshLeadership();

        assertFalse(service.isLeader());
        verify(batchMetrics, atLeastOnce()).gauge(anyString(), any(), anyString(), anyString());
    }

    @Test
    void shouldRespectForceLeaderFlag() {
        ReflectionTestUtils.setField(service, "forceLeader", true);

        assertTrue(service.isLeader());
        verify(repository, never()).tryAcquireOrRenew(anyString(), anyString(), any(LocalDateTime.class));
    }
}
