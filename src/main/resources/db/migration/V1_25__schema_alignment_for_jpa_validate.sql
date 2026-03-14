-- Align migration-managed schema with current JPA mappings.
-- Keep all changes additive and idempotent.

ALTER TABLE ops_change_request
    ADD COLUMN IF NOT EXISTS window_start TIMESTAMP,
    ADD COLUMN IF NOT EXISTS window_end TIMESTAMP,
    ADD COLUMN IF NOT EXISTS impact_summary TEXT,
    ADD COLUMN IF NOT EXISTS risk_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS rollback_plan TEXT;
