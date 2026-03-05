package com.example.filebatchprocessor.service.distribution;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.model.FileDistributionTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileDistributorDispatcherTest {

    @Test
    void shouldRouteToSupportingDistributor() {
        FileDistributor d1 = mock(FileDistributor.class);
        FileDistributor d2 = mock(FileDistributor.class);

        when(d1.supports("HTTP")).thenReturn(false);
        when(d2.supports("HTTP")).thenReturn(true);

        FileDistributorDispatcher dispatcher = new FileDistributorDispatcher(List.of(d1, d2));

        FileDistributionTask task = new FileDistributionTask();
        task.setTargetSystem("HTTP");

        dispatcher.distribute(task);

        verify(d2, times(1)).distribute(task);
        verify(d1, never()).distribute(any());
    }

    @Test
    void shouldThrowIfNoDistributorSupportsTarget() {
        FileDistributor d1 = mock(FileDistributor.class);
        when(d1.supports(any())).thenReturn(false);

        FileDistributorDispatcher dispatcher = new FileDistributorDispatcher(List.of(d1));
        FileDistributionTask task = new FileDistributionTask();
        task.setTargetSystem("UNKNOWN");

        assertThrows(BusinessException.class, () -> dispatcher.distribute(task));
    }
}
