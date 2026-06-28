# 质量门禁运维手册
> 中文名：质量门禁运行手册

## 概述
导入任务在解析阶段会校验错误率，超过阈值直接失败，避免脏数据全量入库。支持全局默认值与按 jobName 覆盖，结果持久化到 `quality_gate_results` 表，提供查询 API。
此外支持导入重复率门禁与导出行数门禁（基于任务参数）。

## 关键配置
| 配置项 | 说明 | 默认 | 生产建议 |
|---|---|---|---|
| `batch.import.parse-error.max-rate` | 全局解析错误率上限 | 0.2 | 0.05~0.2 |
| `batch.import.parse-error.min-lines` | 最小样本行数 | 50 | 50~200 |
| `batch.import.parse-error.rules.<jobName>.max-rate/min-lines` | 按 jobName 覆盖 | 空 | 按需覆盖 |
| `batch.import.duplicate.max-rate` | 全局导入重复率上限 | 0.0 | 0.0~0.01 |
| `batch.import.duplicate.min-lines` | 导入重复率最小样本 | 100 | 100~500 |

## 行为说明
- 错误率 = 解析错误行数 / (读取行数 + 解析错误行数)
- 当总行数 < `min-lines` 时，不触发失败（样本不足）
- 超阈值时 Spring Batch Step 直接失败，任务标记为 FAILED
- 导入重复率门禁仅在导入成功后评估，不会中断主流程

## 数据落表
`quality_gate_results` 表字段：
- `job_name`、`job_execution_id`、`step_execution_id`
- `total_read`、`parse_error`、`error_rate`
- `status`：PASSED/FAILED
- `threshold_max_rate`、`threshold_min_lines`（实际生效阈值）
- `created_at`

## 查询 API
- 最近 50 条：`GET /api/quality/gates`
- 按 jobName 最近 200 条：`GET /api/quality/gates/job/{jobName}`

## 监控指标
- `import_parse_error_gate_failed_total`：质量门禁失败次数（按 jobName 标签）
- `quality_gate_results`：导入重复率/导出行数门禁结果同表记录

## 排障步骤

### 1) 查看失败记录
```sql
SELECT job_name, job_execution_id, total_read, parse_error, error_rate, status, created_at
FROM quality_gate_results
WHERE status = 'FAILED'
ORDER BY created_at DESC
LIMIT 20;
```

### 2) 对比阈值
检查 `threshold_max_rate` 与 `threshold_min_lines` 是否符合预期。

### 3) 检查原始文件
通过 `job_execution_id` 关联日志或输入文件，确认数据质量。

### 4) 调整阈值
- 临时调整：修改 `application.yml` 或环境变量后重启
- 按任务覆盖：在 `rules.<jobName>` 下配置更宽松阈值
- 导出行数门禁：通过任务参数 `quality.expectedRows` 或 `quality.minRows`

### 5) 重新运行
使用任务调度系统或本地编排重新触发，`runMode=backfill` + `rerunId`。

## 告警建议
- 告警：`import_parse_error_gate_failed_total` 增加
- 持续失败：同一 jobName 多次失败时升级

## 相关文档
- 配置矩阵：`docs/architecture/configuration-matrix.md`
- 全局运维手册：`docs/operations/runbook.md`
