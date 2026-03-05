-- ========================================================
-- 任务配置相关表的 DDL 初始化脚本
-- 用于将任务配置从 YAML 迁移到数据库
-- ========================================================

-- ========================================================
-- 表 1: task_definition (任务定义表)
-- 用于存储任务的基本配置信息
-- ========================================================
CREATE TABLE IF NOT EXISTS task_definition (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL UNIQUE,
    job_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    priority VARCHAR(20),
    allow_parallel BOOLEAN DEFAULT FALSE,
    dedup_key VARCHAR(100),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_definition_task_id ON task_definition(task_id);
CREATE INDEX IF NOT EXISTS idx_task_definition_enabled ON task_definition(enabled);
CREATE INDEX IF NOT EXISTS idx_task_definition_priority ON task_definition(priority);

-- ========================================================
-- 表 2: task_trigger (任务触发器表)
-- 用于存储任务的执行计划（CRON、FIXED_RATE、ONE_TIME）
-- ========================================================
CREATE TABLE IF NOT EXISTS task_trigger (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    trigger_type VARCHAR(50) NOT NULL,
    cron_expression VARCHAR(100),
    fixed_rate_ms BIGINT,
    one_time_at TIMESTAMP,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_trigger_task_id FOREIGN KEY (task_id) REFERENCES task_definition(task_id)
);

CREATE INDEX IF NOT EXISTS idx_task_trigger_task_id ON task_trigger(task_id);
CREATE INDEX IF NOT EXISTS idx_task_trigger_type ON task_trigger(trigger_type);
CREATE INDEX IF NOT EXISTS idx_task_trigger_enabled ON task_trigger(enabled);

-- ========================================================
-- 表 3: task_parameter (任务参数表)
-- 用于存储任务执行时所需的参数（键值对形式）
-- ========================================================
CREATE TABLE IF NOT EXISTS task_parameter (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    param_name VARCHAR(100) NOT NULL,
    param_value VARCHAR(1000),
    param_type VARCHAR(50),
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_parameter_task_id FOREIGN KEY (task_id) REFERENCES task_definition(task_id),
    UNIQUE (task_id, param_name)
);

CREATE INDEX IF NOT EXISTS idx_task_parameter_task_id ON task_parameter(task_id);
CREATE INDEX IF NOT EXISTS idx_task_parameter_name ON task_parameter(param_name);

-- ========================================================
-- 初始数据：插入 4 个核心功能任务配置
-- ========================================================

-- 1. 分区导入任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('partitioned-import-daily', 'partitionedImportJob', '分区导入任务：每天导入数据到分区表', 'HIGH', FALSE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
SELECT 'partitioned-import-daily', 'CRON', '0 0 1 * * ?', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'partitioned-import-daily'
      AND trigger_type = 'CRON'
      AND cron_expression = '0 0 1 * * ?'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'partitioned-import-daily', 'batchDate', '', 'STRING', '批次日期（空表示使用当前日期）'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'partitioned-import-daily' AND param_name = 'batchDate'
);

-- 2. 数据导出任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-export-daily', 'fileExportJob', '数据导出任务：每天导出数据到文件', 'NORMAL', FALSE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
SELECT 'file-export-daily', 'CRON', '0 0 2 * * ?', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'file-export-daily'
      AND trigger_type = 'CRON'
      AND cron_expression = '0 0 2 * * ?'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'file-export-daily', 'batchDate', '', 'STRING', '批次日期'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'file-export-daily' AND param_name = 'batchDate'
);
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'file-export-daily', 'format', 'csv', 'STRING', '导出格式：csv/json/excel'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'file-export-daily' AND param_name = 'format'
);
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'file-export-daily', 'outputDir', 'export', 'STRING', '输出目录'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'file-export-daily' AND param_name = 'outputDir'
);

-- 3. 文件接收监控任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-reception-monitor', 'fileReceptionJob', '文件接收监控：每 10 分钟检查一次待处理文件', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
SELECT 'file-reception-monitor', 'FIXED_RATE', 600000, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'file-reception-monitor'
      AND trigger_type = 'FIXED_RATE'
      AND fixed_rate_ms = 600000
);

-- 4. 文件接收超时检测任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-reception-timeout-check', 'fileReceptionTimeoutJob', '文件接收超时检测：每 6 小时检查一次', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
SELECT 'file-reception-timeout-check', 'CRON', '0 0 */6 * * ?', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'file-reception-timeout-check'
      AND trigger_type = 'CRON'
      AND cron_expression = '0 0 */6 * * ?'
);

-- 5. 待分发文件处理任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-distribution-pending', 'fileDistributionJob', '文件分发：每 5 分钟处理待分发任务', 'HIGH', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
SELECT 'file-distribution-pending', 'FIXED_RATE', 300000, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'file-distribution-pending'
      AND trigger_type = 'FIXED_RATE'
      AND fixed_rate_ms = 300000
);

-- 6. 文件分发重试任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-distribution-retry', 'fileDistributionRetryJob', '文件分发重试：每 15 分钟检查需要重试的任务', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
SELECT 'file-distribution-retry', 'FIXED_RATE', 900000, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'file-distribution-retry'
      AND trigger_type = 'FIXED_RATE'
      AND fixed_rate_ms = 900000
);

-- 7. 文件分发超时检测任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-distribution-timeout', 'fileDistributionTimeoutJob', '文件分发超时检测：每 12 小时检查一次', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
SELECT 'file-distribution-timeout', 'CRON', '0 0 */12 * * ?', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'file-distribution-timeout'
      AND trigger_type = 'CRON'
      AND cron_expression = '0 0 */12 * * ?'
);

-- 8. 死信重放任务（自动补偿）
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('dlq-replay-job', 'dlqReplayJob', 'DLQ 自动重放补偿任务', 'HIGH', FALSE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
SELECT 'dlq-replay-job', 'FIXED_RATE', 1800000, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'dlq-replay-job'
      AND trigger_type = 'FIXED_RATE'
      AND fixed_rate_ms = 1800000
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'dlq-replay-job', 'limit', '50', 'INT', '单次重放条数上限'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'dlq-replay-job' AND param_name = 'limit'
);

-- 9. 失败任务恢复入口（默认禁用，人工触发）
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('batch-restart-job', 'batchRestartJob', '批量失败任务恢复入口', 'HIGH', FALSE, FALSE)
ON CONFLICT (task_id) DO NOTHING;

-- 10. 文件导入主链路任务（processFileJob/importJob）
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('process-file-main', 'processFileJob', '文件导入主链路任务：上游文件导入分区表', 'HIGH', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
SELECT 'process-file-main', 'FIXED_RATE', 300000, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'process-file-main'
      AND trigger_type = 'FIXED_RATE'
      AND fixed_rate_ms = 300000
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'process-file-main', 'input.file.name', '${user.dir}/src/main/resources/data/sample.csv', 'STRING', '导入文件路径'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'process-file-main' AND param_name = 'input.file.name'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'process-file-main', 'batchDate', '', 'STRING', '批次日期（空表示当天）'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'process-file-main' AND param_name = 'batchDate'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'process-file-main', 'runMode', 'normal', 'STRING', '运行模式：normal/backfill'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'process-file-main' AND param_name = 'runMode'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'process-file-main', 'rerunId', '', 'STRING', '补跑标识'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'process-file-main' AND param_name = 'rerunId'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'process-file-main', 'priority', '5', 'INT', '任务优先级（数字越大越高）'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'process-file-main' AND param_name = 'priority'
);

-- 11. 原表导出主链路任务（dataExportJob）
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('data-export-main', 'dataExportJob', '原表导出主链路任务：按批次导出给下游', 'NORMAL', FALSE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
SELECT 'data-export-main', 'CRON', '0 1 0 * * ?', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger
    WHERE task_id = 'data-export-main'
      AND trigger_type = 'CRON'
      AND cron_expression = '0 1 0 * * ?'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'data-export-main', 'export.sql', 'select id, business_key, name, description, batch_date from imported_records where batch_date = ''2026-03-01''', 'STRING', '导出查询 SQL（单条 SELECT）'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'data-export-main' AND param_name = 'export.sql'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'data-export-main', 'output.file.name', 'export/data_export_20260301.csv', 'STRING', '导出文件名'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter
    WHERE task_id = 'data-export-main' AND param_name = 'output.file.name'
);

-- ========================================================
-- 结束
-- ========================================================
