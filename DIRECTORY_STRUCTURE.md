# 📁 项目目录结构

> 清晰有序的 scripts 和 docs 目录组织

## 🎯 目录概览

### 🛠️ scripts/ - 脚本工具中心
```
scripts/
├── 🚀 deployment/          # 部署脚本
│   ├── deploy.sh           # Docker 部署
│   ├── local-deploy.sh     # 本地部署
│   └── switch-env.sh       # 环境切换
├── 🧹 maintenance/         # 维护脚本
│   └── cleanup.sh          # 项目清理
├── 🗃️ database/           # 数据库脚本
│   ├── backup.sh           # 数据库备份
│   ├── restore.sh          # 数据库恢复
│   └── init-db.sql         # 数据库初始化
├── 🧪 testing/             # 测试脚本
│   ├── testdata/           # 测试数据
│   │   ├── *.csv          # CSV 数据文件
│   │   ├── *.sql          # SQL 种子文件
│   │   └── *.sh          # 测试脚本
│   └── qa/               # QA 测试
├── 🏠 local/               # 本地开发
│   ├── start-local.sh      # 本地启动
│   ├── stop-local.sh       # 本地停止
│   └── generate-dag-graph.sh # DAG 图生成
├── 📊 monitoring/          # 监控脚本 (预留)
└── README.md              # 脚本使用指南
```

### 📚 docs/ - 文档中心
```
docs/
├── 📖 user-guide/          # 用户指南
│   └── local-deployment.md # 本地部署
├── 👨‍💻 developer-guide/     # 开发者指南
│   ├── test-strategy.md    # 测试策略
│   └── standards.md         # 开发标准
├── 🔧 operations/          # 运维文档
│   ├── ops/               # 运维手册
│   ├── release-process.md    # 发布流程
│   ├── security-baseline.md # 安全基线
│   └── slo-sla.md          # 服务等级协议
├── 🏗️ architecture/        # 架构文档
│   ├── architecture.md      # 系统架构
│   └── configuration-matrix.md # 配置矩阵
├── 📡 api/                 # API 文档
│   ├── jobs-matrix.md       # 作业矩阵
│   └── jobs-params-contract.md # 作业参数契约
├── 📊 monitoring/          # 监控文档
│   └── observability/      # 可观测性
├── 📚 tutorials/           # 教程文档 (预留)
├── 🎨 assets/             # 资源文件 (预留)
└── README.md              # 文档导航
```

## 🚀 快速使用

### 脚本工具
```bash
# 开发环境部署
./scripts/deployment/switch-env.sh dev

# 生产环境部署
./scripts/deployment/local-deploy.sh deploy prod

# 项目清理
./scripts/maintenance/cleanup.sh

# 测试环境初始化
./scripts/testing/testdata/init-test-environment.sh
```

### 文档查找
```bash
# 脚本使用指南
cat scripts/README.md

# 文档导航中心
cat docs/README.md

# 运维手册
cat docs/operations/ops/README.md
```

## 📊 优化效果

| 方面 | 优化前 | 优化后 |
|------|----------|----------|
| **目录结构** | 混乱无序 | 功能分类清晰 |
| **查找效率** | 困难耗时 | 快速直观 |
| **使用体验** | 复杂易错 | 简单明了 |
| **维护成本** | 高频手动 | 低频自动 |

## 🎯 设计原则

### 目录命名
- 使用小写字母和连字符
- 名称明确表达用途
- 避免缩写和模糊词汇

### 文件组织
- 按功能分类
- 按角色分组
- 保持层级合理

### 导航设计
- 每个目录都有 README
- 提供使用示例
- 建立交叉引用

---

**🎉 目录结构优化完成，项目更加清晰有序！**
