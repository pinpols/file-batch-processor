package com.example.filebatchprocessor.unit.observability;

import com.example.filebatchprocessor.observability.BatchMetrics;
import com.example.filebatchprocessor.observability.QuartzMetricsRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.TriggerListener;
import org.quartz.impl.matchers.EverythingMatcher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuartzMetricsRegistrarTest {

    @Mock
    private Scheduler scheduler;
    @Mock
    private BatchMetrics batchMetrics;
    @Mock
    private ListenerManager listenerManager;
    @Mock
    private Trigger trigger;

    private QuartzMetricsRegistrar registrar;

    @BeforeEach
    void setUp() throws Exception {
        registrar = new QuartzMetricsRegistrar(scheduler, batchMetrics);
        when(scheduler.getListenerManager()).thenReturn(listenerManager);
    }

    @Test
    void shouldRegisterGaugesAndMisfireListener() throws Exception {
        registrar.register();

        verify(batchMetrics, times(4)).gauge(any(), any());
        verify(listenerManager).addTriggerListener(any(TriggerListener.class), any(EverythingMatcher.class));
    }

    @Test
    void shouldIncrementCounterOnMisfire() throws Exception {
        registrar.register();
        ArgumentCaptor<TriggerListener> captor = ArgumentCaptor.forClass(TriggerListener.class);
        verify(listenerManager).addTriggerListener(captor.capture(), any(EverythingMatcher.class));

        TriggerKey triggerKey = TriggerKey.triggerKey("tr1", "groupA");
        when(trigger.getKey()).thenReturn(triggerKey);
        captor.getValue().triggerMisfired(trigger);

        verify(batchMetrics).counter("quartz_misfire_total", "group", "groupA");
    }
}
