-- Create job_execution table for custom JobExecution entity
-- Migration script for job_execution table

CREATE TABLE IF NOT EXISTS job_execution (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    task_id VARCHAR(100) NOT NULL,
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    parameters TEXT,
    error_message VARCHAR(2000),
    duration BIGINT,
    triggered_by VARCHAR(100),
    exit_code INTEGER,
    total_read BIGINT,
    total_processed BIGINT,
    total_failed BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_job_execution_task_id ON job_execution(task_id);
CREATE INDEX IF NOT EXISTS idx_job_execution_status ON job_execution(status);
CREATE INDEX IF NOT EXISTS idx_job_execution_created_at ON job_execution(created_at);
