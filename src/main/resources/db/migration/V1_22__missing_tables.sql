-- Missing tables analysis and creation script
-- This script creates tables that are referenced by entities but missing from migrations

-- 1. batch_run_records table (referenced in V1_2 and V1_6 but base table missing)
CREATE TABLE IF NOT EXISTS batch_run_records (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 job_name VARCHAR(128) NOT NULL,
                                                 batch_date VARCHAR(20),
                                                 status VARCHAR(20) NOT NULL,
                                                 start_time TIMESTAMP,
                                                 end_time TIMESTAMP,
                                                 exit_code INTEGER,
                                                 exit_description VARCHAR(1000),
                                                 read_count BIGINT DEFAULT 0,
                                                 write_count BIGINT DEFAULT 0,
                                                 commit_count BIGINT DEFAULT 0,
                                                 rollback_count BIGINT DEFAULT 0,
                                                 skip_count BIGINT DEFAULT 0,
                                                 parse_error_count BIGINT DEFAULT 0,
                                                 duration_ms BIGINT DEFAULT 0,
                                                 throughput_rps DOUBLE PRECISION DEFAULT 0,
                                                 replay_count BIGINT DEFAULT 0,
                                                 last_replay_error VARCHAR(1000),
                                                 tenant_id VARCHAR(64),
                                                 biz_domain VARCHAR(64),
                                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. imported_records table
CREATE TABLE IF NOT EXISTS imported_records (
                                                id BIGSERIAL PRIMARY KEY,
                                                business_key VARCHAR(200) NOT NULL,
                                                batch_date VARCHAR(20) NOT NULL,
                                                source_file_name VARCHAR(1024),
                                                line_no BIGINT,
                                                raw_data TEXT,
                                                parsed_data TEXT,
                                                status VARCHAR(20) NOT NULL,
                                                error_message VARCHAR(1000),
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                CONSTRAINT uk_import_biz_batch UNIQUE (business_key, batch_date)
);

-- 3. imported_records_partition table
CREATE TABLE IF NOT EXISTS imported_records_partition (
                                                          id BIGSERIAL PRIMARY KEY,
                                                          business_key VARCHAR(200) NOT NULL,
                                                          batch_date VARCHAR(20) NOT NULL,
                                                          partition_key VARCHAR(64) NOT NULL,
                                                          source_file_name VARCHAR(1024),
                                                          line_no BIGINT,
                                                          raw_data TEXT,
                                                          parsed_data TEXT,
                                                          status VARCHAR(20) NOT NULL,
                                                          error_message VARCHAR(1000),
                                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          CONSTRAINT uk_import_biz_batch_part UNIQUE (business_key, batch_date, partition_key)
);

-- 4. file_distribution_task table
CREATE TABLE IF NOT EXISTS file_distribution_task (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      task_id VARCHAR(100) NOT NULL,
                                                      source_file_path VARCHAR(1024) NOT NULL,
                                                      target_system VARCHAR(128) NOT NULL,
                                                      target_path VARCHAR(1024) NOT NULL,
                                                      status VARCHAR(20) NOT NULL,
                                                      retry_count INTEGER DEFAULT 0,
                                                      error_message VARCHAR(1000),
                                                      tenant_id VARCHAR(64),
                                                      biz_domain VARCHAR(64),
                                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. file_reception_queue table
CREATE TABLE IF NOT EXISTS file_reception_queue (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    file_name VARCHAR(512) NOT NULL,
                                                    file_path VARCHAR(1024) NOT NULL,
                                                    file_size BIGINT,
                                                    file_hash VARCHAR(128),
                                                    status VARCHAR(20) NOT NULL,
                                                    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    processed_at TIMESTAMP,
                                                    error_message VARCHAR(1000),
                                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. file_data table
CREATE TABLE IF NOT EXISTS file_data (
                                         id BIGSERIAL PRIMARY KEY,
                                         file_name VARCHAR(512) NOT NULL,
                                         file_path VARCHAR(1024) NOT NULL,
                                         file_size BIGINT,
                                         file_type VARCHAR(64),
                                         content_hash VARCHAR(128),
                                         metadata TEXT,
                                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. dlq_records table
CREATE TABLE IF NOT EXISTS dlq_records (
                                           id BIGSERIAL PRIMARY KEY,
                                           task_id VARCHAR(100) NOT NULL,
                                           batch_date VARCHAR(32) NOT NULL,
                                           business_key VARCHAR(200),
                                           raw_data TEXT,
                                           error_message VARCHAR(1000) NOT NULL,
                                           retry_count INTEGER DEFAULT 0,
                                           status VARCHAR(20) NOT NULL,
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. job_log_records table
CREATE TABLE IF NOT EXISTS job_log_records (
                                               id BIGSERIAL PRIMARY KEY,
                                               job_execution_id BIGINT,
                                               job_name VARCHAR(128),
                                               step_name VARCHAR(128),
                                               log_level VARCHAR(10),
                                               log_message TEXT,
                                               log_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. task_execution_state table
CREATE TABLE IF NOT EXISTS task_execution_state (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    task_id VARCHAR(100) NOT NULL,
                                                    batch_date VARCHAR(32) NOT NULL,
                                                    status VARCHAR(20) NOT NULL,
                                                    start_time TIMESTAMP,
                                                    end_time TIMESTAMP,
                                                    retry_count INTEGER DEFAULT 0,
                                                    error_message VARCHAR(1000),
                                                    tenant_id VARCHAR(64),
                                                    biz_domain VARCHAR(64),
                                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_batch_run_records_job_name ON batch_run_records(job_name);
CREATE INDEX IF NOT EXISTS idx_batch_run_records_status ON batch_run_records(status);
CREATE INDEX IF NOT EXISTS idx_batch_run_records_tenant ON batch_run_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_batch_run_records_biz_domain ON batch_run_records(biz_domain);

CREATE INDEX IF NOT EXISTS idx_imported_records_business_key ON imported_records(business_key);
CREATE INDEX IF NOT EXISTS idx_imported_records_batch_date ON imported_records(batch_date);

CREATE INDEX IF NOT EXISTS idx_imported_partition_business_key ON imported_records_partition(business_key);
CREATE INDEX IF NOT EXISTS idx_imported_partition_batch_date ON imported_records_partition(batch_date);
CREATE INDEX IF NOT EXISTS idx_imported_partition_partition_key ON imported_records_partition(partition_key);

CREATE INDEX IF NOT EXISTS idx_file_dist_task_status ON file_distribution_task(status);
CREATE INDEX IF NOT EXISTS idx_file_dist_task_target_system ON file_distribution_task(target_system);
CREATE INDEX IF NOT EXISTS idx_file_dist_task_tenant ON file_distribution_task(tenant_id);

CREATE INDEX IF NOT EXISTS idx_file_reception_status ON file_reception_queue(status);
CREATE INDEX IF NOT EXISTS idx_file_reception_created_at ON file_reception_queue(created_at);

-- CREATE INDEX IF NOT EXISTS idx_dlq_records_task_id ON dlq_records(task_id);
-- CREATE INDEX IF NOT EXISTS idx_dlq_records_batch_date ON dlq_records(batch_date);
-- CREATE INDEX IF NOT EXISTS idx_dlq_records_status ON dlq_records(status);

-- CREATE INDEX IF NOT EXISTS idx_job_log_job_execution_id ON job_log_records(job_execution_id);
-- CREATE INDEX IF NOT EXISTS idx_job_log_job_name ON job_log_records(job_name);

CREATE INDEX IF NOT EXISTS idx_task_exec_state_task_id ON task_execution_state(task_id);
CREATE INDEX IF NOT EXISTS idx_task_exec_state_status ON task_execution_state(status);
CREATE INDEX IF NOT EXISTS idx_task_exec_state_tenant ON task_execution_state(tenant_id);
CREATE INDEX IF NOT EXISTS idx_task_exec_state_biz_domain ON task_execution_state(biz_domain);
