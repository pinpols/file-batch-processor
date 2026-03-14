package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.scheduler.OrchestrationTaskTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "orchestration.enabled=true",
        "orchestration.scheduler.force-leader=true"
})
class QuartzSchedulerIT {

    @Autowired
    TaskSchedulerService taskSchedulerService;

    @Autowired
    Scheduler quartzScheduler;

    @BeforeEach
    void cleanup() throws SchedulerException {
        quartzScheduler.clear();
    }

    @Test
    void cronTriggerRegistersOnQuartz() throws SchedulerException {
        OrchestrationTaskDefinition def = buildDefinition("cron-task", TriggerType.CRON, "0/5 * * * * ?");
        taskSchedulerService.register(def);
        TriggerKey key = triggerKey(def, "cron");
        assertTrue(quartzScheduler.checkExists(key), "Cron trigger should exist");
    }

    @Test
    void fixedDelaySchedulesOneShotTrigger() throws SchedulerException {
        OrchestrationTaskDefinition def = buildDefinition("fixed-delay-task", TriggerType.FIXED_DELAY, null);
        Instant next = Instant.now().plusMillis(500);
        taskSchedulerService.scheduleFixedDelayOnce(def, next);
        JobKey jobKey = jobKey(def);
        List<? extends Trigger> triggers = quartzScheduler.getTriggersOfJob(jobKey);
        assertTrue(triggers.stream().anyMatch(t -> t instanceof SimpleTrigger), "Should have scheduled a simple trigger for fixed delay");
    }

    @Test
    void cronTriggerHasFireAndProceedMisfireInstruction() throws SchedulerException {
        OrchestrationTaskDefinition def = buildDefinition("misfire-task", TriggerType.CRON, "0 0/1 * * * ?");
        taskSchedulerService.register(def);
        TriggerKey key = triggerKey(def, "cron");
        CronTrigger trigger = (CronTrigger) quartzScheduler.getTrigger(key);
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW, trigger.getMisfireInstruction());
    }

    private OrchestrationTaskDefinition buildDefinition(String id, TriggerType type, String cron) {
        OrchestrationTaskTrigger trigger = new OrchestrationTaskTrigger();
        trigger.setType(type);
        trigger.setCron(cron);
        trigger.setFixedDelayMs(1000L);
        return OrchestrationTaskDefinition.builder()
                .id(id)
                .jobName("testJob")
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
