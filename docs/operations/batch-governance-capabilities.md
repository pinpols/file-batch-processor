# Batch 治理能力落地清单
> 中文名：批量治理能力落地清单（任务调度 + 应用治理）

本文对应 9 项能力逐一落地，建议生产使用任务调度系统触发，应用负责治理。

## 1) DAG 编排与失败传播
- 依赖关系：`task_dependency`
- 新增字段：`dependency_timeout_ms`、`on_failure_action(FAIL/SKIP/IGNORE)`
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
- 流程文档：`docs/release-process.md`、`docs/ops/deploy-checklist.md`

## 10) 变更治理与审批
- 变更单：`ops_change_request`（支持 window_start/window_end、risk_level、impact_summary、rollback_plan）
- 审计：`ops_audit_log`、`task_execution_audit`
- 运维入口：`/ops/change-requests`、`/ops/task-audit`
