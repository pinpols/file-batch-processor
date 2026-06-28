package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.service.SchedulerLeaderService;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.quartz.job-store-type=jdbc",
            "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.PostgreSQLDelegate",
            "spring.quartz.jdbc.initialize-schema=never",
            "orchestration.config-source=db",
            "orchestration.enabled=false",
            "orchestration.scheduler.force-leader=true"
        })
@TestInstance(Lifecycle.PER_CLASS)
class TaskSchedulerServiceDlqIT extends PostgresContainerSupport {

    @Autowired
    private TaskSchedulerService taskSchedulerService;

    @Autowired
    private DlqRecordRepository dlqRecordRepository;

    @Autowired
    private SchedulerLeaderService schedulerLeaderService;

    @BeforeEach
    void reset() {
        dlqRecordRepository.deleteAll();
    }

    @Test
    void missingJobIsRoutedToDlq() throws Exception {
        OrchestrationTaskDefinition def = OrchestrationTaskDefinition.builder()
                .id("missing-job-task")
                .jobName("missingJob")
                .build();

        awaitLeader();
        taskSchedulerService.register(def);
        awaitDlqCountAtLeast(1);
        assertTrue(dlqRecordRepository.count() >= 1, "DLQ should contain failed schedule for missing job");
    }

    private void awaitDlqCountAtLeast(long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000L;
        while (System.currentTimeMillis() < deadline) {
            if (dlqRecordRepository.count() >= expected) {
                return;
            }
            Thread.sleep(200L);
        }
        fail("Timed out waiting for DLQ records");
    }

    private void awaitLeader() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (schedulerLeaderService.isLeader()) {
                return;
            }
            Thread.sleep(200L);
        }
        fail("Timed out waiting for scheduler leader");
    }
}
