-- ========================================================
-- V1_2: 批量核心能力增强
-- 1) 跨实例去重锁表
-- 2) DLQ 补偿字段
-- 3) 批次审计指标字段
-- ========================================================

CREATE TABLE IF NOT EXISTS execution_dedup_records (
    id BIGSERIAL PRIMARY KEY,
    dedup_key VARCHAR(256) NOT NULL,
    batch_date VARCHAR(32) NOT NULL,
    rerun_id VARCHAR(128) NOT NULL,
    window_bucket BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_exec_dedup_bucket
ON execution_dedup_records (dedup_key, batch_date, rerun_id, window_bucket);

CREATE INDEX IF NOT EXISTS idx_exec_dedup_created_at
ON execution_dedup_records (created_at);

ALTER TABLE dlq_records
    ADD COLUMN IF NOT EXISTS handled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS replay_count BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_replay_error VARCHAR(1000);

ALTER TABLE batch_run_records
    ADD COLUMN IF NOT EXISTS parse_error_count BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS throughput_rps DOUBLE PRECISION DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rollback_count BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS commit_count BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retry_count BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quality_passed BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS quality_message VARCHAR(500);
