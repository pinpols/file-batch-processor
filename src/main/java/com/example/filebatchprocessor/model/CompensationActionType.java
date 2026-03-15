package com.example.filebatchprocessor.model;

public enum CompensationActionType {
    JOB_RESTART,
    JOB_RETRY,
    STEP_RETRY,
    FILE_RETRY,
    DISPATCH_RESEND,
    DISPATCH_ACK_TIMEOUT,
    BATCH_RERUN,
    DLQ_REPLAY
}
