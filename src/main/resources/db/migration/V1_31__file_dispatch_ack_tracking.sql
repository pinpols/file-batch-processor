ALTER TABLE file_dispatch_record
    ADD COLUMN IF NOT EXISTS created_job_instance_id BIGINT,
    ADD COLUMN IF NOT EXISTS last_dispatch_job_instance_id BIGINT,
    ADD COLUMN IF NOT EXISTS last_ack_job_instance_id BIGINT,
    ADD COLUMN IF NOT EXISTS ack_timeout_minutes INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN IF NOT EXISTS ack_deadline_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ack_message VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS ack_payload JSONB,
    ADD COLUMN IF NOT EXISTS resend_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_file_dispatch_record_ack_deadline
    ON file_dispatch_record(ack_status, ack_deadline_at);

CREATE INDEX IF NOT EXISTS idx_file_dispatch_record_created_job
    ON file_dispatch_record(created_job_instance_id);

CREATE INDEX IF NOT EXISTS idx_file_dispatch_record_last_dispatch_job
    ON file_dispatch_record(last_dispatch_job_instance_id);

CREATE INDEX IF NOT EXISTS idx_file_dispatch_record_last_ack_job
    ON file_dispatch_record(last_ack_job_instance_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_file_dispatch_record_created_job_instance'
    ) THEN
        ALTER TABLE file_dispatch_record
            ADD CONSTRAINT fk_file_dispatch_record_created_job_instance
            FOREIGN KEY (created_job_instance_id) REFERENCES job_instance(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_file_dispatch_record_last_dispatch_job_instance'
    ) THEN
        ALTER TABLE file_dispatch_record
            ADD CONSTRAINT fk_file_dispatch_record_last_dispatch_job_instance
            FOREIGN KEY (last_dispatch_job_instance_id) REFERENCES job_instance(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_file_dispatch_record_last_ack_job_instance'
    ) THEN
        ALTER TABLE file_dispatch_record
            ADD CONSTRAINT fk_file_dispatch_record_last_ack_job_instance
            FOREIGN KEY (last_ack_job_instance_id) REFERENCES job_instance(id);
    END IF;
END $$;

COMMENT ON COLUMN file_dispatch_record.created_job_instance_id IS 'Business job instance that created the logical dispatch record';
COMMENT ON COLUMN file_dispatch_record.last_dispatch_job_instance_id IS 'Business job instance that executed the latest outbound dispatch attempt';
COMMENT ON COLUMN file_dispatch_record.last_ack_job_instance_id IS 'Business job instance that recorded latest ack/timeout processing';
COMMENT ON COLUMN file_dispatch_record.ack_timeout_minutes IS 'Expected ack timeout window in minutes';
COMMENT ON COLUMN file_dispatch_record.ack_deadline_at IS 'Absolute deadline for expected ack reception';
COMMENT ON COLUMN file_dispatch_record.ack_message IS 'Latest ack business message or timeout reason';
COMMENT ON COLUMN file_dispatch_record.ack_payload IS 'Latest ack payload snapshot';
COMMENT ON COLUMN file_dispatch_record.resend_count IS 'How many resend requests have been issued for this logical dispatch';
