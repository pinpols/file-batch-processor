-- ========================================================
-- V1_9: Enhance reconcile_run_records with hash fields
-- ========================================================

ALTER TABLE reconcile_run_records
    ADD COLUMN IF NOT EXISTS source_hash VARCHAR(64);

ALTER TABLE reconcile_run_records
    ADD COLUMN IF NOT EXISTS target_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_reconcile_run_status
    ON reconcile_run_records(status);
