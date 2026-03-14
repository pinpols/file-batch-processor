# YAML Configuration Guide
> 中文名：YAML 配置项注释说明

## 1. 配置文件分层
| 文件 | 用途 | 建议 |
|---|---|---|
| `src/main/resources/application.yml` | 默认基线配置（已补充中文注释） | 本地与通用默认值放这里 |
| `src/main/resources/application-dev.yml` | 开发环境覆盖 | 仅放开发调优项 |
| `src/main/resources/application-prod.yml` | 生产环境覆盖 | 仅放生产安全默认，具体值走环境变量 |

## 2. 重点配置块（按模块）
| 配置前缀 | 作用 |
|---|---|
| `spring.datasource.*` | 主库连接配置 |
| `spring.quartz.*` | Quartz 内核与持久化策略 |
| `batch.alert.*` | 批处理告警阈值 |
| `batch.dlq.*` | 死信重放策略 |
| `batch.import.*` | 数据质量门禁（解析错误率/重复率） |
| `orchestration.scheduler.*` | 内建编排并发、重试、背压、leader |
| `orchestration.quartz.reset-on-startup` | 启动是否清理 Quartz orchestration 记录（默认 false） |
| `orchestration.circuit-breaker.*` | 下游系统熔断参数 |
| `ops.security.*` | 运维接口账号与角色 |

## 3. 使用建议
- 生产环境只改环境变量，不直接改仓库里的 `application-prod.yml` 明文敏感信息。
- 新增配置项时：
1. 在 `application.yml` 增加默认值与中文注释。
2. 在 `docs/architecture/configuration-matrix.md` 增加矩阵项。
3. 如影响调度/治理语义，补充到运行手册。

## 4. 快速校验
- 启动参数：`--spring.profiles.active=dev|prod`。
- 核查生效值：`/actuator/env` 与启动日志中的关键配置打印。
