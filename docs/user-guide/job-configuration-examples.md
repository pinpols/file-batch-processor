# 导入 / 导出作业配置示例

> 中文名：导入与导出作业的配置范例

本文档给出**可直接落库执行**的导入、导出作业配置示例。作业配置存放在三张表
`task_definition` / `task_trigger` / `task_parameter` 中(表结构见
[task-configuration-schema.md](../architecture/task-configuration-schema.md)),应用启动时读取
`enabled = true` 的作业注册到 Quartz 并按触发计划执行。

`task_definition.job_name` 对应 Spring Batch 的 Job bean(如 `processFileJob`、`dataExportJob`)。

---

## 1. 触发类型(task_trigger.trigger_type)

| 类型 | 必填字段 | 语义 |
|------|---------|------|
| `CRON` | `cron_expression`(Quartz 6 段) | 按 cron 周期触发 |
| `FIXED_RATE` | `fixed_rate_ms` | 固定频率触发 |
| `FIXED_DELAY` | `fixed_delay_ms` | 固定延迟;失败时**指数退避**(base×2ⁿ 封顶 30 分钟),退避状态已持久化(重启不丢) |
| `ONE_TIME` | `one_time_at` | 指定时刻一次性触发 |

---

## 2. 导入作业示例(processFileJob)

把上游 CSV 解析后写入分区表,幂等去重。

```sql
-- 1) 作业定义
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('my-import-daily', 'processFileJob', '每天导入 CSV 到分区表', 'HIGH', TRUE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

-- 2) 触发计划(此处用 CRON 每天 01:00;改 FIXED_RATE 见下方注释)
INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
VALUES ('my-import-daily', 'CRON', '0 0 1 * * ?', TRUE);
-- FIXED_RATE 每 5 分钟:
--   INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
--   VALUES ('my-import-daily', 'FIXED_RATE', 300000, TRUE);

-- 3) 参数
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description) VALUES
 ('my-import-daily', 'input.file.name', 'data/2026-07-01.csv', 'STRING', '导入文件路径'),
 ('my-import-daily', 'batchDate',       '2026-07-01',          'STRING', '批次日期(空=当天)'),
 ('my-import-daily', 'runMode',         'normal',              'STRING', 'normal / backfill'),
 ('my-import-daily', 'rerunId',         '',                    'STRING', '补跑标识(配合 backfill)'),
 ('my-import-daily', 'priority',        '5',                   'INT',    '数字越大越高');
```

**幂等性**:`imported_records` 唯一索引 `(business_key, batch_date, partition_key)` 保证重复导入不污染数据,可安全重跑。

**路径安全**:配置 `batch.io.input-base-dir` 后,`input.file.name` 会被强制限定在该基目录内(防路径穿越);未配置时也会拒绝包含 `..` 的逃逸路径。

---

## 3. 导出作业示例(dataExportJob)

按 SQL 取数,流式(服务端游标 + fetchSize)写出文件,可再分发。

```sql
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('my-export-daily', 'dataExportJob', '按批次导出给下游', 'NORMAL', FALSE, TRUE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, cron_expression, enabled)
VALUES ('my-export-daily', 'CRON', '0 0 2 * * ?', TRUE);

INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description) VALUES
 ('my-export-daily', 'export.sql',
   'select id, business_key, name, description, batch_date from imported_records where batch_date = ''2026-07-01''',
   'STRING', '单条只读 SELECT'),
 ('my-export-daily', 'output.file.name', 'export/data_20260701.csv', 'STRING', '导出文件名');
```

> ⚠️ **`export.sql` 校验(安全加固后)**:仅接受**单条只读 SELECT**(或 `WITH ... SELECT`)。
> 会被拒绝并抛 `Unsupported export.sql` 的写法:含分号/注释(`--`、`/* */`)、含 DML/DDL
> 关键字(insert/update/delete/drop/alter/truncate/create/grant/copy 等)、含危险函数或系统对象
> (`pg_read_file`、`lo_import`、`dblink`、`pg_sleep`、`current_setting`、`pg_catalog`、`information_schema` 等)。
>
> 路径安全:配置 `batch.io.output-base-dir` 后,`output.file.name` 被限定在该基目录内。

---

## 4. 相关环境配置(按需)

```yaml
batch:
  io:
    input-base-dir:  /data/inbound      # 导入路径基目录(配了即强制限定,防穿越)
    output-base-dir: /data/outbound     # 导出路径基目录
quality:
  enforce-default: false                # 全局默认:质量门 FAIL 是否把作业判 FAILED(软降级 PARTIAL 为 false)

sftp:                                   # 仅当作业产物经 SFTP 分发时
  known-hosts-path: /etc/ssh/known_hosts   # 默认 fail-closed,必须配 known_hosts(或显式 insecure-skip)
distribution:
  allowed-hosts: dl.example.com,sftp.example.com   # HTTP/FTP 分发目标白名单(留空仍拦内网/环回/云元数据)
```

**按作业覆盖质量硬闸门**:在该作业的 `task_parameter` 加 `quality.enforce=true`(参数优先级高于全局默认)。

---

## 5. 触发与补跑

- **自动**:启动时 `enabled=true` 的作业注册到 Quartz,按触发计划自动执行。
- **补跑**(backfill):设 `runMode=backfill` 并给一个唯一 `rerunId`,透传参数示例:
  ```
  input=/data/input.csv&batchDate=2026-07-01&runMode=backfill&rerunId=bf-20260701&priority=10&maxRetries=3&backoffMs=2000&timeoutMs=60000&maxDurationMs=300000
  ```
- **启停**:改 `task_definition.enabled`(或对应 `/ops/tasks/*/toggle` 端点,需 OPERATOR/ADMIN)。

---

## 6. 内置参考作业

`V1_0__init_task_config.sql` 已 seed 了一批参考作业(`process-file-main`、`data-export-main`、
`file-reception-monitor`、`file-distribution-*`、`dlq-replay-job` 等)。其中两条主链路
`process-file-main` / `data-export-main` 在 `V1_4` 中被**默认禁用且清空硬编码参数**——需运维显式配参并启用后才会运行,可直接照搬本文示例填充。
