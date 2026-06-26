package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.batch.scheduler.TaskSchedulerService;
import com.example.filebatchprocessor.batch.scheduler.TriggerType;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskTrigger;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(
        properties = {
            "spring.quartz.job-store-type=jdbc",
            "spring.quartz.jdbc.initialize-schema=never",
            "orchestration.enabled=true",
            "orchestration.scheduler.force-leader=true"
        })
class QuartzJdbcRecoveryIT extends PostgresContainerSupport {

    @Autowired
    private TaskSchedulerService taskSchedulerService;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @AfterEach
    void cleanup() throws Exception {
        if (!scheduler.isShutdown()) {
            scheduler.clear();
        }
    }

    @Test
    void shouldRecoverScheduledTriggerFromJdbcStoreAfterSchedulerRestart() throws Exception {
        OrchestrationTaskDefinition def = buildCronTask("quartz-recovery-task");
        taskSchedulerService.register(def);

        TriggerKey triggerKey = triggerKey(def, "cron");
        JobKey jobKey = jobKey(def);
        assertTrue(scheduler.checkExists(triggerKey), "Trigger should exist before restart");

        Long triggerRows = jdbcTemplate.queryForObject(
                "select count(*) from qrtz_triggers where trigger_name = ? and trigger_group = 'orchestration'",
                Long.class,
                triggerKey.getName());
        assertTrue(triggerRows != null && triggerRows > 0, "JDBC JobStore should persist trigger row");

        scheduler.shutdown(true);

        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "recovery-checker");
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        props.setProperty("org.quartz.threadPool.threadCount", "1");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.setProperty("org.quartz.jobStore.isClustered", "false");
        props.setProperty("org.quartz.jobStore.dataSource", "qz");
        props.setProperty("org.quartz.dataSource.qz.driver", "org.postgresql.Driver");
        props.setProperty("org.quartz.dataSource.qz.URL", environment.getProperty("spring.datasource.url"));
        props.setProperty("org.quartz.dataSource.qz.user", environment.getProperty("spring.datasource.username"));
        props.setProperty("org.quartz.dataSource.qz.password", environment.getProperty("spring.datasource.password"));
        props.setProperty("org.quartz.dataSource.qz.maxConnections", "5");

        Scheduler restartScheduler = new StdSchedulerFactory(props).getScheduler();
        try {
            assertTrue(restartScheduler.checkExists(jobKey), "Job detail should still exist after restart");
            assertTrue(restartScheduler.checkExists(triggerKey), "Trigger should still exist after restart");
        } finally {
            restartScheduler.shutdown(true);
        }
    }

    private OrchestrationTaskDefinition buildCronTask(String taskId) {
        OrchestrationTaskTrigger trigger = new OrchestrationTaskTrigger();
        trigger.setType(TriggerType.CRON);
        trigger.setCron("0 0/5 * * * ?");
        return OrchestrationTaskDefinition.builder()
                .id(taskId)
                .jobName("processFileJob")
                .enabled(true)
                .trigger(trigger)
                .build();
    }

    private JobKey jobKey(OrchestrationTaskDefinition def) {
        return JobKey.jobKey("task-" + sanitize(def.getId()), "orchestration");
    }

    private TriggerKey triggerKey(OrchestrationTaskDefinition def, String suffix) {
        return TriggerKey.triggerKey("tr-" + sanitize(def.getId()) + "-" + sanitize(suffix), "orchestration");
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "na";
        }
        return value.replaceAll("[^a-zA-Z0-9\\-_]", "_");
    }
}
