# file-batch-processor 综合审计整改记录

日期: 2026-06-28
范围: 单体 `file-batch-processor` 当前 main 工作区修复状态。

## 结论

本轮修复严格限定在单体项目内,没有引入多租户、跨服务协调、消息中间件或分布式执行框架。P0/P1 高风险项已落到代码、配置、迁移安全脚本和回归测试;P2/P3 中可低风险收口的项也一并修复。

## 已修复

- 对账:源文件和目标表使用同一 canonical row 口径哈希,并接入 `PathSafety`。
- 熔断:失败计数与开路判断合并为仓储层原子更新,half-open 不再被恢复流量击穿。
- Reconciler:不再把 READY 或窗口未结束的长跑 RUNNING 误判为死亡任务。
- RBAC:`/ops/batch/**`、`/ops/file-dispatch/**`、`/ops/files/*/reprocess` 改为 OPERATOR/ADMIN。
- 路径安全:空 baseDir 不再放行绝对路径;生产类 profile 要求 input/output base-dir。
- 迁移:V1_35 全表 UPDATE 加差异 WHERE;CI 新增 Flyway 安全脚本。
- 性能:Excel 改 SAX 流式读取;Hikari 池配置提高到与执行器匹配。
- 导出 SQL:默认表白名单、只读 SELECT/WITH、禁止危险函数/注释/多语句,CTE alias 不误判。
- 临时明文:owner-only 目录/文件,异常删除,启动清扫。
- SSRF:分发目标默认阻断内网地址。
- 分发:HTTP/FTP/SFTP 增加连接和请求/读写超时。
- 观测:导入/分发/作业关键路径补 Prometheus counter;告警支持未恢复记录复用和升级。
- 运维契约:OpenAPI 与 Bean Validation 补齐;下载授权不再返回服务端物理路径。
- 归档:删除/归档前检查 active dispatch、reception queue、reception group 引用。
- DLQ:重放 runKey 改为稳定幂等键。
- 旧上传服务:拒绝路径片段文件名,磁盘使用随机存储名,避免穿越和覆盖。

## 单体边界说明

`allowParallel/dynamicShardMax` 没有改成真正并行 shard。当前项目使用同步 `JobLauncher`,把 shard 包一层异步执行会释放调度许可但底层 Job 可能继续跑,反而引入双跑和状态聚合风险。本轮将调度 `default-timeout-ms` 语义收口为 `default-warn-threshold-ms`,保留旧配置兼容,明确它是慢执行告警阈值而不是硬取消。

## 验证

- `mvn -Punit-test test`: 283 passed, 0 failed, 1 skipped。
- `mvn spotless:apply spotless:check`: passed。
- `mvn -DskipTests compile spotbugs:check`: passed, 0 SpotBugs findings。
- `python scripts/check_flyway_safety.py`: passed。
- PostgreSQL integration: blocked by local environment before business logic; Docker daemon 未运行,local `localhost:5432` PostgreSQL refused connection。
