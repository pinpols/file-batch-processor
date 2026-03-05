package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueManagerTest {

    @Test
    void shouldRejectWhenQueueFull() {
        QueueManager manager = new QueueManager(1);
        for (int i = 0; i < 10; i++) {
            OrchestrationTaskDefinition task = new OrchestrationTaskDefinition();
            task.setId("t" + i);
            assertTrue(manager.offer(task, "k" + i));
        }
        OrchestrationTaskDefinition taskOverflow = new OrchestrationTaskDefinition();
        taskOverflow.setId("overflow");
        assertFalse(manager.offer(taskOverflow, "overflow"));
    }

    @Test
    void shouldDetectQueueWaitTimeout() throws Exception {
        QueueManager manager = new QueueManager(10);
        OrchestrationTaskDefinition task = new OrchestrationTaskDefinition();
        task.setId("t1");
        assertTrue(manager.offer(task, "k1"));

        Thread.sleep(1100);
        assertTrue(manager.isQueueWaitTimeout("k1", 1000));
        assertFalse(manager.isQueueWaitTimeout("k1", 60_000));
    }

    @Test
    void shouldPollAndRemoveRunKey() {
        QueueManager manager = new QueueManager(10);
        OrchestrationTaskDefinition task = new OrchestrationTaskDefinition();
        task.setId("t1");
        assertTrue(manager.offer(task, "k1"));
        assertNotNull(manager.enqueuedAt("k1"));

        assertEquals(task, manager.poll());
        manager.removeRunKey("k1");
        assertNull(manager.enqueuedAt("k1"));
    }
}
