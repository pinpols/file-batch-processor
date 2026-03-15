-- Phase 5: business job instance domain

CREATE TABLE IF NOT EXISTS job_instance (
    id BIGSERIAL PRIMARY KEY,
    job_instance_no VARCHAR(64) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    job_name VARCHAR(128) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    operator_name VARCHAR(128),
    biz_date VARCHAR(32),
    batch_no VARCHAR(64),
    run_key VARCHAR(200),
    status VARCHAR(32) NOT NULL,
    rerun_flag BOOLEAN NOT NULL DEFAULT FALSE,
    retry_flag BOOLEAN NOT NULL DEFAULT FALSE,
    manual_flag BOOLEAN NOT NULL DEFAULT FALSE,
    related_file_id BIGINT,
    spring_batch_execution_id BIGINT,
    spring_batch_instance_id BIGINT,
    request_payload JSONB,
    result_summary JSONB,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_instance_no
    ON job_instance(job_instance_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_instance_batch_execution_id
    ON job_instance(spring_batch_execution_id)
    WHERE spring_batch_execution_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_job_instance_task_created
    ON job_instance(task_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_instance_status_created
    ON job_instance(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_instance_related_file
    ON job_instance(related_file_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_instance_run_key
    ON job_instance(run_key);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_job_instance_status'
    ) THEN
        ALTER TABLE job_instance
            ADD CONSTRAINT ck_job_instance_status
            CHECK (status IN (
                'PENDING',
                'TRIGGERED',
                'RUNNING',
                'PARTIAL_SUCCESS',
                'COMPLETED',
                'FAILED',
                'RETRY_PENDING',
                'RERUN_PENDING',
                'CANCELLED',
                'TIMEOUT'
            ));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_job_instance_duration_non_negative'
    ) THEN
        ALTER TABLE job_instance
            ADD CONSTRAINT ck_job_instance_duration_non_negative
            CHECK (duration_ms IS NULL OR duration_ms >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_job_instance_related_file'
    ) THEN
        ALTER TABLE job_instance
            ADD CONSTRAINT fk_job_instance_related_file
            FOREIGN KEY (related_file_id) REFERENCES file_record(id);
    END IF;
END $$;

COMMENT ON TABLE job_instance IS 'Business-facing task instance table; one row represents one business launch';
COMMENT ON COLUMN job_instance.job_instance_no IS 'Business-visible instance identifier, e.g. JI-20260315-9A42C1D0';
COMMENT ON COLUMN job_instance.spring_batch_execution_id IS 'Linked Spring Batch BATCH_JOB_EXECUTION.JOB_EXECUTION_ID';

CREATE TABLE IF NOT EXISTS job_step_instance (
    id BIGSERIAL PRIMARY KEY,
    job_instance_id BIGINT NOT NULL,
    step_code VARCHAR(128) NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    step_order_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    spring_step_execution_id BIGINT,
    read_count BIGINT NOT NULL DEFAULT 0,
    write_count BIGINT NOT NULL DEFAULT 0,
    filter_count BIGINT NOT NULL DEFAULT 0,
    skip_count BIGINT NOT NULL DEFAULT 0,
    commit_count BIGINT NOT NULL DEFAULT 0,
    rollback_count BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    summary_json JSONB,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_step_instance_job_step_attempt
    ON job_step_instance(job_instance_id, step_code, attempt_no);

CREATE INDEX IF NOT EXISTS idx_job_step_instance_job_order
    ON job_step_instance(job_instance_id, step_order_no);

CREATE INDEX IF NOT EXISTS idx_job_step_instance_status
    ON job_step_instance(status);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_job_step_instance_status'
    ) THEN
        ALTER TABLE job_step_instance
            ADD CONSTRAINT ck_job_step_instance_status
            CHECK (status IN (
                'PENDING',
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'SKIPPED',
                'CANCELLED'
            ));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_job_step_instance_attempt_positive'
    ) THEN
        ALTER TABLE job_step_instance
            ADD CONSTRAINT ck_job_step_instance_attempt_positive
            CHECK (attempt_no >= 1);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_job_step_instance_order_positive'
    ) THEN
        ALTER TABLE job_step_instance
            ADD CONSTRAINT ck_job_step_instance_order_positive
            CHECK (step_order_no >= 1);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_job_step_instance_job_instance'
    ) THEN
        ALTER TABLE job_step_instance
            ADD CONSTRAINT fk_job_step_instance_job_instance
            FOREIGN KEY (job_instance_id) REFERENCES job_instance(id) ON DELETE CASCADE;
    END IF;
END $$;

COMMENT ON TABLE job_step_instance IS 'Business-facing step progress snapshot belonging to one job_instance';

CREATE TABLE IF NOT EXISTS job_execution_log (
    id BIGSERIAL PRIMARY KEY,
    job_instance_id BIGINT NOT NULL,
    job_step_instance_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    level VARCHAR(16) NOT NULL DEFAULT 'INFO',
    message VARCHAR(1000) NOT NULL,
    operator_name VARCHAR(128),
    payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_execution_log_job_created
    ON job_execution_log(job_instance_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_execution_log_step_created
    ON job_execution_log(job_step_instance_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_execution_log_event
    ON job_execution_log(event_type, created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_job_execution_log_level'
    ) THEN
        ALTER TABLE job_execution_log
            ADD CONSTRAINT ck_job_execution_log_level
            CHECK (level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_job_execution_log_job_instance'
    ) THEN
        ALTER TABLE job_execution_log
            ADD CONSTRAINT fk_job_execution_log_job_instance
            FOREIGN KEY (job_instance_id) REFERENCES job_instance(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_job_execution_log_job_step_instance'
    ) THEN
        ALTER TABLE job_execution_log
            ADD CONSTRAINT fk_job_execution_log_job_step_instance
            FOREIGN KEY (job_step_instance_id) REFERENCES job_step_instance(id) ON DELETE SET NULL;
    END IF;
END $$;

COMMENT ON TABLE job_execution_log IS 'Structured business task instance event log';
