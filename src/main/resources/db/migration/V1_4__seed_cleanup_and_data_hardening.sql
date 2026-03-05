-- ========================================================
-- V1_4: Seed 清理 + 数据约束加固 + batch_date DATE 化（分阶段）
-- 说明：
-- 1) 不修改历史迁移，仅通过增量脚本清理 legacy seed
-- 2) 采用 DATE 影子列(batch_date_d)，避免直接改列类型造成兼容风险
-- ========================================================

-- -----------------------------
-- A. 清理生产硬编码 seed 参数
-- -----------------------------
UPDATE task_parameter
SET param_value = ''
WHERE task_id = 'process-file-main'
  AND param_name = 'input.file.name'
  AND param_value LIKE '%sample.csv%';

UPDATE task_parameter
SET param_value = 'select id, business_key, name, description, batch_date from imported_records'
WHERE task_id = 'data-export-main'
  AND param_name = 'export.sql'
  AND param_value LIKE '%2026-03-01%';

UPDATE task_parameter
SET param_value = ''
WHERE task_id = 'data-export-main'
  AND param_name = 'output.file.name'
  AND param_value LIKE 'export/%';

-- 主链路任务默认禁用，避免未配置即运行
UPDATE task_definition
SET enabled = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE task_id IN ('process-file-main', 'data-export-main');

-- -----------------------------
-- B. batch_date DATE 迁移（影子列）
-- -----------------------------
ALTER TABLE imported_records ADD COLUMN IF NOT EXISTS batch_date_d DATE;
UPDATE imported_records
SET batch_date_d = CASE
    WHEN batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS NULL;
CREATE INDEX IF NOT EXISTS idx_imported_records_batch_date_d ON imported_records(batch_date_d);
ALTER TABLE imported_records
    ADD CONSTRAINT ck_imported_records_batch_date_format
    CHECK (batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$') NOT VALID;

ALTER TABLE imported_records_partition ADD COLUMN IF NOT EXISTS batch_date_d DATE;
UPDATE imported_records_partition
SET batch_date_d = CASE
    WHEN batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS NULL;
CREATE INDEX IF NOT EXISTS idx_imported_records_partition_batch_date_d ON imported_records_partition(batch_date_d);
ALTER TABLE imported_records_partition
    ADD CONSTRAINT ck_imported_records_partition_batch_date_format
    CHECK (batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$') NOT VALID;

ALTER TABLE execution_dedup_records ADD COLUMN IF NOT EXISTS batch_date_d DATE;
UPDATE execution_dedup_records
SET batch_date_d = CASE
    WHEN batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS NULL;
CREATE INDEX IF NOT EXISTS idx_exec_dedup_batch_date_d ON execution_dedup_records(batch_date_d);
ALTER TABLE execution_dedup_records
    ADD CONSTRAINT ck_exec_dedup_batch_date_format
    CHECK (batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$') NOT VALID;

ALTER TABLE task_execution_state ADD COLUMN IF NOT EXISTS batch_date_d DATE;
UPDATE task_execution_state
SET batch_date_d = CASE
    WHEN batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS NULL;
CREATE INDEX IF NOT EXISTS idx_task_exec_state_batch_date_d ON task_execution_state(batch_date_d);
ALTER TABLE task_execution_state
    ADD CONSTRAINT ck_task_exec_state_batch_date_format
    CHECK (batch_date ~ '^\\d{4}-\\d{2}-\\d{2}$') NOT VALID;

-- -----------------------------
-- C. 约束与索引补强（NOT NULL/CHECK）
-- -----------------------------
ALTER TABLE dlq_records
    ALTER COLUMN handled SET DEFAULT FALSE,
    ALTER COLUMN replay_count SET DEFAULT 0;

ALTER TABLE dlq_records
    ADD CONSTRAINT ck_dlq_replay_count_non_negative
    CHECK (replay_count >= 0) NOT VALID;

ALTER TABLE task_execution_state
    ADD CONSTRAINT ck_task_execution_state_attempt_non_negative
    CHECK (attempt >= 0) NOT VALID,
    ADD CONSTRAINT ck_task_execution_state_max_attempts_positive
    CHECK (max_attempts >= 1) NOT VALID,
    ADD CONSTRAINT ck_task_execution_state_status_allowed
    CHECK (status IN ('READY','RUNNING','SUCCESS','FAILED','BLOCKED')) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_task_execution_state_next_retry_at
ON task_execution_state(next_retry_at);

CREATE INDEX IF NOT EXISTS idx_dlq_records_created_unhandled
ON dlq_records(created_at)
WHERE handled = FALSE;

-- -----------------------------
-- D. 预留后续正式切换
-- -----------------------------
-- 待业务代码完成 LocalDate 全量切换后，可执行：
-- 1) 校验 batch_date_d 非空
-- 2) 使用 batch_date_d 替换 batch_date
-- 3) 删除 legacy 字符串列
