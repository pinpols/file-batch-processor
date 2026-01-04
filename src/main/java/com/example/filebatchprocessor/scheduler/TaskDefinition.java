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
public class TaskDefinition {
    private String id;
    private String jobName;
    private TaskTrigger trigger;
    @Builder.Default
    private TaskPriority priority = TaskPriority.NORMAL;
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();
    private String dedupKey;
    private boolean allowParallel;
    private boolean allowMerge;
    private Integer shardIndex;
    private Integer shardTotal;
}

