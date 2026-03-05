-- ========================================================
-- V1_19: 运维 P0 能力 - 变更管理 + 审计追踪
-- ========================================================

CREATE TABLE IF NOT EXISTS ops_change_request (
    id BIGSERIAL PRIMARY KEY,
    request_no VARCHAR(64) NOT NULL UNIQUE,
    target_type VARCHAR(32) NOT NULL,
    task_id VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    applied_by VARCHAR(100),
    status VARCHAR(32) NOT NULL,
    reject_reason VARCHAR(1000),
    approved_at TIMESTAMP,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_change_request_status
    ON ops_change_request(status);

CREATE INDEX IF NOT EXISTS idx_ops_change_request_task_id
    ON ops_change_request(task_id);

ALTER TABLE ops_change_request
    ADD CONSTRAINT ck_ops_change_request_status
        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'APPLIED')) NOT VALID;

ALTER TABLE ops_change_request
    ADD CONSTRAINT ck_ops_change_request_target
        CHECK (target_type IN ('TASK_DEFINITION', 'TASK_TRIGGER', 'TASK_PARAMETER')) NOT VALID;

CREATE TABLE IF NOT EXISTS ops_audit_log (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    result VARCHAR(16) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_audit_log_created_at
    ON ops_audit_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ops_audit_log_action
    ON ops_audit_log(action);

