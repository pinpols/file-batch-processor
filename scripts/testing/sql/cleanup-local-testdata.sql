-- 本地全场景验证清理脚本。
-- 仅清理 2026-03-01 之后的测试账期和本地脚本专用任务，不影响人工配置。

DELETE FROM quality_gate_results WHERE batch_date >= '2026-03-01';
DELETE FROM record_trace WHERE batch_date >= '2026-03-01';
DELETE FROM dlq_records WHERE created_at >= '2026-03-01';
DELETE FROM reconcile_diff_records WHERE created_at >= '2026-03-01';
DELETE FROM reconcile_run_records WHERE batch_date >= '2026-03-01';
DELETE FROM batch_run_records WHERE batch_date >= '2026-03-01' OR created_at >= '2026-03-01';
DELETE FROM task_execution_state WHERE batch_date >= '2026-03-01';
DELETE FROM imported_records_partition WHERE batch_date >= '2026-03-01';
DELETE FROM imported_records WHERE batch_date >= '2026-03-01';

DELETE FROM task_parameter
WHERE task_id IN ('process-file-main', 'data-export-main', 'fileImportJob', 'dataExportJob', 'reconcileJob');

DELETE FROM task_trigger
WHERE task_id IN ('process-file-main', 'data-export-main', 'fileImportJob', 'dataExportJob', 'reconcileJob');

UPDATE task_definition
SET enabled = false, updated_at = now()
WHERE task_id IN ('process-file-main', 'data-export-main', 'fileImportJob', 'dataExportJob', 'reconcileJob');
