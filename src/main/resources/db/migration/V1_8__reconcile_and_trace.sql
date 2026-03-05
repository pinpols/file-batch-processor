-- ========================================================
-- V1_8: Reconcile run records + record trace index
-- Platform capability: standardized reconciliation and record-level trace.
-- ========================================================

CREATE TABLE IF NOT EXISTS reconcile_run_records (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(128) NOT NULL,
    batch_date VARCHAR(20),
    input_file_name VARCHAR(1024),
    source_count BIGINT NOT NULL DEFAULT 0,
    target_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reconcile_run_created_at
    ON reconcile_run_records(created_at);

CREATE INDEX IF NOT EXISTS idx_reconcile_run_job_batch
    ON reconcile_run_records(job_name, batch_date);


CREATE TABLE IF NOT EXISTS record_trace (
    id BIGSERIAL PRIMARY KEY,
    business_key VARCHAR(200) NOT NULL,
    batch_date VARCHAR(20),
    job_name VARCHAR(128) NOT NULL,
    job_execution_id BIGINT,
    source_file_name VARCHAR(1024),
    line_no BIGINT,
    imported_record_partition_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_record_trace_business_key
    ON record_trace(business_key);

CREATE INDEX IF NOT EXISTS idx_record_trace_business_batch
    ON record_trace(business_key, batch_date);

CREATE INDEX IF NOT EXISTS idx_record_trace_created_at
    ON record_trace(created_at);
