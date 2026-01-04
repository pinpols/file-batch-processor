package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.scheduler.LocalCacheService;
import com.example.filebatchprocessor.scheduler.TaskDefinition;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;

@Slf4j
@Service
public class TaskSchedulerService {

    private final JobOperator jobOperator;
    private final ObjectProvider<Map<String, Job>> jobsProvider;
    private final TaskGraphManager taskGraphManager;
    private final LocalCacheService localCacheService;
    private final TaskMergeService taskMergeService;
    private final TaskScheduler taskScheduler;
    private final ThreadPoolTaskExecutor batchTaskExecutor;

    private final ConcurrentMap<String, Instant> dedupMap = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<TaskDefinition> queue =
            new PriorityBlockingQueue<>(11, (a, b) -> Integer.compare(b.getPriority().weight(), a.getPriority().weight()));

    public TaskSchedulerService(@Qualifier("asyncJobLauncher") JobOperator jobOperator,
                                ObjectProvider<Map<String, Job>> jobsProvider,
                                TaskGraphManager taskGraphManager,
                                LocalCacheService localCacheService,
                                TaskMergeService taskMergeService,
                                TaskScheduler taskScheduler,
                                ThreadPoolTaskExecutor batchTaskExecutor) {
        this.jobOperator = jobOperator;
        this.jobsProvider = jobsProvider;
        this.taskGraphManager = taskGraphManager;
        this.localCacheService = localCacheService;
        this.taskMergeService = taskMergeService;
        this.taskScheduler = taskScheduler;
        this.batchTaskExecutor = batchTaskExecutor;
    }

    public void register(TaskDefinition definition) {
        taskGraphManager.register(definition);
        schedule(definition);
    }

    private void schedule(TaskDefinition definition) {
        if (definition.getTrigger() == null) {
            queue.add(definition);
            return;
        }
        switch (definition.getTrigger().getType()) {
            case CRON:
                taskScheduler.schedule(() -> enqueue(definition), new CronTrigger(definition.getTrigger().getCron()));
                break;
            case FIXED_RATE:
                taskScheduler.scheduleAtFixedRate(() -> enqueue(definition), definition.getTrigger().getFixedRateMs());
                break;
            case FIXED_DELAY:
                taskScheduler.scheduleWithFixedDelay(() -> enqueue(definition), definition.getTrigger().getFixedDelayMs());
                break;
            case ONE_TIME:
                taskScheduler.schedule(() -> enqueue(definition), Date.from(definition.getTrigger().getOneTimeAt()));
                break;
            default:
                queue.add(definition);
        }
    }

    private void enqueue(TaskDefinition definition) {
        queue.add(definition);
        batchTaskExecutor.submit(this::drainQueue);
    }

    private void drainQueue() {
        TaskDefinition def;
        while ((def = queue.poll()) != null) {
            if (!dependenciesSatisfied(def)) {
                // Requeue to wait for dependencies
                queue.add(def);
                continue;
            }
            if (isDuplicate(def)) {
                log.info("Skip duplicate task {}", def.getId());
                continue;
            }
            String mergeKey = buildMergeKey(def);
            List<TaskDefinition> merged = taskMergeService.merge(mergeKey, def, Duration.ofSeconds(10));
            for (TaskDefinition task : merged) {
                launchJob(task);
            }
        }
    }

    private boolean dependenciesSatisfied(TaskDefinition def) {
        String batchDate = def.getParameters().getOrDefault("batchDate", "default");
        for (String dep : def.getDependencies()) {
            Object status = localCacheService.get("task:" + dep + ":" + batchDate + ":completed");
            if (!(status instanceof Boolean) || !(Boolean) status) {
                return false;
            }
        }
        return true;
    }

    private boolean isDuplicate(TaskDefinition def) {
        String batchDate = def.getParameters().getOrDefault("batchDate", "default");
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        String key = Objects.requireNonNullElse(def.getDedupKey(), def.getId()) + ":" + batchDate + ":" + rerunId;
        Instant last = dedupMap.get(key);
        Instant now = Instant.now();
        if (last != null && Duration.between(last, now).getSeconds() < 30) {
            return true;
        }
        dedupMap.put(key, now);
        return false;
    }

    private void launchJob(TaskDefinition def) {
        Map<String, Job> jobs = jobsProvider.getIfAvailable();
        if (jobs == null || !jobs.containsKey(def.getJobName())) {
            log.warn("No job found for name {}", def.getJobName());
            return;
        }
        Job job = jobs.get(def.getJobName());
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("task.id", def.getId())
                .addLong("priority", (long) def.getPriority().weight());

        if (def.getShardIndex() != null) {
            builder.addLong("shard.index", def.getShardIndex().longValue());
        }
        if (def.getShardTotal() != null) {
            builder.addLong("shard.total", def.getShardTotal().longValue());
        }
        def.getParameters().forEach(builder::addString);
        try {
            String jobName = job.getName();
            String parameters = convertJobParametersToString(builder.toJobParameters());
            Long executionId = jobOperator.start(jobName, parameters);
            String batchDate = def.getParameters().getOrDefault("batchDate", "default");
            localCacheService.put("task:" + def.getId() + ":" + batchDate + ":completed", true, Duration.ofMinutes(10));
            log.info("Started job {} with executionId {}", jobName, executionId);
        } catch (Exception e) {
            log.error("Failed to launch job {} for task {}", def.getJobName(), def.getId(), e);
        }
    }

    private String buildMergeKey(TaskDefinition def) {
        String batchDate = def.getParameters().getOrDefault("batchDate", "default");
        String rerunId = def.getParameters().getOrDefault("rerunId", "");
        return def.getJobName() + ":" + batchDate + ":" + rerunId;
    }
}

