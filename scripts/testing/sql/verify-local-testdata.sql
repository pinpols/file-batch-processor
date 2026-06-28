-- 本地全场景验证读模型检查。

SELECT 'imported_records_20260301' AS check_name, COUNT(*) AS value
FROM imported_records
WHERE batch_date = '2026-03-01';

SELECT 'partitioned_records_20260301' AS check_name, COUNT(*) AS value
FROM imported_records_partition
WHERE batch_date = '2026-03-01';

SELECT 'trace_records_20260301' AS check_name, COUNT(*) AS value
FROM record_trace
WHERE batch_date = '2026-03-01';

SELECT 'dlq_backlog' AS check_name, COUNT(*) AS value
FROM dlq_records
WHERE handled = false;

SELECT 'quality_gates_20260301' AS check_name, COUNT(*) AS value
FROM quality_gate_results
WHERE batch_date = '2026-03-01';

SELECT 'task_definitions_enabled' AS check_name, COUNT(*) AS value
FROM task_definition
WHERE task_id IN ('process-file-main', 'data-export-main') AND enabled = true;
