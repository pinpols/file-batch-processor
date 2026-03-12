# DB Bootstrap 与升级手册
> 中文名：数据库初始化与升级手册

## 1. 初始化数据库
```sql
CREATE DATABASE file_batch;
CREATE USER filebatch WITH PASSWORD 'replace_me';
GRANT ALL PRIVILEGES ON DATABASE file_batch TO filebatch;
```

## 2. 连接验证
```bash
psql -h <host> -p 5432 -U filebatch -d file_batch -c "select now();"
```

## 2.1 导入测试数据（本地/联调）
```bash
POSTGRES_HOST=127.0.0.1 \
POSTGRES_PORT=5432 \
POSTGRES_DB=file_batch \
POSTGRES_USER=filebatch \
POSTGRES_PASSWORD=replace_me \
./scripts/testdata/load-testdata-postgres.sh
```

## 3. 应用迁移执行
应用通过 Flyway 在启动时自动执行 `src/main/resources/db/migration/*.sql`：
- `V1_0__init_task_config.sql`
- `V1_1__task_config_hardening.sql`
- `V1_2__batch_capability_upgrade.sql`
- `V1_3__task_execution_state.sql`
- `V1_4__seed_cleanup_and_data_hardening.sql`
- `V1_16__orchestration_governance_upgrade.sql`
- `V1_20__quartz_postgresql_tables.sql`
- `V1_23__task_execution_audit_and_change_window.sql`
- `R__task_seed_data.sql`（repeatable seed）

Quartz JobStore 使用 `qrtz_*` 表，首次启动会由 `V1_20__quartz_postgresql_tables.sql` 自动创建。

## 4. 升级流程
1. 备份数据库（`scripts/db/backup.sh`）。
2. 发布新版本。
3. 启动应用并观察迁移日志。
4. 执行校验 SQL（见第5节）。
5. 触发冒烟作业验证。

## 5. 迁移后校验 SQL
```sql
-- 核心表
\dt

-- 任务配置
select d.task_id, d.job_name, t.trigger_type, d.enabled
from task_definition d
left join task_trigger t on d.task_id = t.task_id
order by d.task_id;

-- 状态机
select status, count(*) from task_execution_state group by status;

-- DLQ
select handled, count(*) from dlq_records group by handled;
```

## 6. 回滚原则
- 代码回滚: 切回上一版本 jar 并重启。
- 数据回滚: 使用 `scripts/db/restore.sh` 恢复到发布前备份点。
- 回滚后必须再次验证 `/actuator/health` 与关键作业。

## 7. 日期字段迁移策略（batch_date）
- 当前采用分阶段方案：保留 `batch_date`（VARCHAR）+ 新增 `batch_date_d`（DATE）影子列。
- 新脚本会把可解析数据回填到 `batch_date_d` 并建立索引。
- 待应用层全部切换到 `DATE` 后，再执行最终列替换。
