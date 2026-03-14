-- Flyway callback: bootstrap historically-missing base tables for clean schema migration.
-- This runs before versioned migrations and keeps old versioned files immutable.

CREATE TABLE IF NOT EXISTS batch_run_records (
    id BIGSERIAL PRIMARY KEY,
    job_execution_id BIGINT,
    job_name VARCHAR(128) NOT NULL,
    batch_date VARCHAR(32),
    tenant_id VARCHAR(64),
    biz_domain VARCHAR(64),
    job_params VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    exit_code INTEGER,
    exit_description VARCHAR(1000),
    read_count BIGINT DEFAULT 0,
    write_count BIGINT DEFAULT 0,
    commit_count BIGINT DEFAULT 0,
    rollback_count BIGINT DEFAULT 0,
    skip_count BIGINT DEFAULT 0,
    parse_error_count BIGINT DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,
    throughput_rps DOUBLE PRECISION DEFAULT 0,
    retry_count BIGINT DEFAULT 0,
    quality_passed BOOLEAN DEFAULT TRUE,
    quality_message VARCHAR(500),
    error_message VARCHAR(1000),
    replay_count BIGINT DEFAULT 0,
    last_replay_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS imported_records (
    id BIGSERIAL PRIMARY KEY,
    business_key VARCHAR(200) NOT NULL,
    name VARCHAR(200),
    description VARCHAR(500),
    batch_date VARCHAR(20) NOT NULL,
    source_file_name VARCHAR(1024),
    line_no BIGINT,
    raw_data TEXT,
    parsed_data TEXT,
    status VARCHAR(20),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_import_biz_batch UNIQUE (business_key, batch_date)
);

CREATE TABLE IF NOT EXISTS imported_records_partition (
    id BIGSERIAL PRIMARY KEY,
    business_key VARCHAR(200) NOT NULL,
    name VARCHAR(200),
    description VARCHAR(500),
    batch_date VARCHAR(20) NOT NULL,
    partition_key VARCHAR(64) NOT NULL,
    checksum VARCHAR(64),
    source_file_name VARCHAR(1024),
    line_no BIGINT,
    raw_data TEXT,
    parsed_data TEXT,
    status VARCHAR(20),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_import_biz_batch_part UNIQUE (business_key, batch_date, partition_key)
);

CREATE TABLE IF NOT EXISTS file_distribution_task (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    export_file_id BIGINT,
    source_file_path VARCHAR(1024),
    file_name VARCHAR(500),
    file_path VARCHAR(1000),
    file_size BIGINT,
    file_hash VARCHAR(128),
    target_system VARCHAR(128) NOT NULL,
    target_path VARCHAR(1024),
    target_address VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    max_retries INTEGER DEFAULT 3,
    retry_count INTEGER DEFAULT 0,
    retry_interval_seconds BIGINT DEFAULT 300,
    last_attempt_time TIMESTAMP,
    completed_time TIMESTAMP,
    error_message VARCHAR(1000),
    tenant_id VARCHAR(64),
    biz_domain VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS file_reception_queue (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT,
    file_hash VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    source_system VARCHAR(100),
    expected_process_time TIMESTAMP,
    received_at TIMESTAMP,
    processed_at TIMESTAMP,
    wait_reason VARCHAR(500),
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS file_data (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(512),
    file_path VARCHAR(1024),
    status VARCHAR(32),
    process_time TIMESTAMP,
    content VARCHAR(5000),
    file_size BIGINT,
    file_type VARCHAR(64),
    content_hash VARCHAR(128),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_records (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    job_name VARCHAR(128),
    batch_date VARCHAR(32),
    business_key VARCHAR(200),
    params VARCHAR(2000),
    raw_data TEXT,
    error_message VARCHAR(1000) NOT NULL,
    error_code VARCHAR(64),
    retry_count INTEGER DEFAULT 0,
    status VARCHAR(20),
    handled BOOLEAN DEFAULT FALSE,
    handled_at TIMESTAMP,
    replay_count BIGINT DEFAULT 0,
    last_replay_error VARCHAR(1000),
    retryable BOOLEAN DEFAULT TRUE,
    manual_required BOOLEAN DEFAULT FALSE,
    compensation_status VARCHAR(32) DEFAULT 'PENDING',
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_log_records (
    id BIGSERIAL PRIMARY KEY,
    job_execution_id BIGINT,
    job_name VARCHAR(128),
    step_name VARCHAR(128),
    log_level VARCHAR(16),
    log_message TEXT,
    log_timestamp TIMESTAMP,
    params VARCHAR(2000),
    status VARCHAR(64),
    message VARCHAR(1000),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_execution_state (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    batch_date VARCHAR(32) NOT NULL,
    rerun_id VARCHAR(128) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    attempt INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 1,
    next_retry_at TIMESTAMP,
    window_start TIMESTAMP,
    window_end TIMESTAMP,
    last_error VARCHAR(1000),
    error_message VARCHAR(1000),
    error_code VARCHAR(64),
    tenant_id VARCHAR(64),
    biz_domain VARCHAR(64),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_batch_run_job_execution_id
    ON batch_run_records (job_execution_id);

CREATE INDEX IF NOT EXISTS idx_file_dist_status
    ON file_distribution_task (status);

CREATE INDEX IF NOT EXISTS idx_file_reception_status
    ON file_reception_queue (status);

CREATE INDEX IF NOT EXISTS idx_file_reception_created_at
    ON file_reception_queue (created_at);
