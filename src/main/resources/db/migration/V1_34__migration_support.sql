-- Phase 10: Migration tracking and cutover support

CREATE TABLE IF NOT EXISTS migration_status (
    id BIGSERIAL PRIMARY KEY,
    migration_name VARCHAR(128) NOT NULL,
    migration_phase VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    progress_percent INTEGER NOT NULL DEFAULT 0,
    total_records BIGINT,
    processed_records BIGINT,
    failed_records BIGINT,
    last_processed_id BIGINT,
    error_summary VARCHAR(1000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_migration_status_name ON migration_status(migration_name);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_migration_status_phase'
    ) THEN
        ALTER TABLE migration_status
            ADD CONSTRAINT ck_migration_status_phase
            CHECK (migration_phase IN ('DUAL_WRITE', 'READ_SWITCH', 'WRITE_SWITCH', 'DEPRECATION', 'RETIREMENT'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_migration_status'
    ) THEN
        ALTER TABLE migration_status
            ADD CONSTRAINT ck_migration_status
            CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'ROLLED_BACK'));
    END IF;
END $$;

COMMENT ON TABLE migration_status IS '分阶段迁移进度记录';

ALTER TABLE file_reception_queue
    ADD COLUMN IF NOT EXISTS legacy_status VARCHAR(32) DEFAULT 'ACTIVE';

ALTER TABLE file_distribution_task
    ADD COLUMN IF NOT EXISTS legacy_status VARCHAR(32) DEFAULT 'ACTIVE';

COMMENT ON COLUMN file_reception_queue.legacy_status IS '旧表迁移状态：MIGRATING 或 DEPRECATED，用于灰度退役';
COMMENT ON COLUMN file_distribution_task.legacy_status IS '旧表迁移状态：MIGRATING 或 DEPRECATED，用于灰度退役';
