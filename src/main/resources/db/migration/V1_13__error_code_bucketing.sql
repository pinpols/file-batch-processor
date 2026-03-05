ALTER TABLE dlq_records
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_dlq_records_error_code
    ON dlq_records (error_code);

ALTER TABLE task_execution_state
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_task_exec_state_error_code
    ON task_execution_state (error_code);
