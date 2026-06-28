-- 第二阶段：文件资产领域模型
-- 1. 新增面向业务的受管文件表
-- 2. 保留旧 file_reception_queue / file_distribution_task 表，支持灰度迁移
-- 3. 增加桥接列，用于双写和读路径切换

CREATE TABLE IF NOT EXISTS file_record (
    id BIGSERIAL PRIMARY KEY,
    file_no VARCHAR(64) NOT NULL,
    source_system VARCHAR(100),
    biz_type VARCHAR(64),
    file_direction VARCHAR(16) NOT NULL DEFAULT 'INBOUND',
    original_name VARCHAR(500) NOT NULL,
    stored_name VARCHAR(500) NOT NULL,
    stored_path VARCHAR(1000) NOT NULL,
    storage_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    file_size BIGINT NOT NULL DEFAULT 0,
    file_hash VARCHAR(128),
    hash_algorithm VARCHAR(32) NOT NULL DEFAULT 'MD5',
    integrity_verified BOOLEAN NOT NULL DEFAULT FALSE,
    file_ext VARCHAR(32),
    mime_type VARCHAR(128),
    charset VARCHAR(32),
    biz_date VARCHAR(32),
    batch_no VARCHAR(64),
    tenant_id VARCHAR(64),
    biz_domain VARCHAR(64),
    parent_file_id BIGINT,
    version_no INTEGER NOT NULL DEFAULT 1,
    latest_version BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    archive_required BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    deletable BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    arrived_time TIMESTAMP,
    ready_time TIMESTAMP,
    processing_start_time TIMESTAMP,
    processed_time TIMESTAMP,
    archived_time TIMESTAMP,
    deleted_time TIMESTAMP,
    retention_until TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_record_file_no
    ON file_record(file_no);

CREATE INDEX IF NOT EXISTS idx_file_record_status
    ON file_record(status);

CREATE INDEX IF NOT EXISTS idx_file_record_source_biz_date
    ON file_record(source_system, biz_date);

CREATE INDEX IF NOT EXISTS idx_file_record_hash
    ON file_record(file_hash);

CREATE INDEX IF NOT EXISTS idx_file_record_parent
    ON file_record(parent_file_id);

CREATE INDEX IF NOT EXISTS idx_file_record_tenant_domain
    ON file_record(tenant_id, biz_domain);

CREATE INDEX IF NOT EXISTS idx_file_record_stored_path_latest
    ON file_record(stored_path, latest_version);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_record_direction'
    ) THEN
        ALTER TABLE file_record
            ADD CONSTRAINT ck_file_record_direction
            CHECK (file_direction IN ('INBOUND', 'OUTBOUND', 'INTERNAL'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_record_status'
    ) THEN
        ALTER TABLE file_record
            ADD CONSTRAINT ck_file_record_status
            CHECK (status IN (
                'UPLOADING',
                'ARRIVED',
                'READY',
                'PROCESSING',
                'PROCESSED',
                'FAILED',
                'DISPATCHING',
                'DISPATCHED',
                'ARCHIVED'
            ));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_record_size_non_negative'
    ) THEN
        ALTER TABLE file_record
            ADD CONSTRAINT ck_file_record_size_non_negative
            CHECK (file_size >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_record_version_positive'
    ) THEN
        ALTER TABLE file_record
            ADD CONSTRAINT ck_file_record_version_positive
            CHECK (version_no >= 1);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_file_record_parent'
    ) THEN
        ALTER TABLE file_record
            ADD CONSTRAINT fk_file_record_parent
            FOREIGN KEY (parent_file_id) REFERENCES file_record(id);
    END IF;
END $$;

COMMENT ON TABLE file_record IS 'Unified managed file asset table for inbound/outbound/internal files';
COMMENT ON COLUMN file_record.file_no IS 'Business-visible file identifier, e.g. FR-20260315-9A42C1D0';
COMMENT ON COLUMN file_record.file_direction IS 'INBOUND | OUTBOUND | INTERNAL';
COMMENT ON COLUMN file_record.integrity_verified IS 'True only after size/hash or protocol completeness checks pass';

CREATE TABLE IF NOT EXISTS file_process_log (
    id BIGSERIAL PRIMARY KEY,
    file_record_id BIGINT NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    status_from VARCHAR(32),
    status_to VARCHAR(32),
    result VARCHAR(32) NOT NULL,
    task_id VARCHAR(128),
    job_name VARCHAR(128),
    job_execution_id BIGINT,
    step_execution_id BIGINT,
    operator VARCHAR(128),
    run_key VARCHAR(200),
    retry_no INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    error_msg VARCHAR(1000),
    extra JSONB,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_process_log_file
    ON file_process_log(file_record_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_file_process_log_task
    ON file_process_log(task_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_file_process_log_result
    ON file_process_log(result, created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_file_process_log_file'
    ) THEN
        ALTER TABLE file_process_log
            ADD CONSTRAINT fk_file_process_log_file
            FOREIGN KEY (file_record_id) REFERENCES file_record(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_process_log_result'
    ) THEN
        ALTER TABLE file_process_log
            ADD CONSTRAINT ck_file_process_log_result
            CHECK (result IN ('SUCCESS', 'FAILED', 'SKIPPED'));
    END IF;
END $$;

COMMENT ON TABLE file_process_log IS 'Structured file lifecycle and processing audit log';

CREATE TABLE IF NOT EXISTS file_dispatch_record (
    id BIGSERIAL PRIMARY KEY,
    dispatch_no VARCHAR(64) NOT NULL,
    dispatch_key VARCHAR(200) NOT NULL,
    file_record_id BIGINT NOT NULL,
    legacy_distribution_task_id BIGINT,
    target_system VARCHAR(100) NOT NULL,
    dispatch_channel VARCHAR(32) NOT NULL,
    target_address VARCHAR(500),
    file_version_no INTEGER NOT NULL DEFAULT 1,
    dispatch_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ack_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    ack_required BOOLEAN NOT NULL DEFAULT FALSE,
    last_dispatch_time TIMESTAMP,
    next_retry_at TIMESTAMP,
    ack_time TIMESTAMP,
    error_code VARCHAR(64),
    error_msg VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_dispatch_record_dispatch_no
    ON file_dispatch_record(dispatch_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_dispatch_record_dispatch_key
    ON file_dispatch_record(dispatch_key);

CREATE INDEX IF NOT EXISTS idx_file_dispatch_record_file
    ON file_dispatch_record(file_record_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_file_dispatch_record_status
    ON file_dispatch_record(dispatch_status, ack_status);

CREATE INDEX IF NOT EXISTS idx_file_dispatch_record_target
    ON file_dispatch_record(target_system, dispatch_channel);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_file_dispatch_record_file'
    ) THEN
        ALTER TABLE file_dispatch_record
            ADD CONSTRAINT fk_file_dispatch_record_file
            FOREIGN KEY (file_record_id) REFERENCES file_record(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_file_dispatch_record_legacy_task'
    ) THEN
        ALTER TABLE file_dispatch_record
            ADD CONSTRAINT fk_file_dispatch_record_legacy_task
            FOREIGN KEY (legacy_distribution_task_id) REFERENCES file_distribution_task(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_dispatch_record_status'
    ) THEN
        ALTER TABLE file_dispatch_record
            ADD CONSTRAINT ck_file_dispatch_record_status
            CHECK (dispatch_status IN ('PENDING', 'DISPATCHING', 'SUCCESS', 'FAILED', 'RETRY_PENDING', 'CANCELLED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_file_dispatch_record_ack_status'
    ) THEN
        ALTER TABLE file_dispatch_record
            ADD CONSTRAINT ck_file_dispatch_record_ack_status
            CHECK (ack_status IN ('NOT_REQUIRED', 'PENDING', 'ACKED', 'REJECTED', 'TIMEOUT'));
    END IF;
END $$;

COMMENT ON TABLE file_dispatch_record IS 'Logical outbound dispatch record with ack/retry/idempotency semantics';

ALTER TABLE file_reception_queue
    ADD COLUMN IF NOT EXISTS file_record_id BIGINT;

ALTER TABLE file_distribution_task
    ADD COLUMN IF NOT EXISTS file_record_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_file_reception_queue_file_record_id
    ON file_reception_queue(file_record_id);

CREATE INDEX IF NOT EXISTS idx_file_distribution_task_file_record_id
    ON file_distribution_task(file_record_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_file_reception_queue_file_record'
    ) THEN
        ALTER TABLE file_reception_queue
            ADD CONSTRAINT fk_file_reception_queue_file_record
            FOREIGN KEY (file_record_id) REFERENCES file_record(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_file_distribution_task_file_record'
    ) THEN
        ALTER TABLE file_distribution_task
            ADD CONSTRAINT fk_file_distribution_task_file_record
            FOREIGN KEY (file_record_id) REFERENCES file_record(id);
    END IF;
END $$;
