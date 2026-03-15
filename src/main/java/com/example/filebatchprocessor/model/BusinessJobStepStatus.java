package com.example.filebatchprocessor.model;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.step.StepExecution;

public enum BusinessJobStepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED;

    public static BusinessJobStepStatus fromSpringBatch(StepExecution stepExecution) {
        BatchStatus status = stepExecution.getStatus();
        if (status == null) {
            return FAILED;
        }
        if (status == BatchStatus.FAILED) {
            return FAILED;
        }
        if (status == BatchStatus.STOPPED) {
            return CANCELLED;
        }
        if (status == BatchStatus.STARTED || status == BatchStatus.STARTING || status == BatchStatus.STOPPING) {
            return RUNNING;
        }
        if (stepExecution.getExitStatus() != null && "NOOP".equalsIgnoreCase(stepExecution.getExitStatus().getExitCode())) {
            return SKIPPED;
        }
        return COMPLETED;
    }
}
