-- 清理早期 seed 中指向项目根目录 export/ 的导出默认值。
-- 导出文件应写入 batch.io.output-base-dir；任务参数只保留相对文件名或子目录。

UPDATE task_parameter
SET param_value = ''
WHERE task_id = 'file-export-daily'
  AND param_name = 'outputDir'
  AND param_value = 'export';

UPDATE task_parameter
SET param_value = regexp_replace(param_value, '^export/', '')
WHERE task_id = 'data-export-main'
  AND param_name = 'output.file.name'
  AND param_value LIKE 'export/%';
