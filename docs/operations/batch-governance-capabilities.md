# Batch 治理能力落地清单
> 中文名：批量治理能力落地清单（任务调度 + 应用治理）

本文对应 9 项能力逐一落地。BFP 定位仍是单体批量应用：调度、治理和执行在同一应用内完成，底层继续复用 Quartz + 本地队列 + Spring Batch；不引入 BFS 的独立 trigger/orchestrator/worker lease/outbox 架构。

## 1) DAG 编排与失败传播
- 依赖关系：`task_dependency`
- 新增字段：`dependency_timeout_ms`、`on_failure_action(FAIL/SKIP/IGNORE)`
- 跨日依赖：`dependency_batch_date_offset_days`（0=同日，-1=T-1，1=T+1），当前按自然日解析；`dependency_calendar_code` 仅作配置表达预留
- 代码：`DependencyResolver`、`TaskOrchestrationConfig`、`TaskSchedulerService`
- 运行态：`/ops/dag` 提供 DAG 拓扑快照

## 2) 批次状态机
- 状态：`READY/RUNNING/BLOCKED/SUCCESS/PARTIAL/FAILED/SKIPPED`
- 表：`task_execution_state`
- 代码：`TaskExecutionStatus`、`TaskExecutionStateService`
- 纠偏：`SchedulerStateReconciler` 定期将超时状态标记为 FAILED 并落 DLQ

## 3) 失败后恢复重跑
- 能力：按 executionId 或按 jobName 重启失败执行
- 代码：`BatchRecoveryService`、`FileImportJobHandler#batchRestartJob`
- 说明：Spring Batch restart 会从失败 Step 继续
- 账期级 replay：`batch_day_replay_session` + `batch_day_replay_entry`，入口 `/ops/batch-days/replays`，按 `ALL` / `ALL_FAILED` / `SUBSET_TASK_IDS` 复用现有手工重跑入队

## 3.1) 批量日主模型
- 批量日表：`batch_day_instance`
- 状态：`OPEN/FROZEN/SETTLING/SETTLED/REPLAYING/CLOSED`
- 入口：`GET /ops/batch-days`、`POST /ops/batch-days`、`POST /ops/batch-days/{id}/status`
- 边界：这是单体内的账期治理锚点，用于表达“哪一天在运行/冻结/重放”；不承担 BFS 中跨服务 trigger outbox、worker lease 或 result_version 的职责

## 4) 数据质量门禁
- 解析错误率门禁：`ParseErrorRateGateListener`
- 批后完整率门禁：`JobCompletionNotificationListener#evaluatePostImportQuality`
- 导入重复率门禁：`JobCompletionNotificationListener#evaluateDuplicateRate`（quality.maxDuplicateRate / quality.minDuplicateLines）
- 导出行数门禁：`JobCompletionNotificationListener#evaluatePostExportQuality`（quality.expectedRows / quality.minRows）
- 结果落库：`quality_gate_results`

## 5) 死信与补偿通道
- DLQ 字段：`retryable/manual_required/compensation_status/next_retry_at/replay_count`
- 补偿逻辑：仅重放“到期 + 可重试 + 非人工”记录；超上限转人工
- 代码：`DlqCompensationService`

## 6) 多实例幂等去重
- 幂等键标准化：`IdempotencyKeyBuilder`
- 跨实例去重：`execution_dedup_records` 唯一约束
- 代码：`TaskSchedulerService#isDuplicate`、`ImportJobRequestResolver`

## 7) 并行分片治理 + 背压
- 动态分片：`LaunchExecutor`
- 并发键限流：`SchedulerConcurrencyLimiter`
- 队列背压：`orchestration.scheduler.backpressure-*`
- 熔断：`TargetSystemCircuitBreaker`
- 运维快照：`/ops/scheduler`

## 8) 可观测性与告警阈值
- 指标：失败率、吞吐、DLQ积压、人工积压、重试积压
- Quartz 指标：`quartz_*`（misfire、jobs/triggers 数量、scheduler 状态）
- 代码：`BatchMetricsPublisher`、`BatchAlertEvaluator`、`QuartzMetricsRegistrar`
- 阈值：`batch.alert.*`

## 9) 多环境治理
- 推荐：`dev=internal/scheduler`，`prod=scheduler`
- 配置真源：`orchestration.config-source=db`
- 流程文档：`docs/operations/release-process.md`、`docs/operations/deploy-checklist.md`

## 10) 变更治理与审批
- 变更单：`ops_change_request`（支持 window_start/window_end、risk_level、impact_summary、rollback_plan）
- 审计：`ops_audit_log`、`task_execution_audit`
- 运维入口：`/ops/change-requests`、`/ops/task-audit`
