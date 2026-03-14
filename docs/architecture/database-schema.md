# Database Schema
> 中文名：数据库表结构说明

## 1. 说明与范围
- 数据库类型：PostgreSQL。
- 建表来源：`src/main/resources/db/migration/*.sql`（Flyway 管理）。
- 本文聚焦业务主表、治理表、调度表，不逐列展开 Quartz/Spring Batch 全量系统列。

## 2. 业务主链路（导入/导出）
| 表名 | 用途 | 关键字段 |
|---|---|---|
| `imported_records` | 导入主表（标准化后数据） | `business_key`, `batch_date`, `checksum`, `created_at` |
| `imported_records_partition` | 分区导入落表（大批量分区存储） | `business_key`, `batch_date`, `checksum` |
| `file_data` | 文件原始/中间数据 | `file_name`, `file_content`, `status` |
| `batch_run_records` | 批次运行记录 | `batch_id`, `status`, `start_time`, `end_time` |
| `job_execution` | 任务执行快照 | `task_id`, `job_name`, `status`, `started_at`, `finished_at` |

## 3. 调度配置与依赖
| 表名 | 用途 | 关键字段 |
|---|---|---|
| `task_definition` | 任务定义（任务维度） | `task_id`, `job_name`, `enabled`, `priority`, `tenant_id` |
| `task_trigger` | 触发器配置（CRON/FIXED_RATE/FIXED_DELAY/ONE_TIME） | `task_id`, `trigger_type`, `cron_expression`, `fixed_rate_ms`, `fixed_delay_ms`, `one_time_at` |
| `task_parameter` | 任务参数键值 | `task_id`, `param_key`, `param_value` |
| `task_dependency` | 任务依赖关系 | `task_id`, `depends_on_task_id`, `failure_action`, `timeout_ms` |
| `scheduler_queue_records` | 调度入队记录 | `task_id`, `enqueue_time`, `status` |
| `scheduler_leader_lock` | Leader 选举锁 | `lock_name`, `owner_id`, `expires_at` |

## 4. 编排运行状态与审计
| 表名 | 用途 | 关键字段 |
|---|---|---|
| `task_execution_state` | 任务状态机（READY/RUNNING/...） | `task_id`, `batch_date`, `status`, `attempt`, `next_retry_at`, `error_code` |
| `task_execution_audit` | 任务执行审计日志 | `task_id`, `execution_id`, `action`, `operator`, `created_at` |
| `ops_change_request` | 运维变更单 | `change_id`, `status`, `requester`, `approver` |
| `ops_audit_log` | 运维操作审计 | `module`, `operation`, `operator`, `result`, `created_at` |

## 5. 失败治理与质量门禁
| 表名 | 用途 | 关键字段 |
|---|---|---|
| `dlq_records` | 死信记录与补偿入口 | `idempotency_key`, `status`, `replay_count`, `next_retry_at`, `error_code` |
| `execution_dedup_records` | 跨实例幂等去重 | `dedup_key`, `window_start`, `window_end` |
| `quality_gate_results` | 质量门禁结果 | `job_name`, `batch_date`, `gate_type`, `passed`, `actual_value`, `threshold` |
| `target_system_circuit_state` | 下游目标系统熔断状态 | `target_system`, `state`, `window_size`, `failure_rate`, `cooldown_until` |

## 6. 对账与可追踪
| 表名 | 用途 | 关键字段 |
|---|---|---|
| `reconcile_run_records` | 对账批次记录 | `reconcile_date`, `status`, `summary` |
| `reconcile_diff_records` | 对账差异明细 | `reconcile_id`, `diff_type`, `source_value`, `target_value` |
| `record_trace` | 记录级链路追踪 | `trace_id`, `business_key`, `stage`, `event_time` |
| `job_log_records` | 作业日志聚合 | `job_name`, `execution_id`, `level`, `message`, `created_at` |

## 7. DAG 编排
| 表名 | 用途 | 关键字段 |
|---|---|---|
| `dag_definition` | DAG 定义头 | `dag_id`, `dag_name`, `enabled` |
| `dag_node` | DAG 节点 | `dag_id`, `node_id`, `task_id`, `node_type` |
| `dag_run` | DAG 运行实例 | `dag_id`, `run_id`, `status`, `started_at`, `finished_at` |
| `dag_node_run` | DAG 节点运行实例 | `run_id`, `node_id`, `status`, `attempt`, `error_message` |

## 8. Quartz 持久化表（调度内核）
- 表前缀：`qrtz_`，由 `V1_20__quartz_postgresql_tables.sql` 初始化。
- 核心表：`qrtz_job_details`, `qrtz_triggers`, `qrtz_cron_triggers`, `qrtz_simple_triggers`, `qrtz_fired_triggers`, `qrtz_locks` 等。
- 注意：业务代码应优先通过 Quartz API 操作任务，不建议常态化直接改 `qrtz_*`。

## 9. Spring Batch 元数据表
- 由 `V1_24__spring_batch_metadata_tables.sql` 初始化。
- 核心表：`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION` 及其上下文/参数表。
- 用途：支持 Job/Step 执行历史、断点续跑、执行参数追踪。

## 10. 推荐查询入口
- 导入链路排障：`task_execution_state` + `dlq_records` + `job_log_records`。
- 调度排障：`scheduler_leader_lock` + `scheduler_queue_records` + `qrtz_triggers`。
- 质量排障：`quality_gate_results` + `reconcile_diff_records`。
