# 贡献指南

感谢您对 File Batch Processor 项目的关注！我们欢迎所有形式的贡献。

## 🎯 贡献方式

### 🐛 报告问题
- 发现 Bug 请提交 Issue
- 描述问题时请提供：
  - 详细的问题描述
  - 复现步骤
  - 环境信息（操作系统、Java 版本等）
  - 相关日志或截图

### 💡 功能建议
- 新功能建议请提交 Issue
- 建议内容请包含：
  - 功能描述和使用场景
  - 实现思路或参考
  - 预期效果

### 🔧 代码贡献
- Fork 项目到您的 GitHub 账户
- 创建功能分支：`git checkout -b feature/amazing-feature`
- 提交您的更改：`git commit -m 'Add some amazing feature'`
- 推送到分支：`git push origin feature/amazing-feature`
- 创建 Pull Request

## 📋 开发流程

### 环境准备
```bash
# 克隆项目
git clone https://github.com/your-username/file-batch-processor.git
cd file-batch-processor

# 安装依赖
./mvnw clean install

# 启动开发环境
./scripts/deployment/switch-env.sh dev
```

### 代码规范
- 遵循项目现有的代码风格
- 添加适当的单元测试
- 更新相关文档
- 确保所有测试通过

### 提交规范
- 使用清晰的提交信息
- 一个提交只做一件事
- 提交前运行测试：`./mvnw test`

## 📝 文档贡献

### 文档类型
- **用户文档**：使用指南、部署说明
- **开发文档**：API 文档、架构说明
- **运维文档**：部署指南、故障排查

### 文档规范
- 使用 Markdown 格式
- 遵循现有的文档结构
- 包含实际示例和代码片段
- 保持与代码同步更新

## 🧪 测试贡献

### 测试类型
- **单元测试**：测试单个组件
- **集成测试**：测试组件交互
- **端到端测试**：测试完整流程

### 测试数据
- 使用项目提供的测试数据
- 添加边界条件测试
- 包含异常情况测试

## 📋 代码审查

### 审查要点
- 代码逻辑正确性
- 性能影响评估
- 安全性检查
- 测试覆盖率

### 审查流程
- 所有 PR 需要代码审查
- 至少一个维护者审查通过
- 自动化检查必须通过

## 🏷️ 许可证

本项目采用 MIT 许可证，详情请参见 [LICENSE](LICENSE) 文件。

## 📞 联系方式

### 技术问题
- GitHub Issues：推荐用于 Bug 报告和功能请求
- 讨论：用于技术讨论和疑问

### 安全问题
- 请勿在公开 Issue 中报告安全漏洞
- 邮件联系：security@example.com

### 其他联系
- 邮件：maintainer@example.com
- 项目主页：https://github.com/your-username/file-batch-processor

## 🎉 致谢

感谢所有为项目做出贡献的开发者！您的贡献将被记录在：

- [贡献者列表](CONTRIBUTORS.md)
- [发布日志](CHANGELOG.md)
- [项目文档](docs/)

---

**🚀 我们期待您的贡献，让项目变得更好！**
