# 批量系统运维 Runbook
> 中文名：运维运行手册

## 1. 启动与停止

### 1.1 启动
```bash
cp .env.example .env
# 编辑 .env 中密码与告警配置

# 默认启动
docker compose -f docker-compose.prod.yml up -d --build
```

### 1.2 停止
```bash
docker compose -f docker-compose.prod.yml down
```

## 2. 健康检查
- 应用健康: `http://localhost:8011/actuator/health`
- 指标接口: `http://localhost:8011/actuator/prometheus`
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`

## 3. 常见故障与处理

### 3.1 上游文件未到
- 现象: 任务超时或依赖未满足，`batch_blocked_task_count` 增高。
- 处理:
  1. 检查接收目录与SFTP连通。
  2. 校验任务参数中的文件路径与批次日。
  3. 必要时使用补跑窗口重新触发。

### 3.2 数据质量问题
- 现象: 导入失败率升高，DLQ增长。
- 处理:
  1. 查询 `dlq_records` 获取失败原因分布。
  2. 修复源数据后执行 `dlqReplayJob`。
  3. 如果是规则性错误，先修复代码再重放。

- 补充: 如果导入直接失败且 ExitStatus 为 `PARSE_ERROR_RATE_TOO_HIGH`，说明触发了解析错误率门禁。
  - 可通过 `BATCH_IMPORT_PARSE_ERROR_MAX_RATE`/`BATCH_IMPORT_PARSE_ERROR_MIN_LINES` 调整阈值
  - 建议先修复源数据格式问题再放宽阈值

### 3.3 数据库异常
- 现象: 作业大量失败，连接超时。
- 处理:
  1. 检查 PostgreSQL 健康与连接数。
  2. 应急降并发（降低调度并发/分片）。
  3. 恢复后优先执行 `batchRestartJob` 断点续跑。

### 3.4 下游节点失败
- 现象: 导出后分发失败或重试超限。
- 处理:
  1. 验证下游连接与权限。
  2. 检查 `fileDistributionRetryJob` 执行日志。
  3. 失败文件放入补偿通道并人工确认后重推。

- 补充: 如果失败原因包含 `SFTP throttled`，说明触发了 SFTP 并发限流。
  - 通过 `DISTRIBUTION_SFTP_MAX_CONCURRENT_PER_HOST` 提升并发上限
  - 或者降低分发任务的生产速率/并发，避免下游连接数打爆

## 4. 备份与恢复

### 4.1 备份
```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=file_batch
export POSTGRES_USER=filebatch
export POSTGRES_PASSWORD='***'
./scripts/db/backup.sh ./backup
```

### 4.2 恢复
```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=file_batch
export POSTGRES_USER=filebatch
export POSTGRES_PASSWORD='***'
./scripts/db/restore.sh ./backup/file_batch_YYYYMMDD_HHMMSS.dump
```

## 5. 告警阈值建议
- 失败率: `> 20%` 连续 5 分钟告警。
- DLQ 积压: `> 100` 连续 5 分钟告警。
- 吞吐下降: `< 5 row/s` 连续 10 分钟告警。
- 堵塞任务: `> 20` 连续 5 分钟告警。
- 服务不可用: `up == 0` 连续 2 分钟告警。
