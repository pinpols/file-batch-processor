package com.example.filebatchprocessor.model;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

public enum BusinessJobInstanceStatus {
    PENDING,
    TRIGGERED,
    RUNNING,
    PARTIAL_SUCCESS,
    COMPLETED,
    FAILED,
    RETRY_PENDING,
    RERUN_PENDING,
    CANCELLED,
    TIMEOUT;

    public static BusinessJobInstanceStatus fromSpringBatch(JobExecution execution) {
        BatchStatus status = execution.getStatus();
        if (status == null) {
            return FAILED;
        }
        if (status == BatchStatus.FAILED) {
            return FAILED;
        }
        if (status == BatchStatus.STOPPED) {
            return CANCELLED;
        }
        if (status == BatchStatus.ABANDONED) {
            return FAILED;
        }
        if (status == BatchStatus.STARTED || status == BatchStatus.STARTING || status == BatchStatus.STOPPING) {
            return RUNNING;
        }
        boolean hasSkips = execution.getStepExecutions().stream().anyMatch(BusinessJobInstanceStatus::hasSkips);
        return hasSkips ? PARTIAL_SUCCESS : COMPLETED;
    }

    private static boolean hasSkips(StepExecution stepExecution) {
        return stepExecution.getSkipCount() > 0
                || stepExecution.getProcessSkipCount() > 0
                || stepExecution.getReadSkipCount() > 0
                || stepExecution.getWriteSkipCount() > 0;
    }
}
