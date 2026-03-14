# P0/P1 覆盖缺口与模板
> 中文名：P0/P1 测试缺口与模板清单

## 1. 目标
- 给出当前仍未覆盖或覆盖不足的 P0/P1 场景。
- 为每个场景提供可直接落地的测试类模板（已在 `src/test/java` 创建）。

## 2. 缺口矩阵（当前）
| 优先级 | 场景 | 当前覆盖现状 | 模板类 |
|---|---|---|---|
| P0 | Quartz JDBC 持久化恢复（重启后 trigger/job 不丢） | 已补集成测试（Docker 环境执行） | `integration/QuartzJdbcRecoveryIT` |
| P0 | DAG 依赖超时窗口 + 失败动作（FAIL/SKIP）传播 | 已补集成测试（Docker 环境执行） | `integration/DagDependencyTimeoutIT` |
| P0 | DLQ 重放并发幂等（重复重放不重复落库） | 已补集成测试（Docker 环境执行） | `integration/DlqReplayIdempotencyIT` |
| P0 | 失败后 checkpoint 恢复（非整批重跑） | 已补可执行用例（执行级恢复链路） | `service/BatchCheckpointRestartE2ETest` |
| P1 | FIXED_DELAY 连续失败背压/限频 | 已补可执行用例（指数退避+最小间隔） | `service/FixedDelayBackpressurePolicyTest` |
| P1 | 变更审批并发一致性（重复审批/抢占） | 已补集成测试（Docker 环境执行） | `integration/OpsChangeApprovalConcurrencyIT` |
| P1 | Quartz misfire 告警联动（metrics->告警） | 已补集成测试（Docker 环境执行） | `integration/QuartzMisfireAlertIT` |

## 3. 运行方式
- fast-test（默认，仅 unit）：`./mvnw -q test`
- integration：`./mvnw -q -Pintegration-test test`
- e2e：`./mvnw -q -Pe2e-test test`
- full：`./mvnw -q -Pfull-test test`

## 4. 说明
- integration 用例默认依赖 Docker + Testcontainers，请设置：`ENABLE_DOCKER_TESTS=true`。
- 无 Docker 环境时，这些用例会自动跳过，不影响 fast-test。
