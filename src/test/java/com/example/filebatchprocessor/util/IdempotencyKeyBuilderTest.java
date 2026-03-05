package com.example.filebatchprocessor.util;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyKeyBuilderTest {

    @Test
    void shouldBuildStableTaskKey() {
        OrchestrationTaskDefinition task = OrchestrationTaskDefinition.builder()
                .id("process-file-main")
                .jobName("processFileJob")
                .dedupKey("biz-key")
                .shardIndex(0)
                .shardTotal(2)
                .build();

        String key = IdempotencyKeyBuilder.forTask(task, "2026-03-04", "r1");
        assertEquals("processFileJob|biz-key|2026-03-04|r1|0/2", key);
    }
}
