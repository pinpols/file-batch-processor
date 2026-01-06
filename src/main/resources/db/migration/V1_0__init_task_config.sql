-- ========================================================
-- 任务配置相关表的 DDL 初始化脚本
-- 用于将任务配置从 YAML 迁移到数据库
-- ========================================================

-- ========================================================
-- 表 1: task_definition (任务定义表)
-- 用于存储 XXL-Job 任务的基本配置信息
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
VALUES ('partitioned-import-daily', 'CRON', '0 0 1 * * ?', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('partitioned-import-daily', 'batchDate', '', 'STRING', '批次日期（空表示使用当前日期）')
ON CONFLICT (task_id, param_name) DO NOTHING;

-- 2. 数据导出任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-export-daily', 'fileExportJob', '数据导出任务：每天导出数据到文件', 'NORMAL', FALSE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
VALUES ('file-export-daily', 'CRON', '0 0 2 * * ?', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('file-export-daily', 'batchDate', '', 'STRING', '批次日期'),
       ('file-export-daily', 'format', 'csv', 'STRING', '导出格式：csv/json/excel'),
       ('file-export-daily', 'outputDir', 'export', 'STRING', '输出目录')
ON CONFLICT (task_id, param_name) DO NOTHING;

-- 3. 文件接收监控任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-reception-monitor', 'fileReceptionJob', '文件接收监控：每 10 分钟检查一次待处理文件', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
VALUES ('file-reception-monitor', 'FIXED_RATE', 600000, TRUE)
ON CONFLICT DO NOTHING;

-- 4. 文件接收超时检测任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-reception-timeout-check', 'fileReceptionTimeoutJob', '文件接收超时检测：每 6 小时检查一次', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
VALUES ('file-reception-timeout-check', 'CRON', '0 0 */6 * * ?', TRUE)
ON CONFLICT DO NOTHING;

-- 5. 待分发文件处理任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-distribution-pending', 'fileDistributionJob', '文件分发：每 5 分钟处理待分发任务', 'HIGH', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
VALUES ('file-distribution-pending', 'FIXED_RATE', 300000, TRUE)
ON CONFLICT DO NOTHING;

-- 6. 文件分发重试任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-distribution-retry', 'fileDistributionRetryJob', '文件分发重试：每 15 分钟检查需要重试的任务', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
VALUES ('file-distribution-retry', 'FIXED_RATE', 900000, TRUE)
ON CONFLICT DO NOTHING;

-- 7. 文件分发超时检测任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('file-distribution-timeout', 'fileDistributionTimeoutJob', '文件分发超时检测：每 12 小时检查一次', 'NORMAL', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
VALUES ('file-distribution-timeout', 'CRON', '0 0 */12 * * ?', TRUE)
ON CONFLICT DO NOTHING;

-- ========================================================
-- 结束
-- ========================================================
