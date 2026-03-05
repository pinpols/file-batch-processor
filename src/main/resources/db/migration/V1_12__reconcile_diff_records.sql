-- ========================================================
-- V1_12: Reconcile diff samples
-- ========================================================

CREATE TABLE IF NOT EXISTS reconcile_diff_records (
    id BIGSERIAL PRIMARY KEY,
    reconcile_run_id BIGINT NOT NULL,
    diff_type VARCHAR(32) NOT NULL,
    business_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reconcile_diff_run
    ON reconcile_diff_records(reconcile_run_id);

CREATE INDEX IF NOT EXISTS idx_reconcile_diff_type
    ON reconcile_diff_records(diff_type);

CREATE INDEX IF NOT EXISTS idx_reconcile_diff_business
    ON reconcile_diff_records(business_key);
