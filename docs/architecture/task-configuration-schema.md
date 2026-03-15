# Task Configuration Schema
> 中文名：任务配置表结构说明

## 说明
本文档描述了任务配置相关的三张表：`task_definition`、`task_trigger`、`task_parameter`。
这些表用于在数据库中存储任务的定义、触发规则和执行参数，支持通过数据库动态管理任务。

## 表结构详解

### 1. task_definition (任务定义表)
| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGSERIAL | PRIMARY KEY | 自增主键 |
| task_id | VARCHAR(100) | NOT NULL, UNIQUE | 任务唯一标识，用于调度和引用 |
| job_name | VARCHAR(100) | NOT NULL | 关联的 Spring Batch 作业名称 |
| description | VARCHAR(500) | 可空 | 任务描述信息 |
| priority | VARCHAR(20) | 可空 | 任务优先级（如 HIGH, NORMAL, LOW） |
| allow_parallel | BOOLEAN | DEFAULT FALSE | 是否允许并发执行 |
| dedup_key | VARCHAR(100) | 可空 | 去重键，用于防止重复任务入队 |
| enabled | BOOLEAN | DEFAULT TRUE | 任务是否启用 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：
- `idx_task_definition_task_id` (task_id)
- `idx_task_definition_enabled` (enabled)
- `idx_task_definition_priority` (priority)

### 2. task_trigger (任务触发器表)
| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGSERIAL | PRIMARY KEY | 自增主键 |
| task_id | VARCHAR(100) | NOT NULL | 外键，关联 task_definition.task_id |
| trigger_type | VARCHAR(50) | NOT NULL | 触发器类型：CRON, FIXED_RATE, FIXED_DELAY, ONE_TIME |
| cron_expression | VARCHAR(100) | 可空 | CRON 表达式（当 trigger_type=CRON 时必填） |
| fixed_rate_ms | BIGINT | 可空 | 固定频率（毫秒）（当 trigger_type=FIXED_RATE 时必填） |
| fixed_delay_ms | BIGINT | 可空 | 固定延迟（毫秒）（当 trigger_type=FIXED_DELAY 时必填） |
| one_time_at | TIMESTAMP | 可空 | 一次性执行时间（当 trigger_type=ONE_TIME 时必填） |
| enabled | BOOLEAN | DEFAULT TRUE | 触发器是否启用 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**外键**：
- `fk_task_trigger_task_id` -> task_definition(task_id)

**索引**：
- `idx_task_trigger_task_id` (task_id)
- `idx_task_trigger_type` (trigger_type)
- `idx_task_trigger_enabled` (enabled)

### 3. task_parameter (任务参数表)
| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGSERIAL | PRIMARY KEY | 自增主键 |
| task_id | VARCHAR(100) | NOT NULL | 外键，关联 task_definition.task_id |
| param_name | VARCHAR(100) | NOT NULL | 参数名称 |
| param_value | VARCHAR(1000) | 可空 | 参数值 |
| param_type | VARCHAR(50) | 可空 | 参数类型（STRING, INT, LONG, DOUBLE, BOOLEAN 等） |
| description | VARCHAR(500) | 可空 | 参数描述 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**外键**：
- `fk_task_parameter_task_id` -> task_definition(task_id)

**唯一约束**：
- (task_id, param_name)：同一任务下参数名唯一

**索引**：
- `idx_task_parameter_task_id` (task_id)
- `idx_task_parameter_name` (param_name)

## 配置示例

### 任务定义示例（来自 V1_0__init_task_config.sql）
```sql
-- 文件导入主链路任务
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('process-file-main', 'processFileJob', '文件导入主链路任务：上游文件导入分区表', 'HIGH', TRUE, TRUE);
```

### 触发器示例
```sql
-- FIXED_RATE 每 5 分钟执行一次
INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
VALUES ('process-file-main', 'FIXED_RATE', 300000, TRUE);
```

### 参数示例
```sql
-- 导入文件路径参数
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('process-file-main', 'input.file.name', '${user.dir}/src/main/resources/data/sample.csv', 'STRING', '导入文件路径');

-- 批次日期参数
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('process-file-main', 'batchDate', '', 'STRING', '批次日期（空表示当天）');

-- 运行模式参数
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('process-file-main', 'runMode', 'normal', 'STRING', '运行模式：normal/backfill');

-- 补跑标识
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('process-file-main', 'rerunId', '', 'STRING', '补跑标识');

-- 任务优先级
INSERT INTO task_parameter (task_id, param_name, param_value, param_type, description)
VALUES ('process-file-main', 'priority', '5', 'INT', '任务优先级（数字越大越高）');
```

## 使用说明

1. **任务来源**：
   - 当 `orchestration.config-source=db` 时，任务配置从数据库加载
   - 当 `orchestration.config-source=yaml` 时，仅用于本地开发（从 application.yml 加载）

2. **修改任务配置**：
   - 通过直接更新数据库表实现动态修改
   - 更改后需重启应用或触发配置刷新（取决于实现）

3. **与作业参数契约的对应**：
   - 表中的 `param_name` 对应 `docs/api/jobs-params-contract.md` 中的参数键
   - 例如：`input.file.name`、`batchDate`、`runMode`、`rerunId`、`priority`

## 迁移历史
- V1_0__init_task_config.sql：初始创建三张表并插入核心功能任务
- 后续迁移可能添加新字段或调整约束，请参考具体的 SQL 迁移文件