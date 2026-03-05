# Configuration Matrix
> 中文名：配置矩阵说明

## 环境分层
- `application.yml`: 默认配置（开发/本地可运行基线）。
- `application-prod.yml`: 生产配置（全部通过环境变量覆盖）。
- 启动参数: `--spring.profiles.active=prod`。

## 核心配置矩阵
| 配置项 | 本地默认 | 生产建议 | 必填 | 说明 |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `default` | `prod` | 是 | 环境开关 |
| `SERVER_PORT` | `8011` | `8011` | 否 | 服务端口 |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/postgres` | `jdbc:postgresql://<host>:5432/file_batch` | 是 | 数据库连接 |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | 专用账号 | 是 | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | 强密码/密钥注入 | 是 | 数据库密码 |
| `BATCH_ALERT_ENABLED` | `true` | `true` | 否 | 批量告警开关 |
| `BATCH_ALERT_FAILURE_RATE_THRESHOLD` | `0.2` | `0.1~0.2` | 否 | 失败率阈值 |
| `BATCH_ALERT_DLQ_BACKLOG_THRESHOLD` | `100` | 按容量设定 | 否 | DLQ积压阈值 |
| `BATCH_ALERT_DLQ_MANUAL_THRESHOLD` | `20` | 按人力容量设定 | 否 | 需人工介入的 DLQ 阈值 |
| `BATCH_ALERT_MIN_THROUGHPUT_RPS_THRESHOLD` | `5` | 按SLO设定 | 否 | 吞吐下限 |
| `BATCH_ALERT_WEBHOOK_ENABLED` | `false` | `true` | 否 | Webhook开关 |
| `BATCH_ALERT_WEBHOOK_URL` | 空 | 企业告警地址 | 条件必填 | 告警接收端 |
| `ORCHESTRATION_CONFIG_SOURCE` | `db` | `db` | 否 | 任务来源：`db`（推荐）或 `yaml`（仅 local/dev） |
| `BATCH_INPUT_FILE` | 空 | 必填（按任务传参） | 条件必填 | 导入文件路径；默认样例文件已解耦 |
| `BATCH_IMPORT_PARSE_ERROR_MAX_RATE` | `0.2` | `0.05~0.2` | 否 | 导入解析错误率上限（parseErrors/(read+parseErrors)）；超阈直接失败 |
| `BATCH_IMPORT_PARSE_ERROR_MIN_LINES` | `50` | `50~200` | 否 | 质量门禁最小样本行数，小于该行数不触发失败 |
| `ORCHESTRATION_SCHEDULER_DEFAULT_RETRY_JITTER_RATIO` | `0.0` | `0.1~0.3` | 否 | 重试抖动比例（防雪崩），0 表示关闭 |
| `ORCHESTRATION_SCHEDULER_BACKPRESSURE_THRESHOLD` | `1500` | `1000~5000` | 否 | 队列背压水位，超过后延迟入队 |
| `ORCHESTRATION_SCHEDULER_BACKPRESSURE_DELAY_MS` | `5000` | `2000~10000` | 否 | 背压延迟时间 |
| `BATCH_DLQ_MAX_REPLAY_COUNT` | `5` | `3~10` | 否 | 单条 DLQ 最大自动重放次数 |
| `BATCH_DLQ_RETRY_DELAY_MS` | `60000` | `30000~300000` | 否 | DLQ 重放失败后的延迟重试窗口 |
| `ORCHESTRATION_SCHEDULER_MAX_CONCURRENT_BY_KEY` | 空 | 按业务设定 | 否 | 按 `jobName` 或 `jobName:targetSystem` 并发上限（YAML Map） |
| `ORCHESTRATION_CIRCUIT_BREAKER_WINDOW_SIZE` | `10` | `10~50` | 否 | 熔断滑动窗口大小（任务次数） |
| `ORCHESTRATION_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD` | `0.5` | `0.3~0.7` | 否 | 熔断失败率阈值 |
| `ORCHESTRATION_CIRCUIT_BREAKER_COOLDOWN_DURATION_MS` | `300000` | `180000~600000` | 否 | 熔断冷却时间（毫秒） |
| `DISTRIBUTION_SFTP_MAX_CONCURRENT_PER_HOST` | `2` | `2~10` | 否 | SFTP 按 host 并发限流，避免下游连接被打爆 |
| `JAVA_OPTS` | 默认容器参数 | 带OOM退出与内存上限 | 否 | JVM参数 |

## 配置优先级
1. 命令行参数
2. 环境变量
3. `application-<profile>.yml`
4. `application.yml`

## 企业级能力配置说明

### 失败治理
| 配置项 | 本地默认 | 生产建议 | 说明 |
|---|---|---|---|
| `ORCHESTRATION_SCHEDULER_DEFAULT_RETRY_JITTER_RATIO` | `0.0` | `0.1~0.3` | 重试抖动比例（防雪崩），0 表示关闭 |
| `errorCode` 分桶 | 自动 | 自动 | DLQ 与 TaskExecutionState 新增 `error_code` 字段，按异常类型分类 |
| `BATCH_DLQ_MAX_REPLAY_COUNT` | `5` | `3~10` | 超限自动转 `MANUAL_REQUIRED` |

### 隔离治理
| 配置项 | 本地默认 | 生产建议 | 说明 |
|---|---|---|---|
| `ORCHESTRATION_SCHEDULER_MAX_CONCURRENT_BY_KEY` | 空 | 按业务设定 | 按 `jobName` 或 `jobName:targetSystem` 并发上限（YAML Map） |

### 质量门禁
| 配置项 | 本地默认 | 生产建议 | 说明 |
|---|---|---|---|
| `BATCH_IMPORT_PARSE_ERROR_MAX_RATE` | `0.2` | `0.05~0.2` | 全局解析错误率上限 |
| `BATCH_IMPORT_PARSE_ERROR_MIN_LINES` | `50` | `50~200` | 质量门禁最小样本行数 |
| `batch.import.parse-error.rules.<jobName>.max-rate/min-lines` | 空 | 按需覆盖 | 按 jobName 覆盖阈值 |
| 结果落库 | 自动 | 自动 | `quality_gate_results` 表 + 查询 API |

### 熔断/降级
| 配置项 | 本地默认 | 生产建议 | 说明 |
|---|---|---|---|
| `ORCHESTRATION_CIRCUIT_BREAKER_WINDOW_SIZE` | `10` | `10~50` | 熔断滑动窗口大小（任务次数） |
| `ORCHESTRATION_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD` | `0.5` | `0.3~0.7` | 熔断失败率阈值 |
| `ORCHESTRATION_CIRCUIT_BREAKER_COOLDOWN_DURATION_MS` | `300000` | `180000~600000` | 熔断冷却时间（毫秒） |
| 状态持久化 | 自动 | 自动 | `target_system_circuit_state` 表，重启不丢状态 |
| 指标 | 自动 | 自动 | `circuit_open_total`/`circuit_closed_total`/`circuit_half_open_total`/`scheduler_circuit_rejected_total` |

## 生产红线
- 不允许在代码仓库明文保存生产密码。
- 生产环境禁止 `ddl-auto=update/create`，仅允许 `validate/none`。
- 告警开关默认开启，通知通道必须联通。
- 生产环境强制使用数据库任务源（`ORCHESTRATION_CONFIG_SOURCE=db`）。
- `yaml` 任务源仅允许 `local/dev` profile，防止配置双写漂移。
