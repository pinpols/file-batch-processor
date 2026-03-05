-- ========================================================
-- V1_11: Extend record_trace for event tracking
-- ========================================================

ALTER TABLE record_trace
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(64);

ALTER TABLE record_trace
    ADD COLUMN IF NOT EXISTS status VARCHAR(32);

ALTER TABLE record_trace
    ADD COLUMN IF NOT EXISTS message VARCHAR(1000);

ALTER TABLE record_trace
    ADD COLUMN IF NOT EXISTS target_system VARCHAR(64);

ALTER TABLE record_trace
    ADD COLUMN IF NOT EXISTS target_address VARCHAR(1024);

ALTER TABLE record_trace
    ADD COLUMN IF NOT EXISTS output_file_name VARCHAR(1024);

CREATE INDEX IF NOT EXISTS idx_record_trace_event_type
    ON record_trace(event_type);
