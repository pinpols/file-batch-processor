package com.example.filebatchprocessor.unit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;
import com.example.filebatchprocessor.util.IdempotencyKeyBuilder;
import org.junit.jupiter.api.Test;

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
