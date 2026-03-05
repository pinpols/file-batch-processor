-- ========================================================
-- V1_16: orchestration governance hardening
-- 1) dependency rules (timeout + on-failure action)
-- 2) task state machine extension (PARTIAL/SKIPPED)
-- 3) DLQ compensation channel fields
-- ========================================================

ALTER TABLE task_dependency
    ADD COLUMN IF NOT EXISTS dependency_timeout_ms BIGINT,
    ADD COLUMN IF NOT EXISTS on_failure_action VARCHAR(16) DEFAULT 'FAIL';

ALTER TABLE task_dependency
    ADD CONSTRAINT ck_task_dependency_timeout_positive
    CHECK (dependency_timeout_ms IS NULL OR dependency_timeout_ms > 0) NOT VALID;

ALTER TABLE task_dependency
    ADD CONSTRAINT ck_task_dependency_failure_action
    CHECK (on_failure_action IN ('FAIL', 'SKIP', 'IGNORE')) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_task_dependency_failure_action
    ON task_dependency(on_failure_action);

ALTER TABLE task_definition
    ADD COLUMN IF NOT EXISTS timeout_ms BIGINT,
    ADD COLUMN IF NOT EXISTS max_queue_wait_ms BIGINT,
    ADD COLUMN IF NOT EXISTS dependency_timeout_ms BIGINT,
    ADD COLUMN IF NOT EXISTS rerun_window_ms BIGINT,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER,
    ADD COLUMN IF NOT EXISTS retry_backoff_ms BIGINT,
    ADD COLUMN IF NOT EXISTS dynamic_shard_max INTEGER;

ALTER TABLE task_execution_state
    DROP CONSTRAINT IF EXISTS ck_task_execution_state_status_allowed;

ALTER TABLE task_execution_state
    ADD CONSTRAINT ck_task_execution_state_status_allowed
    CHECK (status IN ('READY','RUNNING','SUCCESS','FAILED','BLOCKED','PARTIAL','SKIPPED')) NOT VALID;

ALTER TABLE dlq_records
    ADD COLUMN IF NOT EXISTS retryable BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS manual_required BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS compensation_status VARCHAR(32) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

UPDATE dlq_records
SET retryable = COALESCE(retryable, TRUE),
    manual_required = COALESCE(manual_required, FALSE),
    compensation_status = COALESCE(compensation_status, 'PENDING'),
    next_retry_at = COALESCE(next_retry_at, created_at)
WHERE handled = FALSE;

ALTER TABLE dlq_records
    ADD CONSTRAINT ck_dlq_compensation_status
    CHECK (compensation_status IN ('PENDING', 'RETRY_PENDING', 'REPLAYED', 'MANUAL_REQUIRED')) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_dlq_retry_window
    ON dlq_records(handled, manual_required, retryable, next_retry_at);
