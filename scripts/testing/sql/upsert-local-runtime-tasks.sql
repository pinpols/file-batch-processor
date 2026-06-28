-- 本地全场景验证运行态任务配置。
-- 变量由脚本传入：
--   input_file   导入 CSV 绝对路径
--   output_file  导出 CSV 绝对路径

INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled, updated_at)
VALUES
  ('process-file-main', 'fileImportJob', '本地验证：文件导入主链路', 'HIGH', true, true, now()),
  ('data-export-main', 'dataExportJob', '本地验证：数据导出主链路', 'NORMAL', false, true, now())
ON CONFLICT (task_id) DO UPDATE
SET job_name = EXCLUDED.job_name,
    description = EXCLUDED.description,
    priority = EXCLUDED.priority,
    allow_parallel = EXCLUDED.allow_parallel,
    enabled = EXCLUDED.enabled,
    updated_at = now();

DELETE FROM task_trigger
WHERE task_id IN ('process-file-main', 'data-export-main');

INSERT INTO task_trigger (task_id, trigger_type, fixed_delay_ms, enabled, updated_at)
VALUES
  ('process-file-main', 'FIXED_DELAY', 3600000, true, now()),
  ('data-export-main', 'FIXED_DELAY', 3600000, true, now());

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description, updated_at)
VALUES
  ('process-file-main', 'input.file.name', :'input_file', 'STRING', '本地验证导入文件', now()),
  ('process-file-main', 'batchDate', '2026-03-01', 'STRING', '本地验证账期', now()),
  ('data-export-main', 'export.sql', 'select id, business_key, name, description, batch_date from imported_records where batch_date = ''2026-03-01'' order by id', 'STRING', '本地验证导出 SQL', now()),
  ('data-export-main', 'batchDate', '2026-03-01', 'STRING', '本地验证账期', now()),
  ('data-export-main', 'output.file.name', :'output_file', 'STRING', '本地验证导出文件', now())
ON CONFLICT (task_id, param_name) DO UPDATE
SET param_value = EXCLUDED.param_value,
    param_type = EXCLUDED.param_type,
    description = EXCLUDED.description,
    updated_at = now();
