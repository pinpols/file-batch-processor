# 脚本索引

## 数据库
- `scripts/database/init-db.sql`: Docker/PostgreSQL 初始化入口，创建应用角色并授权当前数据库。
- `scripts/database/backup.sh`: 数据库备份。
- `scripts/database/restore.sh`: 数据库恢复。

Flyway 迁移由应用启动时执行，包括 Quartz JDBC 表结构。

## 本地开发
- `scripts/local/start-local.sh`: 启动本地应用和可选运维组件。
- `scripts/local/stop-local.sh`: 停止本地应用和可选运维组件。
- `scripts/local/run-tests.sh`: 分层运行单元、集成、E2E 或全量测试。
- `scripts/local/generate-dag-graph.sh`: 从数据库读取 DAG 配置并生成 Mermaid 图。

## 测试
- `scripts/testing/init-test-environment.sh`: 清理并加载本地测试数据。
- `scripts/testing/load-testdata-postgres.sh`: 通过统一环境加载指定 SQL。
- `scripts/testing/run-local-scenarios.sh`: 本地后端启动后的全场景验证入口；会热刷新调度器配置并校验导入/导出终态。
- `scripts/testing/smoke-test.sh`: 健康、指标、运维 API 和数据库连通性冒烟。

## 公共库
- `scripts/lib/env-common.sh`: 本地脚本统一环境变量，默认连接 `file_batch` 数据库；同时管理 `BATCH_IO_INPUT_BASE_DIR` 和 `BATCH_IO_OUTPUT_BASE_DIR`，本地默认限定在测试数据目录和场景运行目录内。
- `scripts/lib/logging.sh`: 统一日志输出和运行目录。
- `scripts/lib/psql.sh`: PostgreSQL 执行封装，宿主机无 `psql` 时回退到 Docker 容器。

## 维护
- `scripts/maintenance/cleanup.sh`: 清理日志、临时文件、本地导出产物和生成文档。
