-- 增强测试数据种子脚本
-- 包含多种业务场景的真实数据

-- 清理现有数据
DELETE FROM imported_records_partition WHERE batch_date >= '2026-03-01';
DELETE FROM imported_records WHERE batch_date >= '2026-03-01';
DELETE FROM task_execution_state WHERE batch_date >= '2026-03-01';
DELETE FROM batch_run_records WHERE created_at >= '2026-03-01';

-- 插入基础导入记录
INSERT INTO imported_records (business_key, name, description, batch_date, created_at, updated_at) VALUES
    ('FIN001:2026-03-01', '张三', '正常交易记录', '2026-03-01', '2026-03-01 10:30:00', '2026-03-01 10:30:00'),
    ('FIN002:2026-03-01', '李四', '高级付款记录', '2026-03-01', '2026-03-01 11:15:00', '2026-03-01 11:15:00'),
    ('FIN003:2026-03-01', '王五', '待处理交易', '2026-03-01', '2026-03-01 12:00:00', '2026-03-01 12:00:00'),
    ('FIN004:2026-03-01', '赵六', '失败交易记录', '2026-03-01', '2026-03-01 13:45:00', '2026-03-01 13:45:00'),
    ('FIN005:2026-03-01', '钱七', '已完成交易', '2026-03-01', '2026-03-01 14:20:00', '2026-03-01 14:20:00')
ON CONFLICT (business_key, batch_date) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = EXCLUDED.updated_at;

-- 插入分区导入记录
INSERT INTO imported_records_partition (business_key, name, description, batch_date, partition_key, created_at, updated_at) VALUES
    ('FIN001:2026-03-01', '张三', '正常交易记录', '2026-03-01', 'p0', '2026-03-01 10:30:00', '2026-03-01 10:30:00'),
    ('FIN002:2026-03-01', '李四', '高级付款记录', '2026-03-01', 'p0', '2026-03-01 11:15:00', '2026-03-01 11:15:00'),
    ('FIN003:2026-03-01', '王五', '待处理交易', '2026-03-01', 'p1', '2026-03-01 12:00:00', '2026-03-01 12:00:00'),
    ('FIN004:2026-03-01', '赵六', '失败交易记录', '2026-03-01', 'p1', '2026-03-01 13:45:00', '2026-03-01 13:45:00'),
    ('FIN005:2026-03-01', '钱七', '已完成交易', '2026-03-01', 'p2', '2026-03-01 14:20:00', '2026-03-01 14:20:00')
ON CONFLICT (business_key, batch_date, partition_key) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = EXCLUDED.updated_at;

-- 插入任务执行状态
INSERT INTO task_execution_state (task_id, batch_date, rerun_id, tenant_id, biz_domain, status, attempt, max_attempts, next_retry_at, window_start, window_end, last_error, error_code, created_at, updated_at) VALUES
    ('fileImportJob', '2026-03-01', '', 'tenant-001', 'finance', 'SUCCESS', 1, 3, NULL, '2026-03-01 09:00:00', '2026-03-01 18:00:00', NULL, NULL, '2026-03-01 09:00:00', '2026-03-01 10:30:00'),
    ('dataExportJob', '2026-03-01', '', 'tenant-001', 'finance', 'READY', 0, 3, '2026-03-01 15:00:00', '2026-03-01 14:00:00', '2026-03-01 16:00:00', NULL, NULL, '2026-03-01 14:00:00', '2026-03-01 14:00:00'),
    ('reconcileJob', '2026-03-01', '', 'tenant-001', 'finance', 'FAILED', 2, 3, '2026-03-01 16:30:00', '2026-03-01 16:00:00', '2026-03-01 17:00:00', '数据对账失败', 'RECONCILE_MISMATCH', '2026-03-01 16:00:00', '2026-03-01 16:15:00');

-- 插入批次运行记录
INSERT INTO batch_run_records (
    job_execution_id, job_name, batch_date, tenant_id, biz_domain, job_params, status,
    start_time, end_time, read_count, write_count, skip_count, parse_error_count,
    duration_ms, throughput_rps, quality_passed, error_message, created_at, updated_at
) VALUES
    (91001, 'fileImportJob', '2026-03-01', 'tenant-001', 'finance', 'run=run-001', 'COMPLETED',
     '2026-03-01 09:00:00', '2026-03-01 10:30:00', 1000, 995, 5, 5, 5400000, 0.18, true, NULL,
     '2026-03-01 09:00:00', '2026-03-01 10:30:00'),
    (91002, 'dataExportJob', '2026-03-01', 'tenant-001', 'finance', 'run=run-002', 'RUNNING',
     '2026-03-01 14:00:00', NULL, 500, 480, 20, 0, 0, 0.0, true, NULL,
     '2026-03-01 14:00:00', '2026-03-01 14:00:00'),
    (91003, 'reconcileJob', '2026-03-01', 'tenant-001', 'finance', 'run=run-003', 'FAILED',
     '2026-03-01 16:00:00', '2026-03-01 16:15:00', 1000, 980, 20, 0, 900000, 1.08, false, '数据对账失败',
     '2026-03-01 16:00:00', '2026-03-01 16:15:00')
ON CONFLICT (job_execution_id) DO UPDATE
SET status = EXCLUDED.status,
    read_count = EXCLUDED.read_count,
    write_count = EXCLUDED.write_count,
    updated_at = EXCLUDED.updated_at;

-- 插入死信队列记录
INSERT INTO dlq_records (job_name, params, error_message, error_code, handled, created_at, updated_at) VALUES
    ('fileImportJob', 'businessKey=FIN006&name=孙八&description=高级付款&batchDate=2026-03-01', '数据格式错误：金额字段包含非法字符', 'PARSE_ERROR', false, '2026-03-01 15:10:00', '2026-03-01 15:10:00'),
    ('dataExportJob', 'export.sql=SELECT * FROM imported_records WHERE batch_date=2026-03-01', '数据库连接超时', 'DB_TIMEOUT', false, '2026-03-01 14:30:00', '2026-03-01 14:30:00'),
    ('reconcileJob', 'sourceFile=financial_data_20260301.csv&targetTable=imported_records', '源文件与数据库记录不匹配', 'RECONCILE_MISMATCH', true, '2026-03-01 16:20:00', '2026-03-01 16:25:00');

-- 插入记录追踪
INSERT INTO record_trace (business_key, batch_date, job_name, job_execution_id, event_type, status, message, created_at) VALUES
    ('FIN001:2026-03-01', '2026-03-01', 'fileImportJob', 91001, 'IMPORT', 'SUCCESS', 'run-001 import success', '2026-03-01 09:05:30'),
    ('FIN002:2026-03-01', '2026-03-01', 'fileImportJob', 91001, 'IMPORT', 'SUCCESS', 'run-001 import success', '2026-03-01 09:10:25'),
    ('FIN001:2026-03-01', '2026-03-01', 'dataExportJob', 91002, 'EXPORT', 'RUNNING', 'run-002 export running', '2026-03-01 14:05:00');

-- 插入质量门禁结果
INSERT INTO quality_gate_results (
    gate_type, job_name, batch_date, job_execution_id, read_count, parse_error_count,
    total_count, error_rate, max_rate, min_lines, status, message, created_at
) VALUES
    ('PARSE_ERROR_RATE', 'fileImportJob', '2026-03-01', 91001, 1000, 5, 1000, 0.005, 0.05, 50, 'PASSED', NULL, '2026-03-01 10:30:00'),
    ('EXPORT_SUCCESS_RATE', 'dataExportJob', '2026-03-01', 91002, 500, 0, 500, 0.0, 0.05, 50, 'PASSED', NULL, '2026-03-01 14:00:00'),
    ('RECONCILE_MATCH_RATE', 'reconcileJob', '2026-03-01', 91003, 1000, 20, 1000, 0.02, 0.05, 50, 'PASSED', NULL, '2026-03-01 16:15:00');

-- 插入任务定义
INSERT INTO task_definition (task_id, job_name, description, tenant_id, biz_domain, priority, enabled, created_at, updated_at) VALUES
    ('fileImportJob', 'fileImportJob', '文件处理作业样例', 'tenant-001', 'finance', 'NORMAL', false, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('dataExportJob', 'dataExportJob', '数据导出作业样例', 'tenant-001', 'finance', 'HIGH', false, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('reconcileJob', 'reconcileJob', '数据对账作业样例', 'tenant-001', 'finance', 'NORMAL', false, '2026-03-01 08:00:00', '2026-03-01 08:00:00')
ON CONFLICT (task_id) DO UPDATE
SET job_name = EXCLUDED.job_name,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id,
    biz_domain = EXCLUDED.biz_domain,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    updated_at = EXCLUDED.updated_at;

-- 插入任务触发器
DELETE FROM task_trigger WHERE task_id IN ('fileImportJob', 'dataExportJob', 'reconcileJob');

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, fixed_rate_ms, fixed_delay_ms, one_time_at, enabled, created_at, updated_at) VALUES
    ('fileImportJob', 'CRON', '0 30 9 * * ?', NULL, NULL, NULL, false, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('dataExportJob', 'FIXED_DELAY', NULL, NULL, 3600000, NULL, false, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('reconcileJob', 'ONE_TIME', NULL, NULL, NULL, '2026-03-01 17:00:00', false, '2026-03-01 08:00:00', '2026-03-01 08:00:00');
