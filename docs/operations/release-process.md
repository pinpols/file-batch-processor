# Release Process
> 中文名：发布流程说明

## 1. 版本策略
- 采用语义化版本：`MAJOR.MINOR.PATCH`。
- 分支建议：`main`（稳定） + `codex/*`（变更分支）。

## 2. 发布前检查
1. 代码质量：`./mvnw -q test`
2. 依赖安全：OWASP / Trivy 扫描通过
3. 配置校验：生产环境变量齐全（数据库、告警、SFTP）
4. 数据库准备：已备份、迁移脚本评审通过

## 3. 发布步骤
1. 打包：`./mvnw -DskipTests clean package`
2. 部署：
   - Docker: `docker compose -f docker-compose.prod.yml up -d --build`
   - systemd: 替换 jar 后 `systemctl restart file-batch-processor`
3. 验证：
   - `/actuator/health`
   - 关键作业手工触发一次（导入/导出）
   - 告警平台无新增 P1/P2

## 4. 回滚步骤
1. 应用回滚到上一个稳定版本
2. 如涉及数据问题，执行数据库恢复脚本
3. 回滚后重新执行健康检查与冒烟

## 5. 发布后观察
- 观察窗口：至少 30 分钟
- 重点指标：失败率、吞吐、DLQ 积压、阻塞任务数
