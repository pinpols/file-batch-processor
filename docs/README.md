# File Batch Processor 文档索引

本目录只保留长期维护文档：架构、接口、运维、开发规范、审计记录和用户使用说明。一次性计划、临时待办和执行完成报告不放在正式文档树中。

## 快速开始

| 文档 | 内容 |
| --- | --- |
| [本地部署指南](./user-guide/local-deployment.md) | 本地依赖、启动步骤和常见配置 |
| [作业配置示例](./user-guide/job-configuration-examples.md) | 导入、导出、多格式文件和声明式映射参数示例 |

## 开发者指南

| 文档 | 内容 |
| --- | --- |
| [开发规范](./developer-guide/standards.md) | 代码风格、命名、注释和提交要求 |
| [测试策略](./developer-guide/test-strategy.md) | 单元测试、集成测试、E2E 测试和 Testcontainers 使用约定 |

## 架构设计

| 文档 | 内容 |
| --- | --- |
| [系统架构](./architecture/architecture.md) | 模块边界、核心链路和运行时组件 |
| [架构与技术栈](./architecture/ARCHITECTURE_AND_TECH_STACK.md) | 技术选型和关键依赖 |
| [数据库表结构](./architecture/database-schema.md) | 业务表、调度表和治理表总览 |
| [配置矩阵](./architecture/configuration-matrix.md) | 主要配置项、默认值和环境变量 |
| [任务配置表结构](./architecture/task-configuration-schema.md) | `task_definition` 等任务配置表说明 |
| [YAML 配置说明](./architecture/yaml-configuration-guide.md) | 本地 YAML 配置方式和限制 |

## API 参考

| 文档 | 内容 |
| --- | --- |
| [任务管理 API](./api/job-management-api.md) | 作业触发、查询和管理接口 |
| [任务矩阵](./api/jobs-matrix.md) | 内置作业清单和入口 |
| [任务参数契约](./api/jobs-params-contract.md) | 作业参数、必填项和默认行为 |

## 运维手册

| 文档 | 内容 |
| --- | --- |
| [运维概览](./operations/README.md) | 日常操作入口 |
| [部署检查清单](./operations/deploy-checklist.md) | 上线前检查项 |
| [发布流程](./operations/release-process.md) | 构建、验证和发布步骤 |
| [运行手册](./operations/runbook.md) | 常见运维操作 |
| [事件响应 SOP](./operations/incident-sop.md) | 故障响应流程 |
| [SLA/SLO](./operations/slo-sla.md) | 服务目标和告警口径 |
| [安全基线](./operations/security-baseline.md) | 认证、路径、协议和凭据要求 |
| [Systemd 部署](./operations/systemd-deploy.md) | systemd 部署方式 |
| [DB 初始化](./operations/db-bootstrap.md) | 数据库初始化说明 |
| [质量门禁](./operations/quality-gate-runbook.md) | 导入/导出质量检查和处置 |
| [熔断器运行手册](./operations/circuit-breaker-runbook.md) | 熔断状态和恢复流程 |
| [加密压缩导入](./operations/encrypted-compressed-intake.md) | PGP、gzip、zip 文件导入约束 |
| [清单驱动入库](./operations/manifest-driven-intake.md) | manifest 到齐、对账和放行流程 |
| [声明式映射](./operations/declarative-mapping.md) | feed 与字段映射配置 |
| [多告警渠道](./operations/alerting-channels.md) | webhook、email、IM 告警配置 |

## 可观测性与审计

| 文档 | 内容 |
| --- | --- |
| [日志规范](./observability/logging-spec.md) | 日志字段、级别和 trace 约定 |
| [综合审计记录](./audit/2026-06-28-comprehensive-audit.md) | 架构、安全、运维和测试审计结论 |
| [单体批处理审计记录](./operations/monolith-batch-audit-2026-06-28.md) | 单体项目边界内的修复与遗留风险 |

## 目录结构

```text
file-batch-processor/
├── src/main/java/com/example/filebatchprocessor/
│   ├── batch/           # Spring Batch 作业、reader、writer、调度组件
│   ├── config/          # Spring 配置和配置属性
│   ├── controller/      # 运维与查询接口
│   ├── model/           # JPA 实体与领域枚举
│   ├── repository/      # Spring Data Repository
│   ├── service/         # 业务服务和运维能力
│   └── observability/   # 指标、健康检查和日志上下文
├── src/main/resources/
│   ├── db/migration/    # Flyway 迁移
│   └── application.yml  # 默认配置
├── src/test/            # 单元、集成和 E2E 测试
├── scripts/             # 本地、部署、数据库和测试辅助脚本
├── docs/                # 长期维护文档
├── deploy/              # systemd 等部署配置
├── ops/                 # Prometheus、Grafana、Alertmanager 配置
└── pom.xml
```
