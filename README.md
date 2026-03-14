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

### 测试执行（分层）
- 默认快速测试：`./mvnw test`（跳过 integration/e2e/performance）。
- 集成测试：`./mvnw -Pintegration-test test`。
- E2E 测试：`./mvnw -Pe2e-test test`。
- 全量测试：`./mvnw -Pfull-test test`。

### 扩展点
- Writer 落地真实数据库时，可按业务键/多字段自定义 `business_key` 生成逻辑。
- 导出后的分发：可在 `dataExportJob` 后追加 Step（SFTP/OSS/HTTP 上传）。
- 监控与告警：可接入 Prometheus 指标、日志 traceId 与告警策略。
