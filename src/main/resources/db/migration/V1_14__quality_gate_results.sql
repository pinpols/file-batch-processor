CREATE TABLE IF NOT EXISTS quality_gate_results (
    id BIGSERIAL PRIMARY KEY,
    gate_type VARCHAR(64) NOT NULL,
    job_name VARCHAR(128),
    step_name VARCHAR(128),
    batch_date VARCHAR(32),
    job_execution_id BIGINT,
    step_execution_id BIGINT,
    read_count BIGINT NOT NULL DEFAULT 0,
    parse_error_count BIGINT NOT NULL DEFAULT 0,
    total_count BIGINT NOT NULL DEFAULT 0,
    error_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    max_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    min_lines BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_gate_created_at
    ON quality_gate_results(created_at);

CREATE INDEX IF NOT EXISTS idx_quality_gate_job_batch
    ON quality_gate_results(job_name, batch_date);

CREATE INDEX IF NOT EXISTS idx_quality_gate_status
    ON quality_gate_results(status);
