# SLO / SLA 基线
> 中文名：SLO/SLA 指标说明

## 目标（SLO）
- 可用性：月度 `>= 99.9%`。
- 导入成功率：日均 `>= 99.5%`。
- 导出成功率：日均 `>= 99.5%`。
- 故障恢复时间（MTTR）：P1 `<= 30 分钟`。
- 告警响应时间：P1 `<= 5 分钟`。

## 观测指标
- `batch_recent_failure_count` / `batch_recent_completed_count`
- `batch_dlq_backlog`
- `batch_avg_throughput_rps`
- `batch_blocked_task_count`
- `batch_sla_duration_breach_count`
- `scheduler_queue_sla_breach_total`
- `up{job="file-batch-processor"}`

## SLA 建议
- 对内：工作时段 7x12，关键窗口 7x24 值守。
- 对外：批次截止窗口内交付成功率 `>= 99%`。

## 违约处理
- 达不到 SLO 时，必须触发复盘并输出修复计划（责任人/日期/验收指标）。
