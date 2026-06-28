# 测试数据与脚本

本目录提供本地验证用的种子数据和加载脚本。数据规模较小，目标是覆盖功能分支；性能压测应使用专门造数脚本或外部数据集，不应把大体量运行产物提交到仓库。

## 数据文件

| 文件 | 用途 |
| --- | --- |
| `import_success.csv` | 正常导入样例 |
| `import_with_parse_errors.csv` | 解析错误和跳过逻辑样例 |
| `import_idempotent.csv` | 重复业务键样例 |
| `large_dataset_financial.csv` | 小规模性能路径样例 |
| `chinese_dataset_financial.csv` | 中文字符导入样例 |
| `reconcile_source_large.csv` | 对账源文件样例 |

## SQL 种子

| 文件 | 用途 |
| --- | --- |
| `seed_imported_records.sql` | 基础导入记录 |
| `seed_trace_and_dlq.sql` | trace 与 DLQ 查询样例 |
| `seed_reconcile_mismatch.sql` | 对账差异样例 |
| `seed_enhanced_test_data.sql` | 文件、任务、告警等综合场景 |

## 脚本

| 脚本 | 用途 |
| --- | --- |
| `init-test-environment.sh` | 初始化本地测试环境 |
| `load-testdata-postgres.sh` | 向 PostgreSQL 加载指定 SQL |
| `load-all-testdata.sh` | 按约定顺序加载全部 SQL 种子 |
| `smoke-test.sh` | 调用本地接口做基础冒烟验证 |
| `run-local-scenarios.sh` | 在本地后端已启动时执行全场景验证；会刷新调度器配置并校验导入/导出终态 |

## SQL 脚本

| 文件 | 用途 |
| --- | --- |
| `sql/cleanup-local-testdata.sql` | 清理 2026-03-01 之后的本地验证数据 |
| `sql/upsert-local-runtime-tasks.sql` | 写入本地导入/导出运行态任务参数 |
| `sql/verify-local-testdata.sql` | 输出本地验证读模型计数 |

## 使用示例

```bash
./scripts/testing/init-test-environment.sh
./scripts/testing/load-testdata-postgres.sh scripts/testing/seed_imported_records.sql
./scripts/testing/smoke-test.sh
```

本地后端已启动后执行完整场景：

```bash
APP_BACKGROUND=true ./scripts/local/start-local.sh
./scripts/testing/run-local-scenarios.sh
./scripts/local/stop-local.sh
```

`run-local-scenarios.sh` 会先加载 2026-03-01 账期样例数据，再写入 `process-file-main` 和
`data-export-main` 的本地运行态参数，并调用 `/ops/scheduler/reload` 让已启动后端热刷新数据库任务配置。
脚本不只检查触发接口返回 `accepted=true`，还会等待两个任务进入 `SUCCESS`，并校验导出 CSV 文件非空。

## 维护约定

- CSV 和 SQL 必须可重复执行；需要清理时由脚本显式删除或覆盖目标数据。
- Shell 只负责编排；清理、造数、验证 SQL 放在独立 `.sql` 文件中。
- 数据库、端口、认证等公共变量从 `scripts/lib/env-common.sh` 统一读取，不在脚本里重复散落默认值。
- 文件名要体现业务场景，避免使用 `demo`、`temp`、`final` 这类无法长期维护的命名。
- 不提交本地导出结果、压测产物、日志或数据库备份。
- 新增测试数据时同步更新本文件，并说明对应测试或手工验证入口。
