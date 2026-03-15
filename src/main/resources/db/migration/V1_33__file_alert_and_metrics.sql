-- Phase 9: Alert, Monitoring, and Archiving capabilities

CREATE TABLE IF NOT EXISTS file_alert_log (
    id BIGSERIAL PRIMARY KEY,
    alert_code VARCHAR(64) NOT NULL,
    alert_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000),
    file_record_id BIGINT,
    task_id VARCHAR(128),
    biz_date VARCHAR(32),
    source_system VARCHAR(100),
    target_system VARCHAR(100),
    payload JSONB,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by VARCHAR(128),
    acknowledged_at TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by VARCHAR(128),
    resolved_at TIMESTAMP,
    resolution_notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_alert_log_code ON file_alert_log(alert_code);
CREATE INDEX IF NOT EXISTS idx_file_alert_log_file ON file_alert_log(file_record_id);
CREATE INDEX IF NOT EXISTS idx_file_alert_log_severity ON file_alert_log(severity);
CREATE INDEX IF NOT EXISTS idx_file_alert_log_acknowledged ON file_alert_log(acknowledged);
CREATE INDEX IF NOT EXISTS idx_file_alert_log_resolved ON file_alert_log(resolved);
CREATE INDEX IF NOT EXISTS idx_file_alert_log_created ON file_alert_log(created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_alert_log_severity'
    ) THEN
        ALTER TABLE file_alert_log
            ADD CONSTRAINT ck_file_alert_log_severity
            CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_alert_log_type'
    ) THEN
        ALTER TABLE file_alert_log
            ADD CONSTRAINT ck_file_alert_log_type
            CHECK (alert_type IN ('FILE_TIMEOUT', 'FILE_UNPROCESSED', 'DISPATCH_NO_ACK', 
                               'DISK_SPACE', 'DIRECTORY_BACKLOG', 'ARCHIVE_FAILURE', 
                               'METRICS_THRESHOLD'));
    END IF;
END $$;

COMMENT ON TABLE file_alert_log IS 'File-related alert and warning log for monitoring';

CREATE TABLE IF NOT EXISTS file_metrics_snapshot (
    id BIGSERIAL PRIMARY KEY,
    snapshot_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metric_date DATE NOT NULL,
    
    received_count BIGINT NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    processing_count BIGINT NOT NULL DEFAULT 0,
    pending_count BIGINT NOT NULL DEFAULT 0,
    
    avg_processing_duration_sec DOUBLE PRECISION,
    max_processing_duration_sec DOUBLE PRECISION,
    min_processing_duration_sec DOUBLE PRECISION,
    
    dispatch_count BIGINT NOT NULL DEFAULT 0,
    dispatch_success_count BIGINT NOT NULL DEFAULT 0,
    dispatch_failed_count BIGINT NOT NULL DEFAULT 0,
    dispatch_pending_count BIGINT NOT NULL DEFAULT 0,
    ack_timeout_count BIGINT NOT NULL DEFAULT 0,
    
    archive_count BIGINT NOT NULL DEFAULT 0,
    archive_failed_count BIGINT NOT NULL DEFAULT 0,
    
    dlq_count BIGINT NOT NULL DEFAULT 0,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_metrics_snapshot_date ON file_metrics_snapshot(metric_date);
CREATE INDEX IF NOT idx_file_metrics_snapshot_time ON file_metrics_snapshot(snapshot_time);

COMMENT ON TABLE file_metrics_snapshot IS 'Periodic metrics snapshot for file processing monitoring';

CREATE TABLE IF NOT EXISTS file_retention_policy (
    id BIGSERIAL PRIMARY KEY,
    policy_name VARCHAR(64) NOT NULL,
    file_category VARCHAR(32) NOT NULL,
    retention_days INTEGER NOT NULL,
    archive_before_delete BOOLEAN NOT NULL DEFAULT TRUE,
    check_dependency_before_archive BOOLEAN NOT NULL DEFAULT TRUE,
    allow_manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_retention_policy_category 
    ON file_retention_policy(file_category);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_retention_policy_category'
    ) THEN
        ALTER TABLE file_retention_policy
            ADD CONSTRAINT ck_file_retention_policy_category
            CHECK (file_category IN ('INBOUND', 'OUTBOUND', 'INTERNAL', 'TEMP', 'ARCHIVE'));
    END IF;
END $$;

COMMENT ON TABLE file_retention_policy IS 'File retention and cleanup policy configuration';

INSERT INTO file_retention_policy (policy_name, file_category, retention_days, archive_before_delete, check_dependency_before_archive, description)
VALUES 
    ('inbound_retention', 'INBOUND', 90, TRUE, TRUE, 'Retain inbound files for 90 days'),
    ('outbound_retention', 'OUTBOUND', 180, TRUE, TRUE, 'Retain outbound files for 180 days'),
    ('temp_file_cleanup', 'TEMP', 7, FALSE, FALSE, 'Clean up temp files after 7 days'),
    ('internal_retention', 'INTERNAL', 365, TRUE, TRUE, 'Retain internal files for 1 year')
ON CONFLICT (file_category) DO NOTHING;
