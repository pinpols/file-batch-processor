package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("isDockerAvailable")
@TestPropertySource(
        properties = {
            "orchestration.enabled=false",
            "spring.quartz.auto-startup=true",
            "spring.quartz.job-store-type=jdbc",
            "spring.quartz.overwrite-existing-jobs=true",
            "spring.quartz.jdbc.initialize-schema=always",
            "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.PostgreSQLDelegate",
            "spring.quartz.properties.org.quartz.jobStore.tablePrefix=QRTZ_",
            "spring.quartz.properties.org.quartz.threadPool.threadCount=2"
        })
class QuartzJdbcSimpleTriggerIT extends PostgresContainerSupport {

    @Autowired
    Scheduler scheduler;

    @AfterEach
    void cleanup() throws SchedulerException {
        scheduler.clear();
        ProbeJob.reset();
    }

    @Test
    void simpleTriggerShouldFireAtLeastOnceOnJdbcJobStore() throws Exception {
        ProbeJob.reset();
        JobKey jobKey = JobKey.jobKey("it-simple-job", "it");
        TriggerKey triggerKey = TriggerKey.triggerKey("it-simple-trigger", "it");

        JobDetail jobDetail = JobBuilder.newJob(ProbeJob.class)
                .withIdentity(jobKey)
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .startAt(java.util.Date.from(Instant.now().plusSeconds(2)))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(1)
                        .withRepeatCount(0))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        boolean fired = ProbeJob.LATCH.await(20, TimeUnit.SECONDS);

        assertTrue(fired, "SimpleTrigger should fire at least once on JDBC JobStore");
        assertTrue(ProbeJob.EXEC_COUNT.get() >= 1, "Execution count should be >= 1");
    }

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception ex) {
            return false;
        }
    }

    public static class ProbeJob implements Job {
        static final AtomicInteger EXEC_COUNT = new AtomicInteger(0);
        static volatile CountDownLatch LATCH = new CountDownLatch(1);

        static void reset() {
            EXEC_COUNT.set(0);
            LATCH = new CountDownLatch(1);
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            EXEC_COUNT.incrementAndGet();
            LATCH.countDown();
        }
    }
}
