-- 增强测试数据种子脚本
-- 包含多种业务场景的真实数据

-- 清理现有数据
DELETE FROM imported_record_partitioned WHERE batch_date >= '2026-03-01';
DELETE FROM imported_records WHERE batch_date >= '2026-03-01';
DELETE FROM task_execution_state WHERE batch_date >= '2026-03-01';
DELETE FROM batch_run_record WHERE created_at >= '2026-03-01';

-- 插入基础导入记录
INSERT INTO imported_records (business_key, name, description, batch_date, created_at, updated_at) VALUES
    ('FIN001:2026-03-01', '张三', '正常交易记录', '2026-03-01', '2026-03-01 10:30:00', '2026-03-01 10:30:00'),
    ('FIN002:2026-03-01', '李四', '高级付款记录', '2026-03-01', '2026-03-01 11:15:00', '2026-03-01 11:15:00'),
    ('FIN003:2026-03-01', '王五', '待处理交易', '2026-03-01', '2026-03-01 12:00:00', '2026-03-01 12:00:00'),
    ('FIN004:2026-03-01', '赵六', '失败交易记录', '2026-03-01', '2026-03-01 13:45:00', '2026-03-01 13:45:00'),
    ('FIN005:2026-03-01', '钱七', '已完成交易', '2026-03-01', '2026-03-01 14:20:00', '2026-03-01 14:20:00');

-- 插入分区导入记录
INSERT INTO imported_record_partitioned (business_key, name, description, batch_date, created_at, updated_at) VALUES
    ('FIN001:2026-03-01', '张三', '正常交易记录', '2026-03-01', '2026-03-01 10:30:00', '2026-03-01 10:30:00'),
    ('FIN002:2026-03-01', '李四', '高级付款记录', '2026-03-01', '2026-03-01 11:15:00', '2026-03-01 11:15:00'),
    ('FIN003:2026-03-01', '王五', '待处理交易', '2026-03-01', '2026-03-01 12:00:00', '2026-03-01 12:00:00'),
    ('FIN004:2026-03-01', '赵六', '失败交易记录', '2026-03-01', '2026-03-01 13:45:00', '2026-03-01 13:45:00'),
    ('FIN005:2026-03-01', '钱七', '已完成交易', '2026-03-01', '2026-03-01 14:20:00', '2026-03-01 14:20:00');

-- 插入任务执行状态
INSERT INTO task_execution_state (task_id, batch_date, rerun_id, tenant_id, biz_domain, status, attempt, max_attempts, next_retry_at, window_start, window_end, last_error, error_code, created_at, updated_at) VALUES
    ('processFileJob', '2026-03-01', '', 'tenant-001', 'finance', 'COMPLETED', 1, 3, NULL, '2026-03-01 09:00:00', '2026-03-01 18:00:00', NULL, NULL, '2026-03-01 09:00:00', '2026-03-01 10:30:00'),
    ('dataExportJob', '2026-03-01', '', 'tenant-001', 'finance', 'READY', 0, 3, '2026-03-01 15:00:00', '2026-03-01 14:00:00', '2026-03-01 16:00:00', NULL, NULL, '2026-03-01 14:00:00', '2026-03-01 14:00:00'),
    ('reconcileJob', '2026-03-01', '', 'tenant-001', 'finance', 'FAILED', 2, 3, '2026-03-01 16:30:00', '2026-03-01 16:00:00', '2026-03-01 17:00:00', '数据对账失败', 'RECONCILE_MISMATCH', '2026-03-01 16:00:00', '2026-03-01 16:15:00');

-- 插入批次运行记录
INSERT INTO batch_run_record (job_name, batch_date, run_id, status, total_records, success_records, failed_records, start_time, end_time, created_at, updated_at) VALUES
    ('processFileJob', '2026-03-01', 'run-001', 'COMPLETED', 1000, 995, 5, '2026-03-01 09:00:00', '2026-03-01 10:30:00', '2026-03-01 09:00:00', '2026-03-01 10:30:00'),
    ('dataExportJob', '2026-03-01', 'run-002', 'RUNNING', 500, 480, 20, '2026-03-01 14:00:00', NULL, '2026-03-01 14:00:00', '2026-03-01 14:00:00'),
    ('reconcileJob', '2026-03-01', 'run-003', 'FAILED', 1000, 980, 20, '2026-03-01 16:00:00', '2026-03-01 16:15:00', '2026-03-01 16:00:00', '2026-03-01 16:15:00');

-- 插入死信队列记录
INSERT INTO dlq_record (job_name, params, error_message, error_code, handled, created_at, updated_at) VALUES
    ('processFileJob', 'businessKey=FIN006&name=孙八&description=高级付款&batchDate=2026-03-01', '数据格式错误：金额字段包含非法字符', 'PARSE_ERROR', false, '2026-03-01 15:10:00', '2026-03-01 15:10:00'),
    ('dataExportJob', 'export.sql=SELECT * FROM imported_records WHERE batch_date=2026-03-01', '数据库连接超时', 'DB_TIMEOUT', false, '2026-03-01 14:30:00', '2026-03-01 14:30:00'),
    ('reconcileJob', 'sourceFile=financial_data_20260301.csv&targetTable=imported_records', '源文件与数据库记录不匹配', 'RECONCILE_MISMATCH', true, '2026-03-01 16:20:00', '2026-03-01 16:25:00');

-- 插入记录追踪
INSERT INTO record_trace (trace_id, task_id, batch_date, run_id, business_key, operation, status, start_time, end_time, created_at, updated_at) VALUES
    ('trace-001', 'processFileJob', '2026-03-01', 'run-001', 'FIN001', 'IMPORT', 'SUCCESS', '2026-03-01 09:05:00', '2026-03-01 09:05:30', '2026-03-01 09:05:00', '2026-03-01 09:05:30'),
    ('trace-002', 'processFileJob', '2026-03-01', 'run-001', 'FIN002', 'IMPORT', 'SUCCESS', '2026-03-01 09:10:00', '2026-03-01 09:10:25', '2026-03-01 09:10:00', '2026-03-01 09:10:25'),
    ('trace-003', 'dataExportJob', '2026-03-01', 'run-002', 'FIN001', 'EXPORT', 'RUNNING', '2026-03-01 14:05:00', NULL, '2026-03-01 14:05:00', '2026-03-01 14:05:00');

-- 插入质量门禁结果
INSERT INTO quality_gate_results (task_id, batch_date, run_id, gate_type, threshold_value, actual_value, status, error_message, created_at, updated_at) VALUES
    ('processFileJob', '2026-03-01', 'run-001', 'PARSE_ERROR_RATE', 5.0, 0.5, 'PASSED', NULL, '2026-03-01 10:30:00', '2026-03-01 10:30:00'),
    ('dataExportJob', '2026-03-01', 'run-002', 'EXPORT_SUCCESS_RATE', 95.0, 96.0, 'PASSED', NULL, '2026-03-01 14:00:00', '2026-03-01 14:00:00'),
    ('reconcileJob', '2026-03-01', 'run-003', 'RECONCILE_MATCH_RATE', 98.0, 98.0, 'PASSED', NULL, '2026-03-01 16:15:00', '2026-03-01 16:15:00');

-- 插入任务定义
INSERT INTO task_definition (task_id, job_name, description, tenant_id, biz_domain, priority, enabled, created_at, updated_at) VALUES
    ('processFileJob', 'processFileJob', '文件处理作业', 'tenant-001', 'finance', 'NORMAL', true, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('dataExportJob', 'dataExportJob', '数据导出作业', 'tenant-001', 'finance', 'HIGH', true, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('reconcileJob', 'reconcileJob', '数据对账作业', 'tenant-001', 'finance', 'NORMAL', true, '2026-03-01 08:00:00', '2026-03-01 08:00:00');

-- 插入任务触发器
INSERT INTO task_trigger (task_id, trigger_type, cron_expression, fixed_rate_ms, fixed_delay_ms, one_time_at, enabled, created_at, updated_at) VALUES
    ('processFileJob', 'CRON', '0 30 9 * * ?', NULL, NULL, NULL, true, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('dataExportJob', 'FIXED_DELAY', NULL, NULL, 3600000, NULL, true, '2026-03-01 08:00:00', '2026-03-01 08:00:00'),
    ('reconcileJob', 'ONE_TIME', NULL, NULL, NULL, '2026-03-01 17:00:00', false, '2026-03-01 08:00:00', '2026-03-01 08:00:00');

COMMIT;
