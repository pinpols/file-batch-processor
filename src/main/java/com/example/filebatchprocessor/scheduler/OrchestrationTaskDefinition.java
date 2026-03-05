package com.example.filebatchprocessor.scheduler;

import com.example.filebatchprocessor.batch.scheduler.TaskPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationTaskDefinition {
    private String id;
    private String jobName;
    private String tenantId;
    private String bizDomain;
    private String env;
    private OrchestrationTaskTrigger trigger;
    @Builder.Default
    private TaskPriority priority = TaskPriority.NORMAL;
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();
    @Builder.Default
    private Map<String, Long> dependencyTimeoutByTask = new HashMap<>();
    @Builder.Default
    private Map<String, String> dependencyFailureActionByTask = new HashMap<>();
    private String dedupKey;
    private boolean allowParallel;
    private boolean allowMerge;
    private Long slaMaxDurationMs;
    private Long slaMaxQueueDelayMs;
    private Integer rateLimitPerMinute;
    private Integer shardIndex;
    private Integer shardTotal;
    private Long timeoutMs;
    private Long maxQueueWaitMs;
    private Integer dynamicShardMax;
    private Long dependencyTimeoutMs;
    private Long rerunWindowMs;
    private Integer maxAttempts;
    private Long retryBackoffMs;
}

