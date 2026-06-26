package com.example.filebatchprocessor.util;

import com.example.filebatchprocessor.scheduler.OrchestrationTaskDefinition;

/**
 * Build normalized idempotency keys for cross-instance deduplication.
 */
public final class IdempotencyKeyBuilder {

    private IdempotencyKeyBuilder() {}

    public static String forTask(OrchestrationTaskDefinition task, String batchDate, String rerunId) {
        String biz = task.getDedupKey() == null || task.getDedupKey().isBlank() ? task.getId() : task.getDedupKey();
        String shard = String.format(
                "%s/%s",
                task.getShardIndex() == null ? "*" : task.getShardIndex(),
                task.getShardTotal() == null ? "*" : task.getShardTotal());
        return String.join("|", safe(task.getJobName()), safe(biz), safe(batchDate), safe(rerunId), shard);
    }

    public static String forImportRequest(
            String inputFile, String batchDate, String rerunId, int shardIndex, int shardTotal) {
        return String.join(
                "|", "processFileJob", safe(inputFile), safe(batchDate), safe(rerunId), shardIndex + "/" + shardTotal);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
