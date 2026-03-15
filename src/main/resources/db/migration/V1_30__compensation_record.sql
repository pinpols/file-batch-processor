-- Phase 6: retry and compensation unification

CREATE TABLE IF NOT EXISTS compensation_record (
    id BIGSERIAL PRIMARY KEY,
    compensation_no VARCHAR(64) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_job_instance_id BIGINT,
    target_step_instance_id BIGINT,
    related_file_id BIGINT,
    related_dlq_record_id BIGINT,
    legacy_distribution_task_id BIGINT,
    source_spring_execution_id BIGINT,
    restarted_spring_execution_id BIGINT,
    operator_name VARCHAR(128),
    reason VARCHAR(500),
    request_payload JSONB,
    result_payload JSONB,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_compensation_record_no
    ON compensation_record(compensation_no);

CREATE INDEX IF NOT EXISTS idx_compensation_record_status_created
    ON compensation_record(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compensation_record_action_created
    ON compensation_record(action_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compensation_record_target_job
    ON compensation_record(target_job_instance_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compensation_record_related_file
    ON compensation_record(related_file_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compensation_record_related_dlq
    ON compensation_record(related_dlq_record_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compensation_record_legacy_dist
    ON compensation_record(legacy_distribution_task_id, created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_compensation_record_action_type'
    ) THEN
        ALTER TABLE compensation_record
            ADD CONSTRAINT ck_compensation_record_action_type
            CHECK (action_type IN (
                'JOB_RESTART',
                'JOB_RETRY',
                'STEP_RETRY',
                'FILE_RETRY',
                'BATCH_RERUN',
                'DLQ_REPLAY'
            ));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_compensation_record_status'
    ) THEN
        ALTER TABLE compensation_record
            ADD CONSTRAINT ck_compensation_record_status
            CHECK (status IN (
                'REQUESTED',
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'CANCELLED'
            ));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_compensation_record_target_job_instance'
    ) THEN
        ALTER TABLE compensation_record
            ADD CONSTRAINT fk_compensation_record_target_job_instance
            FOREIGN KEY (target_job_instance_id) REFERENCES job_instance(id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_compensation_record_target_step_instance'
    ) THEN
        ALTER TABLE compensation_record
            ADD CONSTRAINT fk_compensation_record_target_step_instance
            FOREIGN KEY (target_step_instance_id) REFERENCES job_step_instance(id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_compensation_record_related_file'
    ) THEN
        ALTER TABLE compensation_record
            ADD CONSTRAINT fk_compensation_record_related_file
            FOREIGN KEY (related_file_id) REFERENCES file_record(id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_compensation_record_related_dlq'
    ) THEN
        ALTER TABLE compensation_record
            ADD CONSTRAINT fk_compensation_record_related_dlq
            FOREIGN KEY (related_dlq_record_id) REFERENCES dlq_records(id) ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_compensation_record_legacy_distribution_task'
    ) THEN
        ALTER TABLE compensation_record
            ADD CONSTRAINT fk_compensation_record_legacy_distribution_task
            FOREIGN KEY (legacy_distribution_task_id) REFERENCES file_distribution_task(id) ON DELETE SET NULL;
    END IF;
END $$;

COMMENT ON TABLE compensation_record IS 'Unified retry/compensation audit table';
COMMENT ON COLUMN compensation_record.compensation_no IS 'Business-visible compensation identifier';
