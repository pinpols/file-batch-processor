package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.service.EnhancedClusterSchedulerService;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerMetaData;

class EnhancedClusterSchedulerServiceTest {

    @Test
    void shouldUseConfiguredInstanceId() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        SchedulerMetaData metaData = mock(SchedulerMetaData.class);
        when(scheduler.getMetaData()).thenReturn(metaData);
        when(metaData.isJobStoreClustered()).thenReturn(true);
        when(metaData.getSchedulerName()).thenReturn("fileBatchQuartzScheduler");
        when(metaData.getSchedulerInstanceId()).thenReturn("AUTO");
        when(metaData.isStarted()).thenReturn(true);
        when(metaData.isShutdown()).thenReturn(false);
        when(metaData.isInStandbyMode()).thenReturn(false);
        when(metaData.getNumberOfJobsExecuted()).thenReturn(0);
        when(metaData.getRunningSince()).thenReturn(null);
        when(scheduler.isStarted()).thenReturn(true);

        EnhancedClusterSchedulerService service = new EnhancedClusterSchedulerService(scheduler, "node-a", 15000L, 3);

        service.initialize();

        assertTrue(service.isActiveClusterNode());
        assertEquals("node-a", service.getClusterStatus().getInstanceId());
    }

    @Test
    void shouldGenerateInstanceIdWhenConfigBlank() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        SchedulerMetaData metaData = mock(SchedulerMetaData.class);
        when(scheduler.getMetaData()).thenReturn(metaData);
        when(metaData.isJobStoreClustered()).thenReturn(false);

        EnhancedClusterSchedulerService service = new EnhancedClusterSchedulerService(scheduler, "   ", 15000L, 3);

        service.initialize();

        assertFalse(service.isActiveClusterNode());
        assertFalse(service.getClusterStatus().getInstanceId().isBlank());
    }
}
