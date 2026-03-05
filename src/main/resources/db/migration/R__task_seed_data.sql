-- ========================================================
-- Repeatable Seed: 仅初始化最小任务骨架（不写生产硬编码参数）
-- 说明：
-- 1) DDL 与 seed 分离，seed 走 repeatable 脚本
-- 2) 仅补缺，不覆盖人工配置
-- ========================================================

INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('process-file-main', 'processFileJob', '文件导入主链路任务（需运维配置参数）', 'HIGH', TRUE, FALSE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('data-export-main', 'dataExportJob', '原表导出主链路任务（需运维配置参数）', 'NORMAL', FALSE, FALSE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'process-file-main', 'input.file.name', '', 'STRING', '导入文件路径（必填）'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter WHERE task_id='process-file-main' AND param_name='input.file.name'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'data-export-main', 'export.sql', '', 'STRING', '导出 SQL（单条 SELECT，建议由运维配置）'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter WHERE task_id='data-export-main' AND param_name='export.sql'
);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
SELECT 'data-export-main', 'output.file.name', '', 'STRING', '导出目标文件名（必填）'
WHERE NOT EXISTS (
    SELECT 1 FROM task_parameter WHERE task_id='data-export-main' AND param_name='output.file.name'
);
