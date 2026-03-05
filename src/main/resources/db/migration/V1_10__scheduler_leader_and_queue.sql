-- ========================================================
-- V1_10: Scheduler leader lease + scheduler queue records
-- Purpose: multi-instance safety for orchestration scheduler
-- ========================================================

CREATE TABLE IF NOT EXISTS scheduler_leader_lock (
    lock_name VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheduler_leader_expires
    ON scheduler_leader_lock(expires_at);


CREATE TABLE IF NOT EXISTS scheduler_queue_records (
    run_key VARCHAR(256) PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    batch_date VARCHAR(32) NOT NULL,
    rerun_id VARCHAR(128) NOT NULL DEFAULT '',
    enqueued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheduler_queue_task
    ON scheduler_queue_records(task_id, batch_date, rerun_id);

CREATE INDEX IF NOT EXISTS idx_scheduler_queue_enqueued
    ON scheduler_queue_records(enqueued_at);
