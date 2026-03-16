package com.example.filebatchprocessor.integration;

import com.example.filebatchprocessor.model.BatchRunRecord;
import com.example.filebatchprocessor.model.DlqRecord;
import com.example.filebatchprocessor.repository.BatchRunRecordRepository;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.service.BatchAlertEvaluator;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.listeners.TriggerListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "batch.alert.enabled=true",
        "batch.alert.webhook.enabled=false",
        "batch.alert.failure-rate-threshold=0.2",
        "batch.alert.dlq-backlog-threshold=1",
        "batch.alert.dlq-manual-threshold=1",
        "batch.alert.min-throughput-rps-threshold=100"
})
class QuartzMisfireAlertIT extends PostgresContainerSupport {

    @Autowired
    private Scheduler scheduler;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private BatchAlertEvaluator batchAlertEvaluator;
    @Autowired
    private DlqRecordRepository dlqRecordRepository;
    @Autowired
    private BatchRunRecordRepository batchRunRecordRepository;

    @BeforeEach
    void setUp() {
        dlqRecordRepository.deleteAll();
        batchRunRecordRepository.deleteAll();
    }

    @Test
    void shouldEmitMisfireMetricAndEvaluateAlertRules() throws Exception {
        Trigger synthetic = TriggerBuilder.newTrigger()
                .withIdentity("misfire-it-trigger", "misfire-it-group")
                .startNow()
                .build();

        List<org.quartz.TriggerListener> listeners = scheduler.getListenerManager().getTriggerListeners();
        for (org.quartz.TriggerListener listener : listeners) {
            if (listener instanceof TriggerListenerSupport) {
                listener.triggerMisfired(synthetic);
            }
        }

        double misfireCount = meterRegistry
                .find("quartz_misfire_total")
                .tag("group", "misfire-it-group")
                .counter()
                .count();
        assertTrue(misfireCount >= 1.0, "quartz misfire metric should be incremented");

        DlqRecord dlq = new DlqRecord();
        dlq.setJobName("processFileJob");
        dlq.setParams("taskId=t1");
        dlq.setErrorMessage("synthetic");
        dlq.setHandled(false);
        dlq.setManualRequired(true);
        dlq.setRetryable(true);
        dlq.setNextRetryAt(LocalDateTime.now().minusSeconds(5));
        dlqRecordRepository.save(dlq);

        BatchRunRecord run = new BatchRunRecord();
        run.setJobExecutionId(9001L);
        run.setJobName("processFileJob");
        run.setStatus("FAILED");
        run.setThroughputRps(1.0);
        run.setCreatedAt(LocalDateTime.now());
        batchRunRecordRepository.save(run);

        // Evaluate alert logic path (failure rate, backlog, manual backlog, throughput).
        batchAlertEvaluator.evaluate();
    }
}
