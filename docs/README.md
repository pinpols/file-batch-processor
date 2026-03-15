# 📚 File Batch Processor 文档中心

> 项目文档导航索引

## 📖 文档目录

### 🚀 快速开始
| 文档 | 说明 |
|------|------|
| [本地部署指南](./user-guide/local-deployment.md) | 本地开发环境搭建 |

### 👨‍💻 开发者指南
| 文档 | 说明 |
|------|------|
| [开发规范](./developer-guide/standards.md) | 代码规范、命名约定 |
| [测试策略](./developer-guide/test-strategy.md) | 单元测试、集成测试策略 |

### 🏗️ 架构设计
| 文档 | 说明 |
|------|------|
| [系统架构](./architecture/architecture.md) | 整体架构设计 |
| [配置矩阵](./architecture/configuration-matrix.md) | 配置项说明 |
| [数据库表结构](./architecture/database-schema.md) | 业务表/调度表/治理表结构总览 |
| [YAML配置说明](./architecture/yaml-configuration-guide.md) | 配置文件分层与注释说明 |

### 📡 API 参考
| 文档 | 说明 |
|------|------|
| [任务管理API](./api/job-management-api.md) | 任务调度、管理的REST API |
| [任务矩阵](./api/jobs-matrix.md) | 所有任务清单 |
| [任务参数契约](./api/jobs-params-contract.md) | 任务参数定义 |

### 🔍 可观测性
| 文档 | 说明 |
|------|------|
| [日志规范](./observability/logging-spec.md) | 日志格式、级别规范 |

### 🔧 运维手册
| 文档 | 说明 |
|------|------|
| [运维概览](./operations/README.md) | 监控、告警、故障处理 |
| [发布流程](./operations/release-process.md) | 版本发布步骤 |
| [部署检查清单](./operations/deploy-checklist.md) | 部署前检查项 |
| [DB初始化](./operations/db-bootstrap.md) | 数据库初始化脚本 |
| [SLA/SLO](./operations/slo-sla.md) | 服务等级协议 |
| [运行手册](./operations/runbook.md) | 日常运维操作 |
| [质量门禁](./operations/quality-gate-runbook.md) | 质量检查流程 |
| [熔断器运行手册](./operations/circuit-breaker-runbook.md) | 熔断器配置使用 |
| [事件响应SOP](./operations/incident-sop.md) | 事故响应流程 |
| [安全基线](./operations/security-baseline.md) | 安全配置要求 |
| [Systemd部署](./operations/systemd-deploy.md) | Systemd服务部署 |

### 📊 测试报告
| 文档 | 说明 |
|------|------|
| [E2E测试报告](./test/FINAL_E2E_COMPLETION_REPORT.md) | 端到端测试结果 |
| [测试完成报告](./test/FINAL_TEST_COMPLETION_REPORT.md) | 测试执行总结 |

### 📦 其他
| 文档 | 说明 |
|------|------|
| [架构与技术栈](./architecture/ARCHITECTURE_AND_TECH_STACK.md) | 技术栈说明 |
| [任务配置表结构](./architecture/task-configuration-schema.md) | 任务配置表结构说明 |
| [当前能力总结](./todo_analysis_plans/CURRENT_CAPABILITIES_SUMMARY_TODO.md) | 基于V1_27-V1_34迁移的当前系统能力总结 |
| [早期规划文档](./todo_analysis_plans/system-capability-completion-260312.md) | 批量调度系统补齐实施计划 |
| [能力缺口分析](./todo_analysis_plans/system-capability-gap-analysis-260312.md) | 系统能力与缺口分析（历史参考） |
| [待办事项](./todo_analysis_plans/todo_260312.md) | 批量调度系统能力补齐计划状态 |

---

## 📁 项目目录结构

```
file-batch-processor/
├── src/
│   ├── main/
│   │   ├── java/com/example/filebatchprocessor/
│   │   │   ├── batch/           # Spring Batch 作业配置
│   │   │   ├── config/          # 配置类
│   │   │   ├── controller/      # REST API 控制器
│   │   │   ├── exception/       # 异常处理
│   │   │   ├── listener/        # 事件监听器
│   │   │   ├── model/           # JPA 实体模型
│   │   │   ├── observability/   # 可观测性组件
│   │   │   ├── params/          # 参数模型
│   │   │   ├── repository/      # 数据访问层
│   │   │   ├── scheduler/       # 任务调度相关
│   │   │   ├── service/         # 业务服务层
│   │   │   └── util/            # 工具类
│   │   └── resources/
│   │       ├── db/migration/    # Flyway 数据库迁移
│   │       └── application.yml  # 应用配置
│   └── test/
│       └── java/.../
│           ├── e2e/             # 端到端测试
│           ├── integration/     # 集成测试
│           ├── support/         # 测试支持类
│           └── unit/            # 单元测试
├── scripts/
│   ├── database/                # 数据库脚本
│   ├── deployment/             # 部署脚本
│   ├── local/                  # 本地开发脚本
│   ├── maintenance/            # 维护脚本
│   ├── testing/                # 测试脚本
│   └── testdata/               # 测试数据
├── docs/                       # 项目文档
├── deploy/                     # 部署配置
├── docker-compose.dev.yml      # 开发环境
├── docker-compose.prod.yml     # 生产环境
├── Dockerfile
└── pom.xml
```

---

## 🔗 快速链接

- **GitHub**: https://github.com/example/file-batch-processor
- **问题反馈**: https://github.com/example/file-batch-processor/issues
- **版本历史**: [CHANGELOG.md](../CHANGELOG.md)

## 📝 贡献指南

欢迎贡献代码！请阅读 [CONTRIBUTING.md](../CONTRIBUTING.md) 了解贡献流程。
