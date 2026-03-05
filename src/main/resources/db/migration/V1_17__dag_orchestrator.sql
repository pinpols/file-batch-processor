-- ========================================================
-- V1_17: DAG orchestrator for complex task dependencies
-- ========================================================

CREATE TABLE IF NOT EXISTS dag_definition (
    dag_id VARCHAR(128) PRIMARY KEY,
    dag_name VARCHAR(256) NOT NULL,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    fail_fast BOOLEAN NOT NULL DEFAULT TRUE,
    max_duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dag_node (
    id BIGSERIAL PRIMARY KEY,
    dag_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(100) NOT NULL,
    node_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dag_node_dag_id FOREIGN KEY (dag_id) REFERENCES dag_definition(dag_id),
    CONSTRAINT fk_dag_node_task_id FOREIGN KEY (task_id) REFERENCES task_definition(task_id),
    CONSTRAINT uq_dag_node UNIQUE (dag_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_dag_node_dag_id ON dag_node(dag_id);
CREATE INDEX IF NOT EXISTS idx_dag_node_order ON dag_node(dag_id, node_order);

CREATE TABLE IF NOT EXISTS dag_run (
    id BIGSERIAL PRIMARY KEY,
    dag_id VARCHAR(128) NOT NULL,
    batch_date VARCHAR(32) NOT NULL,
    rerun_id VARCHAR(128) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dag_run_dag_id FOREIGN KEY (dag_id) REFERENCES dag_definition(dag_id)
);

CREATE INDEX IF NOT EXISTS idx_dag_run_lookup ON dag_run(dag_id, batch_date, rerun_id, created_at);
CREATE INDEX IF NOT EXISTS idx_dag_run_status ON dag_run(status);

CREATE TABLE IF NOT EXISTS dag_node_run (
    id BIGSERIAL PRIMARY KEY,
    dag_run_id BIGINT NOT NULL,
    task_id VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 1,
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dag_node_run_dag_run_id FOREIGN KEY (dag_run_id) REFERENCES dag_run(id)
);

CREATE INDEX IF NOT EXISTS idx_dag_node_run_dag_run ON dag_node_run(dag_run_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dag_node_run_unique ON dag_node_run(dag_run_id, task_id);

INSERT INTO dag_definition (dag_id, dag_name, description, enabled, fail_fast, max_duration_ms)
VALUES ('default-main-pipeline', '默认主链路', '导入 -> 导出 -> 分发', TRUE, TRUE, 7200000)
ON CONFLICT (dag_id) DO NOTHING;

INSERT INTO dag_node (dag_id, task_id, node_order, enabled)
SELECT 'default-main-pipeline', 'process-file-main', 10, TRUE
WHERE EXISTS (SELECT 1 FROM task_definition WHERE task_id = 'process-file-main')
ON CONFLICT (dag_id, task_id) DO NOTHING;

INSERT INTO dag_node (dag_id, task_id, node_order, enabled)
SELECT 'default-main-pipeline', 'data-export-main', 20, TRUE
WHERE EXISTS (SELECT 1 FROM task_definition WHERE task_id = 'data-export-main')
ON CONFLICT (dag_id, task_id) DO NOTHING;

INSERT INTO dag_node (dag_id, task_id, node_order, enabled)
SELECT 'default-main-pipeline', 'file-distribution-pending', 30, TRUE
WHERE EXISTS (SELECT 1 FROM task_definition WHERE task_id = 'file-distribution-pending')
ON CONFLICT (dag_id, task_id) DO NOTHING;

INSERT INTO task_dependency (task_id, depends_on_task_id, dependency_timeout_ms, on_failure_action)
SELECT 'data-export-main', 'process-file-main', 600000, 'FAIL'
WHERE EXISTS (SELECT 1 FROM task_definition WHERE task_id = 'data-export-main')
  AND EXISTS (SELECT 1 FROM task_definition WHERE task_id = 'process-file-main')
ON CONFLICT (task_id, depends_on_task_id) DO NOTHING;

INSERT INTO task_dependency (task_id, depends_on_task_id, dependency_timeout_ms, on_failure_action)
SELECT 'file-distribution-pending', 'data-export-main', 600000, 'FAIL'
WHERE EXISTS (SELECT 1 FROM task_definition WHERE task_id = 'file-distribution-pending')
  AND EXISTS (SELECT 1 FROM task_definition WHERE task_id = 'data-export-main')
ON CONFLICT (task_id, depends_on_task_id) DO NOTHING;
