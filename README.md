## File Batch Processor 使用说明

### 场景概览
- **场景一：文件接收 → 解析 → 入表（幂等）**  
  - 入口：`processFileJob`（可被本地编排触发）。  
  - Reader：`FileRecordReader` 读取 CSV，支持分片；记录 read.count 与 checksum。  
  - Processor：`FileRecordProcessor` 示例逻辑，可扩展业务校验/转换。  
  - Writer：`FileRecordWriter` 将数据写入表 `imported_records`，唯一索引 `business_key + batch_date` 保证幂等。  
  - 质量校验：`JobCompletionNotificationListener` 检查读写计数/校验和，不一致则失败。

- **场景二：按 SQL 取数 → 生成文件（可再分发）**  
  - 入口：`dataExportJob`。  
  - Reader：`JdbcCursorItemReader`，SQL 从参数 `export.sql` 传入（默认导出 `imported_records`）。  
  - Writer：`FlatFileItemWriter` 输出 CSV，路径由 `output.file.name` 决定；可在后续 Step/Listener 中扩展分发（SFTP/HTTP/OSS）。

### 关键能力
- 触发：CRON/固定频率/固定延迟/一次性；任务配置默认来自数据库（`task_definition`、`task_trigger`、`task_parameter`），YAML 仅用于 local/dev 临时调试。
- DAG/优先级/分片：`TaskSchedulerService` 管理；分片参数透传到 Reader。
- 去重与合并：短窗 dedup，时间窗合并小任务；批量日/重跑 `batchDate/runMode/rerunId` 透传。
- 可靠性：可配重试/退避/最大时长/超时；失败落 `dlq_records`。
- 幂等：Writer 利用唯一索引 + 批量日保证重复导入不污染数据。

### 新增能力
- **多格式导入**：CSV/定长之外支持 JSON（顶层数组，Jackson streaming）、Excel `.xlsx`（首行表头列名映射）。文档格式按 record 序号分片。详见 [job 配置范例](docs/user-guide/job-configuration-examples.md)。
- **加密/压缩文件导入**：上游文件可 PGP 加密 + `.gz`/`.zip` 压缩送达；导入前解密+解压到临时明文文件再解析（PGP 完整性校验、zip-slip 防护、step 结束清理临时文件）。详见 [encrypted-compressed-intake](docs/operations/encrypted-compressed-intake.md)。
- **清单(manifest)驱动入库**：上游送 `.manifest.json` 控制文件列出期望文件 + 条数/MD5；等一组到齐且对账通过才放行（灰度默认关）。详见 [manifest-driven-intake](docs/operations/manifest-driven-intake.md)。
- **多告警渠道**：`AlertSender` SPI（webhook/email/IM 飞书）+ `AlertDispatcher` 失败隔离 + severity 门槛。详见 [alerting-channels](docs/operations/alerting-channels.md)。
- **声明式映射**：`feed_definition`/`field_mapping` 配置 + `MappingEngine`（6 算子)+ `attributes JSONB`；**已接入导入链路（feedId 路由,默认路径不变）**——job 参数带 `feedId` 走 feed 路径,按配置映射列与 business_key。详见 [declarative-mapping](docs/operations/declarative-mapping.md)。
- **安全/可靠性加固**：export.sql 只读白名单、SSRF/路径穿越防护、SFTP 主机密钥校验、运维端点角色限制、弱口令 fail-fast；ACK 无限重发修复、熔断状态持久化、多副本 leader 门控、FIXED_DELAY 退避持久化、质量门 opt-in 硬闸门。

### 配置示例（application.yml）
- 导入示例（已内置）：`process-file-cron`、`process-file-fixed`、一次性 backfill。  
  - 参数：`input.file.name`、`batchDate`、`runMode`、`rerunId` 等。
- 导出示例（已内置）：`data-export-daily`  
  - `export.sql`: 自定义查询  
  - `output.file.name`: 输出路径

### 触发参数示例
```
input=/data/input.csv&batchDate=2025-01-01&runMode=backfill&rerunId=bf-20250101&priority=10&maxRetries=3&backoffMs=2000&timeoutMs=60000&maxDurationMs=300000
```

### 启动与运行
1) 准备 PostgreSQL（本地默认：`jdbc:postgresql://localhost:5432/postgres`，用户/密码见 `application-dev.yml`）。  
2) 构建/启动：`./mvnw spring-boot:run`。  
3) Flyway 会自动执行 `db/migration`，Quartz 使用 `QRTZ_*` 表（JDBC JobStore）。  
4) 调度：
   - 默认路径：应用启动时从数据库读取启用任务并注册到 Quartz。
   - YAML 路径：仅当 `orchestration.config-source=yaml` 且运行于 local/dev 时使用。
5) 监控接口：
   - 健康检查：`/actuator/health`
   - 指标：`/actuator/metrics`
   - Prometheus：`/actuator/prometheus`

### 文档索引
- 作业配置范例（导入/导出/多格式）：[docs/user-guide/job-configuration-examples.md](docs/user-guide/job-configuration-examples.md)
- 任务配置表结构：[docs/architecture/task-configuration-schema.md](docs/architecture/task-configuration-schema.md)
- 运维专题：[加密压缩导入](docs/operations/encrypted-compressed-intake.md) · [清单驱动入库](docs/operations/manifest-driven-intake.md) · [多告警渠道](docs/operations/alerting-channels.md) · [声明式映射](docs/operations/declarative-mapping.md) · [熔断 runbook](docs/operations/circuit-breaker-runbook.md) · [质量门 runbook](docs/operations/quality-gate-runbook.md) · [安全基线](docs/operations/security-baseline.md)
- 设计稿/实现计划:`docs/superpowers/specs/` 与 `docs/superpowers/plans/`

### 测试执行（分层）
- 单元测试：`mvn test -Punit-test`（200+ tests）
- 集成测试：`mvn test -Pintegration-test`（默认使用 Testcontainers PostgreSQL；如需本地 PG，需显式设置 `TEST_POSTGRES_URL` 或 `-Dtest.postgres.url=...`）
- E2E 测试：`mvn test -Pe2e-test`（需要 PostgreSQL test 数据库）

### 数据库准备
1. 创建测试数据库：`CREATE DATABASE test;`
2. 运行 Flyway 迁移：`mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/test`

### 扩展点
- Writer 落地真实数据库时，可按业务键/多字段自定义 `business_key` 生成逻辑。
- 导出后的分发：可在 `dataExportJob` 后追加 Step（SFTP/OSS/HTTP 上传）。
- 监控与告警：可接入 Prometheus 指标、日志 traceId 与告警策略。
