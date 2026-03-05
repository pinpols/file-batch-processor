-- ========================================================
-- 表: task_dependency (任务依赖关系表)
-- 用于声明「task_id 依赖 depends_on_task_id」
-- ========================================================

CREATE TABLE IF NOT EXISTS task_dependency (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    depends_on_task_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_dependency_task_id
        FOREIGN KEY (task_id) REFERENCES task_definition(task_id),
    CONSTRAINT fk_task_dependency_depends_on_task_id
        FOREIGN KEY (depends_on_task_id) REFERENCES task_definition(task_id),
    CONSTRAINT uq_task_dependency UNIQUE (task_id, depends_on_task_id)
);

CREATE INDEX IF NOT EXISTS idx_task_dependency_task_id
    ON task_dependency(task_id);

CREATE INDEX IF NOT EXISTS idx_task_dependency_depends_on_task_id
    ON task_dependency(depends_on_task_id);

