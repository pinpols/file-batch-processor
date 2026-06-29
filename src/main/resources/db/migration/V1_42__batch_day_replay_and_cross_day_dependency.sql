-- V1_42: 单体批量日治理 + 账期级 replay + 跨日依赖表达

ALTER TABLE task_dependency
    ADD COLUMN IF NOT EXISTS dependency_batch_date_offset_days INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS dependency_calendar_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_task_dependency_batch_offset
    ON task_dependency(dependency_batch_date_offset_days);

CREATE TABLE IF NOT EXISTS batch_day_instance (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    calendar_code VARCHAR(64) NOT NULL,
    biz_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    frozen_at TIMESTAMP,
    settled_at TIMESTAMP,
    last_replay_session_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_batch_day_instance UNIQUE (tenant_id, calendar_code, biz_date),
    CONSTRAINT ck_batch_day_status CHECK (
        status IN ('OPEN','FROZEN','SETTLING','SETTLED','REPLAYING','CLOSED')
    )
);

CREATE INDEX IF NOT EXISTS idx_batch_day_status
    ON batch_day_instance(status, biz_date);

CREATE TABLE IF NOT EXISTS batch_day_replay_session (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    calendar_code VARCHAR(64) NOT NULL,
    biz_date DATE NOT NULL,
    scope VARCHAR(32) NOT NULL,
    scope_payload JSONB,
    status VARCHAR(32) NOT NULL,
    total_count INTEGER NOT NULL DEFAULT 0,
    succeeded_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    in_flight_count INTEGER NOT NULL DEFAULT 0,
    reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_batch_day_replay_scope CHECK (scope IN ('ALL','ALL_FAILED','SUBSET_TASK_IDS')),
    CONSTRAINT ck_batch_day_replay_status CHECK (status IN ('RUNNING','SUCCEEDED','PARTIAL_FAILED','CANCELLED')),
    CONSTRAINT ck_batch_day_replay_counts CHECK (
        total_count >= 0 AND succeeded_count >= 0 AND failed_count >= 0 AND in_flight_count >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_batch_day_replay_active
    ON batch_day_replay_session(tenant_id, calendar_code, biz_date)
    WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS idx_batch_day_replay_recent
    ON batch_day_replay_session(created_at DESC);

CREATE TABLE IF NOT EXISTS batch_day_replay_entry (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES batch_day_replay_session(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    job_name VARCHAR(128),
    source_instance_id BIGINT,
    rerun_id VARCHAR(128),
    run_key VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_batch_day_replay_entry_status CHECK (
        status IN ('PENDING','ENQUEUED','SUCCEEDED','FAILED','SKIPPED')
    )
);

CREATE INDEX IF NOT EXISTS idx_batch_day_replay_entry_session
    ON batch_day_replay_entry(session_id, id);

CREATE INDEX IF NOT EXISTS idx_batch_day_replay_entry_task
    ON batch_day_replay_entry(task_id, status);
