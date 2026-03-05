CREATE TABLE IF NOT EXISTS target_system_circuit_state (
    target_system VARCHAR(128) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    last_failure_at TIMESTAMP,
    window_failure_count BIGINT NOT NULL DEFAULT 0,
    window_size BIGINT NOT NULL DEFAULT 10,
    failure_rate_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    cooldown_duration_ms BIGINT NOT NULL DEFAULT 300000,
    cooldown_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_target_system_circuit_status
    ON target_system_circuit_state(status);

CREATE INDEX IF NOT EXISTS idx_target_system_circuit_cooldown_until
    ON target_system_circuit_state(cooldown_until);
