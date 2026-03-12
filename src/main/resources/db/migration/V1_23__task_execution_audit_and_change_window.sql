-- ========================================================
-- V1_23: Task execution audit + ops change window/impact
-- ========================================================

CREATE TABLE IF NOT EXISTS task_execution_audit (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    job_name VARCHAR(128) NOT NULL,
    batch_date VARCHAR(32),
    run_key VARCHAR(200),
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32),
    params TEXT,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_execution_audit_task_created
    ON task_execution_audit(task_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_execution_audit_event
    ON task_execution_audit(event_type, created_at DESC);

-- ALTER TABLE ops_change_request
--     ADD COLUMN IF NOT EXISTS window_start TIMESTAMP,
--     ADD COLUMN IF NOT EXISTS window_end TIMESTAMP,
--     ADD COLUMN IF NOT EXISTS impact_summary TEXT,
--     ADD COLUMN IF NOT EXISTS risk_level VARCHAR(32),
--     ADD COLUMN IF NOT EXISTS rollback_plan TEXT;
