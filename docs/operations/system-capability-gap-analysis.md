# 批量调度系统能力与缺口分析

日期：2026-03-15  
范围：基于当前代码仓库实现，对照“成熟 Java 批量调度系统”常见能力清单做差距分析。  
结论口径：`已具备 / 部分具备 / 缺失`，重点关注生产可运营性，而不是单纯框架是否齐全。

配套实施计划见：[批量调度系统补齐实施计划](./system-capability-completion-plan.md)

## 1. 总体判断

当前系统已经不是简单 Demo，已经具备一套可运行的批量平台骨架，核心能力包括：

- Quartz JDBC + DB 配置源的任务调度
- Spring Batch 作业执行
- 文件接收、文件分发、文件导出、分区导入
- 任务状态持久化、执行审计、DLQ 补偿
- 任务依赖编排、去重、队列幂等、Leader 协调
- Prometheus 指标、基础告警、运维接口、变更审批

但如果按“成熟生产系统”标准看，当前系统仍然有一个明显特征：

`调度和执行框架比较完整，业务任务实例层和文件资产管理层还不够完整。`

换句话说，现在系统更像：

- 调度层：比较强
- 批处理执行层：中上
- 文件服务层：基础可用，但离“受控文件资产管理”还有差距
- 运维治理层：有骨架，但还没完全闭环

## 2. 能力矩阵

| 模块 | 现状判断 | 说明 |
| --- | --- | --- |
| 调度服务 | 已具备 | Quartz JDBC、DB 任务配置、手工触发、依赖 DAG、misfire、Leader/cluster 模式都有 |
| 批处理执行服务 | 已具备 | Spring Batch 作业、批次运行审计、恢复重启、DLQ 重放都有 |
| 文件服务 | 部分具备 | 有接收、完整性校验、分发、导出，但缺统一文件资产模型、版本化、归档生命周期 |
| 任务实例服务 | 部分具备 | 有 `task_execution_state`、`task_execution_audit`、`batch_run_records`，但缺统一业务任务实例模型 |
| 状态流转服务 | 部分具备 | 有状态字段和状态表，但没有统一状态机服务来约束合法迁移 |
| 重试与补偿服务 | 部分具备 | 有 DLQ 重放、任务重启、分发重试；缺文件级/步骤级/批次级统一补偿编排 |
| 幂等控制服务 | 已具备 | DB 唯一约束去重、队列幂等、执行幂等都有 |
| 参数配置服务 | 部分具备 | `task_definition/task_trigger/task_parameter` 完整，但缺版本化、环境层级、批量参数治理 |
| 告警通知服务 | 部分具备 | 有阈值告警和 webhook；缺文件 SLA/回执/磁盘等更细告警与多渠道 |
| 日志与审计服务 | 部分具备 | 有任务审计、Ops 审计、记录轨迹；缺文件操作全链路审计 |
| 监控指标服务 | 已具备 | Micrometer/Prometheus/Grafana 资产已补齐，指标面较完整 |
| 权限与运维台 | 部分具备 | 有角色、看板、触发、开关、审批；缺文件运维、补跑台、下载鉴权台 |
| 依赖编排服务 | 已具备 | DAG、依赖、阻塞、拓扑图、失败治理具备 |
| 超时与死信处理 | 已具备 | stale state reconcile、DLQ、重放、人工介入标记具备 |
| 对账校验服务 | 部分具备 | 有 reconcile 相关表和接口，但还不是统一的数据校验中心 |
| 外围适配服务 | 部分具备 | HTTP/SFTP/FTP 分发已做，入站和回执适配尚未统一抽象 |
| 归档清理服务 | 部分具备 | DB retention 有，文件归档/冷存储/版本保留不足 |
| 资源管理服务 | 部分具备 | 并发限制、背压、熔断、Leader 有，但磁盘/临时目录治理不足 |

## 3. 现有能力分析

### 3.1 调度与编排

当前调度层是系统最成熟的部分之一。

已具备：

- DB 配置源：`task_definition`、`task_trigger`、`task_parameter`
- Quartz JDBC 持久化调度
- DAG 依赖关系、拓扑排序、图快照
- queue 幂等、执行去重、leader 锁、misfire 处理
- 运维触发和任务开关

代码依据：

- `TaskSchedulerService`
- `TaskGraphManager`
- `TaskConfigService`
- `SchedulerLeaderService`
- `MisfirePolicyService`

判断：

- 这层已经具备“批量编排平台”的基本雏形
- 主要缺口不在调度框架，而在业务对象建模和文件治理

### 3.2 批处理执行

当前批处理执行层也比较完整。

已具备：

- Spring Batch Job/Step 执行
- `batch_run_records` 记录技术执行结果
- `task_execution_state` 记录编排状态
- `task_execution_audit` 记录审计事件
- `BatchRecoveryService` 支持失败重启
- `DlqCompensationService` 支持死信重放

判断：

- 现在已经有“技术执行元数据”
- 但还缺“业务任务实例服务”

缺口表现：

- 还没有统一的 `job_instance / job_step_instance / job_execution_log`
- 任务触发来源、业务日期、关联文件、补跑原因、人工操作人等信息分散在多张表或参数里
- 现在更偏“技术执行记录”，不是“业务任务中心”

### 3.3 文件服务

当前文件服务是“可用”，但还不算“成熟文件资产管理层”。

已具备：

- 文件接收队列表：`file_reception_queue`
- 文件分发表：`file_distribution_task`
- 文件大小 + 哈希校验
- 文件接收状态、失败重试、超时扫描
- HTTP/SFTP/FTP 分发
- 导出文件生成和基础文件信息读取

代码依据：

- `FileReceptionService`
- `FileDistributionService`
- `FileExportService`

但缺口比较明显：

1. 没有统一 `file_record`

- 现在入站和出站是两张业务表
- 缺统一文件编号、原始名、存储名、存储路径、版本、归档标记、删除标记、业务日期、批次号、字符集等完整元数据

2. 半文件防护不足

- 当前 `receiveFile(...)` 只检查“文件存在 + 计算 hash”
- 没有 `.part/.done` 协议
- 没有“文件大小稳定后再处理”的机制
- 没有原子 rename 约束

3. 文件判重偏弱

- 当前接收表唯一约束主要是 `file_name`
- 缺“文件名 + hash + 来源系统 + 业务日期”等复合判重策略
- 无法稳妥处理“同名不同内容”或“不同名同内容”

4. 文件生命周期不完整

- 缺文件版本化
- 缺归档时间、归档位置、可删除标记
- 缺出站文件历史版本管理

5. 文件状态机不统一

- 接收文件状态和分发任务状态都存在
- 但状态迁移散落在服务方法里，没有统一状态机服务

6. 大文件策略不完整

- 接收端 hash 计算是流式的，这点是好的
- 但导出端 `fileExportTasklet` 会把结果先拼成 `String[][]`
- 对大批量导出仍有内存放大风险

结论：

- 文件服务当前更像“文件接收/分发功能”
- 还不是“受控文件资产管理层”

## 4. 你重点关心的能力，当前缺什么

### 4.1 任务实例服务

当前状态：`部分具备`

已有：

- `task_execution_state`
- `task_execution_audit`
- `batch_run_records`

缺失：

- 统一业务任务实例主表
- step 级业务实例表
- 文件、批次、业务日期、触发来源、操作人、补跑原因等统一关联

建议补：

- `job_instance`
- `job_step_instance`
- `job_execution_log`

这层要成为“业务视角任务中心”，而不是继续把 Quartz/Spring Batch 元数据直接暴露给运维。

### 4.2 状态机 / 流转控制服务

当前状态：`部分具备`

已有：

- `task_execution_state.status`
- `file_reception_queue.status`
- `file_distribution_task.status`
- 数据库约束里有部分状态校验

缺失：

- 统一状态枚举和迁移规则
- 非法迁移拦截
- 状态迁移日志标准化

建议补：

- 任务状态机服务
- 文件状态机服务
- 明确终态、中间态、补偿态

### 4.3 重试与补偿服务

当前状态：`部分具备`

已有：

- DLQ 重放
- Batch 重启
- 文件分发重试
- stale state reconcile

缺失：

- 文件级重处理
- 指定步骤补偿
- 指定业务日期补跑的统一入口
- 补偿策略模板化

建议补：

- `RetryCompensationService`
- 文件重处理 API
- 批次补跑 API
- step 级补偿命令模型

### 4.4 幂等控制服务

当前状态：`已具备`

已有：

- `ExecutionDedupService`
- `SchedulerQueueService`
- DB 唯一约束去重

但文件幂等仍不够：

- 文件接收还需要内容级判重
- 下游分发也需要“同一文件同一目标系统同一版本”幂等键

### 4.5 参数配置服务

当前状态：`部分具备`

已有：

- DB 任务配置三表
- 运维变更申请/审批/执行

缺失：

- 参数版本历史
- 环境级覆盖策略
- 生效时间控制
- 配置比对和回滚体验

建议补：

- 参数版本表
- 参数快照
- 配置回滚记录

### 4.6 告警通知服务

当前状态：`部分具备`

已有：

- failure rate、DLQ backlog、throughput 告警
- webhook 通知

缺失：

- 文件超时未到达告警
- 文件已到达未处理告警
- 下游未回执告警
- 磁盘/目录容量告警
- 多渠道通知

### 4.7 日志与审计服务

当前状态：`部分具备`

已有：

- `task_execution_audit`
- `ops_audit_log`
- `record_trace`

缺失：

- 文件上传/下载/归档/删除审计
- 补跑/重传与文件关联追踪
- 审计统一检索入口

### 4.8 监控指标服务

当前状态：`已具备`

已有：

- Micrometer + Prometheus
- Batch 成功率、吞吐、DLQ、blocked task 等指标
- Quartz 指标
- Grafana provisioning/dashboard 资产

缺失：

- 文件服务专项指标还不够全
- 缺磁盘空间、目录文件数、下载/分发回执延迟等指标

### 4.9 权限与运维台

当前状态：`部分具备`

已有：

- Viewer / Operator / Admin 三角色
- dashboard
- trigger task
- toggle task
- change request
- audit 查询

缺失：

- 文件运维台
- 文件重处理/归档/下载权限控制
- 批次补跑操作台
- 失败任务补偿台

### 4.10 对账校验服务

当前状态：`部分具备`

已有：

- reconcile run/diff 表
- reconcile controller
- record trace

缺失：

- 文件条数、金额、头尾汇总的统一校验规则中心
- 导出后回执对账
- 任务完成后的自动验数编排

## 5. 最关键的结构性缺口

如果只挑最关键的 6 个缺口，当前系统需要优先补下面这些：

### P0-1. 统一文件资产模型

建议新增：

- `file_record`
- `file_process_log`
- `file_dispatch_record`

原因：

- 现在文件接收和文件分发是割裂的
- 无法完整表达文件从“到达 -> 校验 -> 处理 -> 导出 -> 分发 -> 归档”的全生命周期

### P0-2. 文件状态机服务

建议统一文件状态，例如：

- `UPLOADING`
- `ARRIVED`
- `READY`
- `PROCESSING`
- `PROCESSED`
- `FAILED`
- `DISPATCHING`
- `DISPATCHED`
- `ARCHIVED`

### P0-3. 半文件防护

至少补一个：

- `.part -> final rename`
- `.done` 标志文件
- 文件大小稳定检测兜底

### P0-4. 业务任务实例服务

现有 `task_execution_state + batch_run_records + audit` 不够统一。

建议补：

- 业务任务实例主表
- step 实例
- 文件、批次、业务日期、人工触发、补跑、重试关联

### P0-5. 补偿与补跑统一入口

当前补偿能力分散在：

- Batch restart
- DLQ replay
- file distribution retry

建议统一成一套运维可操作的补偿服务。

### P0-6. 文件幂等和版本化

至少补：

- 来源系统 + 文件 hash + 业务日期 维度判重
- 导出文件 version
- 分发记录唯一键

## 6. 推荐新增的表

### 6.1 文件层

建议最少新增：

- `file_record`
- `file_process_log`
- `file_dispatch_record`

建议字段：

- `file_no`
- `source_system`
- `biz_type`
- `original_name`
- `stored_name`
- `stored_path`
- `file_size`
- `file_hash`
- `file_ext`
- `charset`
- `biz_date`
- `batch_no`
- `status`
- `version`
- `arrived_time`
- `processed_time`
- `archived_time`
- `deletable`
- `deleted_flag`

### 6.2 任务实例层

建议最少新增：

- `job_instance`
- `job_step_instance`
- `job_execution_log`

至少补齐：

- `trigger_source`
- `operator`
- `biz_date`
- `batch_no`
- `rerun_flag`
- `retry_flag`
- `related_file_id`
- `result_summary`

## 7. 优先级建议

### 第一阶段：先把系统“稳住”

优先补：

1. 文件资产表
2. 文件状态机
3. 半文件防护
4. 文件内容级幂等
5. 业务任务实例表
6. 补偿/补跑统一入口

### 第二阶段：把系统“管起来”

优先补：

1. 文件运维台
2. 文件归档/版本化
3. 文件专项告警
4. 文件与任务的审计关联
5. 对账规则中心

### 第三阶段：把系统“做深”

优先补：

1. 配置版本化
2. 文件与下游回执闭环
3. 更细的资源治理
4. 多节点文件扫描主备策略

## 8. 最后的判断

当前系统已经具备：

- 调度平台
- 批处理平台
- 基础文件处理能力
- 运行治理骨架

但离“成熟生产批量系统”还差的，主要不是再换框架，而是把下面两层补完整：

1. `业务任务实例层`
2. `文件资产管理层`

如果这两层补上，整个系统会从“能跑的批量平台”明显提升到“可运营、可追踪、可恢复的生产系统”。
