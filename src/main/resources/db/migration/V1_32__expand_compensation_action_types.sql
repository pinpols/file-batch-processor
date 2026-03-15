ALTER TABLE compensation_record
    DROP CONSTRAINT IF EXISTS ck_compensation_record_action_type;

ALTER TABLE compensation_record
    ADD CONSTRAINT ck_compensation_record_action_type
    CHECK (action_type IN (
        'JOB_RESTART',
        'JOB_RETRY',
        'STEP_RETRY',
        'FILE_RETRY',
        'DISPATCH_RESEND',
        'DISPATCH_ACK_TIMEOUT',
        'BATCH_RERUN',
        'DLQ_REPLAY'
    ));
