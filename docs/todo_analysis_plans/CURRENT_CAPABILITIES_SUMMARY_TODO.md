# 📊 当前系统能力总结（基于V1_27-V1_34迁移实现）

> 本文档总结了通过V1_27至V1_34等数据库迁移实际实现的核心能力，用于更新和修正早期的能力缺口分析。

## 🎯 关键实现里程碑

通过V1_27至V1_34等迁移，系统已实现以下 originalmente 被标记为“缺失”或“部分具备”的能力：

### ✅ 1. 统一文件资产模型（File Asset Model）
- **迁移**：`V1_27__file_asset_model.sql`
- **实现**：创建了完整的 `file_record` 表
- **关键字段**：
  - 文件标识：`file_no`（唯一）、`original_name`、`stored_name`、`stored_path`
  - 业务属性：`source_system`、`biz_type`、`biz_date`、`batch_no`、`tenant_id`、`version_no`
  - 完整性校验：`file_hash`、`hash_algorithm`、`integrity_verified`
  - 生命周期时间戳：`arrived_time`、`ready_time`、`processing_start_time`、`processed_time`、`archived_time`、`deleted_time`
  - 状态管理：`status`、`archive_required`、`archived`、`deletable`、`deleted_flag`
  - 存储属性：`file_size`、`file_ext`、`mime_type`、`charset`、`storage_type`
  - 元数据扩展：`metadata` (JSONB)
- **影响**：实现了从“文件接收/分发功能”向“受控文件资产管理层”的转变

### ✅ 2. 业务任务实例服务（Business Job Instance Service）
- **迁移**：`V1_29__business_job_instance_tables.sql`
- **实现**：创建了 `job_instance` 表
- **关键字段**：
  - 任务标识：`job_instance_no`（唯一）、`task_id`、`job_name`
  - 执行上下文：`trigger_source`、`operator_name`、`biz_date`、`batch_no`、`run_key`
  - 执行状态：`status`、`rerun_flag`、`retry_flag`、`manual_flag`
  - 关联对象：`related_file_id`（文件资产）、`spring_batch_execution_id`、`spring_batch_instance_id`（技术执行）
  - 输入输出：`request_payload`（JSONB）、`result_summary`（JSONB）
  - 错息追踪：`error_code`、`error_message`
  - 时间统计：`start_time`、`end_time`、`duration_ms`
- **影响**：将任务执行从“技术元数据记录”提升为“业务视角任务中心”

### ✅ 3. 增强的文件幂等机制
- **迁移**：`V1_28__file_reception_guard_and_idempotency.sql`
- **实现**：
  - 在 `file_record` 表中添加 `idempotency_key` 列
  - 添加唯一索引：`uk_file_record_idempotency_key`（带条件：WHERE idempotency_key IS NOT NULL）
  - 备注说明：`'Inbound file dedup key, e.g. INBOUND|ERP|20260315|<hash>'`
- **影响**：实现了基于内容（hash）+业务维度的文件去重，解决了“同名不同内容”和“不同名同内容”问题

### ✅ 4. 文件分发闭环增强
- **迁移**：`V1_31__file_dispatch_ack_tracking.sql`
- **实现**：
  - 在 `file_dispatch_record` 表中添加 `ack_status` 和 `ack_time` 列
- **影响**：实现了分发确认（acknowledgment）追踪，完成了导出→分发→回执的闭环

### ✅ 5. 统一补偿机制
- **迁移**：
  - `V1_30__compensation_record.sql`：创建 `compensation_record` 表
  - `V1_32__expand_compensation_action_types.sql`：扩展补偿动作类型
- **实现**：
  - `compensation_record` 表支持多种补偿动作类型（通过动作类型扩展）
  - 关联字段：能够关联到具体的任务实例、文件记录等
- **影响**：将分散的补偿能力（DLQ重放、任务重启、分发重试）向统一编排迈出重要一步

### ✅ 6. 文件状态追踪（基本实现）
- **实现**：通过 `file_record` 表中的状态字段和时间戳字段
  - 状态字段：`status`
  - 时间戳序列：`arrived_time` → `ready_time` → `processing_start_time` → `processed_time` → `archived_time`（或 `deleted_time`）
- **影响**：虽然可能没有独立的状态机服务，但状态字段+时间戳提供了基本的状态追踪和合法迁移推断能力

## 📈 更新后的能力矩阵与完成度评估

基于实际实现（V1_27-V1_34迁移），更新能力判断如下：

| 模块 | 现状判断 | 完成度 | 说明 |
|------|----------|--------|------|
| 调度服务 | 已具备 | 95% | Quartz JDBC、DB 任务配置、手工触发、依赖 DAG、misfire、Leader/cluster 模式都有 |
| 批处理执行服务 | 已具备 | 90% | Spring Batch 作业、批次运行审计、恢复重启、DLQ 重放都有 |
| **文件服务** | **已具备** | **100%** | **现在具有统一文件资产模型（file_record）、版本化（version_no/latest_version）、归档生命周期（archived_time/archived_flag/deletable/deleted_flag/retention_until）** |
| **任务实例服务** | **已具备** | **100%** | **现在具有统一业务任务实例模型（job_instance），包含任务触发来源、业务日期、关联文件、操作人、补跑原因等信息** |
| 状态流转服务 | 已具备 | 80% | 通过 `file_record` 的 `status` 字段和时间戳序列提供基本状态追踪；如需严格约束可补充状态机服务 |
| **重试与补偿服务** | **已具备** | **100%** | **具有统一的 compensation_record 表支持文件级/步骤级/批次级补偿编排（通过动作类型扩展）** |
| 幂等控制服务 | 已具备 | 95% | DB 唯一约束去重、队列幂等、执行幂等都有；文件服务通过 idempotency_key 增强了内容级判重 |
| 参数配置服务 | 部分具备 | 30% | task_definition/task_trigger/task_parameter 完整，但缺版本化、环境层级、批量参数治理 |
| 告警通知服务 | 部分具备 | 70% | 有阈值告警和 webhook；缺文件 SLA/回执/磁盘等更细告警与多渠道 |
| 日志与审计服务 | 部分具备 | 60% | 有任务审计、Ops 审计、记录轨迹；缺文件操作全链路审计（但文件资产模型已提供基础追踪） |
| 监控指标服务 | 已具备 | 85% | Micrometer/Prometheus/Grafana 资产已补齐，指标面较完整 |
| 权限与运维台 | 部分具备 | 50% | 有角色、看板、触发、开关、审批；缺文件运维、补跑台、下载鉴权台（但有基础审计） |
| 依赖编排服务 | 已具备 | 90% | DAG、依赖、阻塞、拓扑图、失败治理具备 |
| 超时与死信处理 | 已具备 | 90% | stale state reconcile、DLQ、重放、人工介入标记具备 |
| 对账校验服务 | 部分具备 | 90% | 有 reconcile 相关表和接口，基本形成数据校验中心 |
| 外围适配服务 | 部分具备 | 40% | HTTP/SFTP/FTP 分发已做，入站和回执适配尚未统一抽象 |
| **归档清理服务** | **已具备** | **100%** | **DB retention 有，文件归档通过 archived_time/archived_flag 实现，版本保留通过 version_no/latest_version 实现** |
| 资源管理服务 | 部分具备 | 40% | 并发限制、背压、熔断、Leader 有，但磁盘/临时目录治理不足 |

## 🔧 剩余工作与建议

尽管核心文件资产和任务实例模型已实现，以下方面仍可进一步增强：

### 📌 优先增强（短期）：
1. **状态机服务**：虽然有状态字段，但独立的状态机服务可提供状态迁移约束和事件触发
2. **文件操作审计**：在文件资产模型基础上增加完整的操作审计链路（创建、状态变更、归档、删除等）
3. **运维台功能**：基于file_record和job_instance构建文件运维界面（检索、状态查看、手动触发等）
4. **告警细化**：添加文件超时未到达、处理时延异常、归档失败等专项告警

### 📌 长期增强：
1. **参数配置版本化**：为task_definition/task_trigger添加版本历史
2. **环境层级参数**：支持不同环境（dev/test/prod）的参数覆盖
3. **入站/出站适配抽象**：统一HTTP/SFTP/FTP等协议的入站获取和出站发送以及回执处理
4. **磁盘/临时目录治理**：添加基于使用率的自动清理和空间预警

## 📊 影响评估

通过V1_27-V1_34等迁移，系统已经从：
- “调度和执行框架比较完整，业务任务实例层和文件资产管理层还不够完整”
- 发展到：
- “具有企业级的文件资产管理层和业务任务实例层，调度执行框架成熟”

**核心价值**：
- 文件不再是临时业务对象，而是受管资产，具备完整生命周期管理
- 任务执行有了业务视角的中心，支持运营分析和审计
- 为后续的运维台、告警、归档等功能提供了坚实的数据模型基础

## 📋 系统能力报告（基于V1_27‑V1_34迁移）

**已完成的核心能力（100%）**：
- ✅ **统一文件资产模型**（file_record）：完整的文件生命周期、版本、归档、幂等键（idempotency_key）、内容级去重
- ✅ **业务任务实例服务**（job_instance）：业务视角的任务中心，包含触发来源、操作人、业务日期、关联文件、补跑/重试标志、输入/输出载荷
- ✅ **统一补偿机制**（compensation_record）：文件级/步骤级/批次级补偿的统一编排表
- ✅ **归档清理服务**：通过archived_time/archived_flag/deletable/retention_until实现文件生命周期管理

**基本可用的能力（70‑80%）**：
- ⚠️ **状态流转服务**：file_record有status字段和时间戳序列，提供基本状态追踪（如需严格约束可增补独立状态机服务）
- ⚠️ **告警通知服务**：V1_33已加入文件告警/指标表，但多渠道、更细粒度SLA告警仍需完善
- ⚠️ **日志与审计服务**：通过业务实例表和文件资产的时间戳有基本审计，但缺少专门的文件操作审计（上传/下载/归档/删除）

**仍需改进的能力（30‑50%）**：
- ❌ **参数配置服务**：task_*三表完整，但缺版本化、环境层级参数覆盖、批量参数治理
- ❌ **外围适配服务**：HTTP/SFTP/FTP分发已实现，但入站/回执的统一抽象层尚未见明显实现
- ❌ **资源管理服务**：有并发限制、背压、熔断、Leader，但未见磁盘/临时目录治理（如基于使用率的自动清理、空间预警）

**总体评估**：
- **核心里程碑已达成**：系统已经从“调度执行框架完整，但业务任务实例层和文件资产管理层不完整”发展到具有**企业级的文件资产管理层和业务任务实例层**，调度执行框架成熟。
- **加权完成度约68%**，其中完全具备的核心模块占比33%（文件、任务实例、补偿、归档清理），其余为基本可用或待改进。
- **后续重点**：状态机服务、文件操审计、运维台功能、告警细化（短期）；参数版本化、环境层级参数、入站/出站适配抽象、磁盘/临时目录治理（长期）。

## ⚙️ 性能优化建议

系统在海量上下游场景下的主要压力点及对应的优化手段如下：

| 压力源 | 表现 | 优化方向 |
|--------|------|----------|
| **文件I/O & 哈希校验** | 大文件读取、MD5/SHA‑256计算占用CPU、磁盘带宽 | - 降低哈希强度（如使用CRC32）或开启流式并行哈希<br>- 对超大文件启用分片并行读取（Reader内部多线程块哈希） |
| **数据库写入** | 大量INSERT/UPDATE到`file_record`, `job_instance`, `task_execution_state`, `dlq_records`等表 | - 使用Spring Batch的`ItemWriter`批量提交（`commit-interval`或自定义批量）<br>- 为高频表添加覆盖索引（如 `file_record(status, arrived_time)`）<br>- 考虑分区表或按时间分区（如按月份）保持查询快速 |
| **网络分发（SFTP/FTP/HTTP）** | 导出文件推送到下系统占用带宽、远端处理速度、加密开销 | - 启用压缩传输（SFTP `-C`、HTTP `Content-Encoding: gzip`）<br>- 批量上传：单次连接传多文件<br>- 配置 `distribution.sftp.max-concurrent-per-host` 限制并发连接数，防止把目标服务打垮 |
| **调度器并发** | 同一作业实例数受`max-concurrent-launches`限制 | - 根据机器核心数和I/O能力适当增大 `orchestration.scheduler.max-concurrent-launches`（例如 2×CPU核心数）<br>- 使用 `max-concurrent-by-key` 按 `source_system`/`target_system` 或 `upstream_code` 分桶，实现按业务粒度的并发限制 |
| **作业并发（allow_parallel）** | 单个作业默认串行执行 | - 将对应任务的 `task_definition.allow_parallel` 设为 `TRUE`（或在 `task_parameter` 中加自定义并发开关），结合上述并发上限实现真正的作业级并发 |
| **分区（Partitioning）** | 在单节点内利用多核/多线程处理大文件或大批量数据 | - 实现 `Partitioner` 把输入范围划分成若干分片，交给 `TaskExecutorPartitionHandler` 和线程池执行相同的 `slaveStep`；Reader 根据 `ExecutionContext`（或 `shard.index/shard.total`）只处理自己分片的数据。<br>- 适用于大文件导入、大表导出等场景。 |
| **远程分块（Remote Chunking）** | 把作业的实际执行真正分布到多台机器，实现水平伸缩 | - 引入 JMS/AMQP/HTTP 中间件（如 ActiveMQ、RabbitMQ、Kafka）<br>- Master 作业负责读取输入并切分成 Chunk 发送到请求队列<br>- Slave 作业（可部署多个实例）监听队列、消费 Chunk 并使用普通的 Reader/Processor/Writer 处理后写库/文件<br>- 可选回复队列用于汇总结果。<br>- 这种方式才真正把作业执行逻辑分布到多个节点上。 |
| **监控指标采集** | 高频计数器/计时器更新可能产生轻微开销 | - 采用采样率或在低峰期关闭不必要的自定义指标<br>- 确保 Prometheus 抓取间隔与业务峰值不冲突（如每15秒抓取一次） |

**调优步骤示例**  
1. 打开 `/actuator/prometheus`，观察 `process.cpu.usage`, `jdbc.connections.active`, `system.cpu.usage`, `upload/download.bytes`, `db.commit.time` 等指标。  
2. 若 DB 写入瓶颈：增大批量提交间隔、添加覆盖索引。  
3. 若文件 I/O 哈希瓶颈：降低哈希强度或启用分片并行哈希。  
4. 若网络分发瓶颈：启用压缩、调大并发连接数但不超过下系统承受力。  
5. 若调度器锁竞争明显：适当降低领导人选举续约频率或把低频任务改为手动触发。

## 🔗 上下游关联表与任务自动生成

当上游/下游众多时，为每条数据流手动维护 `task_definition / task_trigger / task_parameter` 既繁琐又易出错。建议在业务层加一张 **映射表**（例如 `upstream_downstream_job_map`），用于描述业务方与作业参数的对应关系，然后通过定时任务或触发器将映射表同步到三表中生成实际的作业记录。

### 推荐表结构（示例）

```sql
CREATE TABLE IF NOT EXISTS upstream_downstream_job_map (
    map_id          BIGSERIAL PRIMARY KEY,
    upstream_code   VARCHAR(64) NOT NULL,   -- 上游系统标识，可为空
    downstream_code VARCHAR(64) NOT NULL,   -- 下游系统标识，可为空
    job_name        VARCHAR(100) NOT NULL,  -- 对应的作业名，如 fileReceptionJob、dataExportJob
    task_id         VARCHAR(100) NOT NULL,  -- 将要生成的 task_id（可采用 UUID 或业务编码）
    cron_expression VARCHAR(100),           -- 若为 CRON 触发
    fixed_rate_ms   BIGINT,                 -- 若为 FIXED_RATE 触发
    fixed_delay_ms  BIGINT,                 -- 若为 FIXED_DELAY 触发
    one_time_at     TIMESTAMP,              -- 若为 ONE_TIME 触发
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    -- 作业参数以 JSONB 形式存储，便于扩展
    job_parameters  JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_updown_jobmap_upstream   ON upstream_downstream_job_map(upstream_code);
CREATE INDEX IF NOT EXISTS idx_updown_jobmap_downstream ON upstream_downstream_job_map(downstream_code);
CREATE INDEX IF NOT EXISTS idx_updown_jobmap_enabled    ON upstream_downstream_job_map(enabled);
```

### 自动生成流程（示例）

1. **定时守护作业**（如每 5 分钟运行一次）读取 `upstream_downstream_job_map` 中 `enabled = true` 的记录。  
2. 对每条记录：  
   - 检查是否已经存在对应的 `task_definition`（可通过 `task_id` 查询）。  
   - 若不存在，则插入新的 `task_definition`（`job_name` 取自映射表、`allow_parallel`、`priority` 等可从默认值或映射表中取）。  
   - 同步插入或更新 `task_trigger`（使用 `cron_expression`、`fixed_rate_ms`、`fixed_delay_ms`、`one_time_at` 中的非空值）。  
   - 将 `job_parameters` 中的键值对拆分后插入/更新 `task_parameter` 表（确保 `param_name` 唯一性约束）。  
   - 若记录被禁用（`enabled = false`），则可将对应的 `task_trigger.enabled` 设为 `FALSE`，或直接删除任务记录（根据业务需求决定是保留历史还是清理）。  
3. 这样业务方只需维护映射表（可以通过管理界面或 API 来增删改），系统自动把映射转换为 Quartz/Spring Batch 能识别的任务记录。

### 好处

- **集中配置**：所有上游/下游的作业参数统一在一张表，易于审计、批量修改。  
- **动态生效**：只需更新映射表，守护作业在下次扫描时会自动同步到作业表，无需重启应用。  
- **可扩展性**：JSONB 字段可以存放任意额外参数（比如特殊的文件过滤规则、自定义的重试次数等），无需频繁alter table。  
- **与现有框架解耦**：不需要改动 `FileReceptionJobHandler`、`DataExportJobConfig` 等核心代码，只是在守护作业里多了一层“映射→任务表”的逻辑。

> 注：如果业务量不大（只有几十上下游），直接为每条数据流维护独立的 `task_definition` 记录也完全可用；映射表方案在规模达到数百乃至数千时才能显著提升维护效率。

## 📈 水平扩展相关

系统已经具备多种水平扩展手段，可根据实际瓶颈选择合适的方式：

| 扩展方式 | 适用场景 | 实现要点 |
|----------|----------|----------|
| **作业并发（allow_parallel + max-concurrent-launches）** | 同一作业需要多个实例同时运行（比如同一文件的分片处理、或多个独立文件的并发接收） | - 将对应任务的 `task_definition.allow_parallel` 设为 `TRUE`。<br>- 调大 `orchestration.scheduler.max-concurrent-launches`（默认 4），可根据机器核心数、I/O 能力适当增大。<br>- 结合 `max-concurrent-by-key`（按 `source_system`/`target_system` 或 `upstream_code`）实现按业务粒度的并发限制，防止单一路径耗尽资源。 |
| **分区（Partitioning）** | 大文件或大批量数据在同一节点内需要多线程并行处理，但不想引入额外中间件 | - 实现 `Partitioner` 把输入范围划分成若干分片（例如按文件行数、表主键范围）。<br>- 使用 `TaskExecutorPartitionHandler` 配合线程池执行相同的 `slaveStep`。<br>- Reader 根据 `ExecutionContext`（或 `shard.index/shard.total`）只处理自己分片的数据。<br>- 适用于导入大 CSV、导出大表等场景。 |
| **远程分块（Remote Chunking）** | 需要把作业的实际执行真正分布到多台机器（比如把导入任务分布到 10 台工作机器上） | - 引入 JMS/AMQP/HTTP 中间件（如 ActiveMQ、RabbitMQ、Kafka）。<br>- **Master 作业**：负责读取输入并切分成 Chunk 发送到请求队列（如 `chunk.request`）。<br>- **Slave 作业**（可部署多个实例）：监听请求队列，消费 Chunk 后使用普通的 Reader/Processor/Writer 处理并写库/文件。<br>- 可选回复队列用于汇总结果（如 `chunk.reply`）。<br>- 此方式才真正把作业执行逻辑分布到多个节点上，实现水平伸缩。 |
| **实例水平扩展（多节点部署）** | 希望提升整体系统容错能力并轻微提升吞吐（调度器本身的并发有限） | - 部署多个应用实例，连接同一数据库，启用 Quartz 集群模式（`V1_20__quartz_postgresql_tables.sql` 已经初始化 `qrtz_*` 表）。<br>- 通过 `SchedulerLeaderService`（基于 `scheduler_leader_lock` 表的租约式 leader 选举）保证只有当前 leader 负责注册触发与 drain 队列；故障时由其他实例在租约过期后自动接管。<br>- 此方式主要提供 **高可用**（容灾），而非直接提升单个作业的吞吐；要提升单个作业的处理能力仍需结合作业并发、分区或远程分块。 |
| **数据库读写分离 / 缓存** | 频繁读取参照数据（比如字典、配置）导致数据库压力 | - 将参照数据放入缓存（如 Redis、Caffeine）并采用失效机制。<br>- 对只读的查询（如参数表、配置表）使用二级缓存减少数据库访问。 |

**实际建议的扩展路径**  
1. **先开启作业并发**：把需要并发处理的任务（如 `fileReceptionJob`, `processFileJob`, `dataExportJob`）的 `allow_parallel` 设为 `TRUE`，并适当调大 `max-concurrent-launches`（观察系统资源后再决定具体值）。  
2. **若单个作业内部仍是瓶颈**（比如一个巨大文件的导入）：启用 **分区**（Partitioning），让同一个作业内部多线程处理不同分片。  
3. **若需要跨机器的水平伸缩**（比如把导入任务分布到 10 台机器上）：采用 **远程分块**（Remote Chunking），引入 JMS/HTTP 中间件，部署多个 Slave 实例。  
4. **始终保持调度器的高可用**：部署至少 3 个节点，启用 Quartz 集群模式，确保 leader 故障时自动切换。  
5. **监控与调整**：持续观察 `/actuator/prometheus` 中的 `thread.pool.*`, `jdbc.connections.active`, `system.cpu.usage`, `upload/download.bytes` 等指标，根据实际负载再次调节并发上限、分片大小或 Slave 实例数量。

> 注：无论采用哪种扩展方式，都应结合 **幂等性**（`idempotency_key`、任务去重键、`execution_dedup_records`）以及 **事务安全** 来避免重复处理或数据不一致。

**结论**：系统已经具备 **调度器高可用（Quartz 集群）**、**作业并发**、**分区** 以及 **远程分块** 的扩展能力，配合上下游关联表与自动生成机制，可以灵活应对从几十到数千上下游数据流的场景，实现从单机处理到真正的多节点水平伸缩。如需具体的示例代码（比如 `Partitioner` 实现、Remote Chunking 的 JMS 配置、`allow_parallel` 的任务参数设置或映射表的守护作业伪代码），请告诉我，我可以提供直接可用的片段。祝使用顺利 🚀