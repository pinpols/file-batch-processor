# 项目结构说明

## 📁 核心目录

```
file-batch-processor/
├── 📁 src/                    # 源代码
│   ├── main/
│   │   ├── java/com/example/filebatchprocessor/
│   │   │   ├── 📁 batch/         # Spring Batch 作业和调度器
│   │   │   ├── 📁 config/        # 配置类
│   │   │   ├── 📁 controller/    # REST API 控制器
│   │   │   ├── 📁 model/         # JPA 实体模型
│   │   │   ├── 📁 repository/    # 数据访问层
│   │   │   ├── 📁 service/       # 业务服务层
│   │   │   ├── 📁 scheduler/      # 任务调度相关
│   │   │   └── 📁 util/          # 工具类
│   │   └── resources/
│   │       ├── 📁 db/migration/  # Flyway 数据库迁移
│   │       └── 📄 application.yml # 应用配置
├── 📁 scripts/                 # 运维脚本
│   ├── 📁 db/              # 数据库操作脚本
│   ├── 📁 local/           # 本地开发脚本
│   ├── 📁 qa/              # 测试环境脚本
│   └── 📁 testdata/         # 测试数据
├── 📁 docs/                   # 项目文档
│   ├── 📄 README.md         # 文档索引
│   ├── 📄 architecture.md    # 架构设计
│   └── 📁 ops/             # 运维手册
├── 📁 deploy/                 # 部署配置
│   └── 📁 systemd/          # Systemd 服务配置
└── 📄 pom.xml               # Maven 构建配置
```

## 🚀 快速开始

### 本地开发
```bash
# 启动本地环境
./scripts/local/start-local.sh

# 停止本地环境  
./scripts/local/stop-local.sh

# 生成 DAG 依赖图
./scripts/local/generate-dag-graph.sh
```

### 数据库管理
```bash
# 备份数据库
./scripts/db/backup.sh

# 恢复数据库
./scripts/db/restore.sh

# 加载测试数据
./scripts/testdata/load-all-testdata.sh
```

### 部署
```bash
# Systemd 部署
sudo ./deploy/systemd/install-systemd.sh

# Docker 部署
docker-compose -f docker-compose.prod.yml up -d
```

## 📋 核心功能

### 🔄 任务调度
- **CRON 调度**：复杂时间规则
- **固定频率**：按固定间隔执行
- **固定延迟**：任务完成后延迟执行
- **一次性执行**：指定时间执行一次

### 🏗️ 任务编排
- **依赖管理**：DAG 任务依赖图
- **并发控制**：任务级别并发限制
- **优先级调度**：任务优先级管理
- **状态跟踪**：完整的执行状态机

### 🛡️ 容错机制
- **重试策略**：指数退避重试
- **熔断器**：保护下游系统
- **死信队列**：失败任务处理
- **Misfire 恢复**：错失任务自动恢复

### 📊 监控运维
- **实时监控**：Prometheus + Grafana
- **链路追踪**：任务执行链路
- **审计日志**：完整操作审计
- **权限控制**：三级权限体系

## 🔧 配置说明

### 应用配置
- `application.yml`：主配置文件
- 环境变量：支持配置覆盖
- 配置源：支持 YAML/DB 两种配置源

### 任务配置
- **数据库配置**：通过管理界面配置
- **YAML 配置**：开发环境快速配置
- **参数化**：支持环境变量替换

## 📚 文档导航

| 文档 | 描述 |
|------|------|
| `docs/README.md` | 文档索引和导航 |
| `docs/architecture.md` | 系统架构设计 |
| `docs/ops/` | 运维手册和最佳实践 |
| `docs/configuration-matrix.md` | 配置参数说明 |
| `docs/jobs-matrix.md` | 作业类型和参数 |

## 🎯 最佳实践

### 开发规范
- 遵循分层架构：Controller → Service → Repository
- 使用 Lombok 简化代码
- 统一异常处理和日志记录
- 编写单元测试和集成测试

### 运维规范
- 使用环境变量管理敏感配置
- 启用数据库备份和监控
- 配置日志轮转和清理
- 定期更新依赖和安全补丁

### 部署规范
- 使用容器化部署
- 配置健康检查和负载均衡
- 实施蓝绿部署或滚动更新
- 建立灾备和恢复机制
