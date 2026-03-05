# Unified Logging Spec
> 中文名：日志规范说明

## 1. Format
- 默认：`TEXT`（人类可读，多行异常栈）
- 可选：`JSON`（单行结构化）
- 切换方式：
  - 默认文本：`SPRING_PROFILES_ACTIVE=dev`
  - JSON：`SPRING_PROFILES_ACTIVE=dev,json-log`
- 输出目标：
  - Console: `stdout`
  - File(TEXT): `logs/app.log`
  - File(JSON): `logs/app.json.log`

## 2. Required fields
- `ts`: RFC3339 timestamp
- `level`: log level
- `app`: application name
- `env`: deployment environment
- `thread`: thread name
- `logger`: logger name
- `msg`: event message

## 3. Correlation fields
- `trace_id`
- `span_id`
- `task_id`
- `job_name`
- `execution_id`
- `batch_date`
- `shard_index`
- `shard_total`

## 4. Error fields
- `exception`: stack trace escaped to single line.

## 5. Level guide
- `INFO`: lifecycle/status transitions and summaries.
- `WARN`: retriable/transient problems and threshold nearing.
- `ERROR`: unrecoverable failures or threshold violations.

## 6. Event recommendations
- Job start/end summary should always include:
  - `job_name`, `execution_id`, `batch_date`
  - `read_count`, `write_count`, `skip_count`, `duration_ms`
- Data-quality gate failure should include:
  - `quality_passed=false`, `quality_message`

## 7. Operations examples
- Find failed jobs by execution (JSON mode):
```bash
rg '"level":"ERROR".*"execution_id":"' logs/app.json.log
```
- Find blocked dependencies (JSON mode):
```bash
rg '"msg":"Task .* Blocked by failed dependency"' logs/app.json.log
```
- Find failed jobs by execution (TEXT mode):
```bash
rg "ERROR .*execution_id" logs/app.log
```
