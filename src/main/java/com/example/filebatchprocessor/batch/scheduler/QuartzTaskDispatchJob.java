package com.example.filebatchprocessor.batch.scheduler;

import com.example.filebatchprocessor.config.SpringContextHolder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class QuartzTaskDispatchJob implements Job {

    public static final String TASK_ID = "taskId";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String taskId = context.getMergedJobDataMap().getString(TASK_ID);
        if (taskId == null || taskId.isBlank()) {
            throw new JobExecutionException("Missing taskId in JobDataMap");
        }
        TaskSchedulerService taskSchedulerService = SpringContextHolder.getBean(TaskSchedulerService.class);
        taskSchedulerService.enqueueByTaskId(taskId);
    }
}
