# 🔧 运维手册

> 完整的系统运维、监控和故障处理指南

## 🎯 运维概览

### 📊 监控体系
| 监控类型 | 工具 | 覆盖范围 | 告警级别 |
|------|------|----------|----------|
| **应用监控** | Spring Boot Actuator | 应用健康、JVM、业务指标 | Critical/Warning |
| **基础设施监控** | Prometheus + Grafana | 系统、网络、存储 | Critical/Warning |
| **日志监控** | ELK Stack | 应用日志、错误日志 | Warning/Error |
| **链路追踪** | Spring Cloud Sleuth | 请求链路、性能分析 | Info/Warning |

### 🚨 告警策略
| 告警类型 | 阈值 | 通知方式 | 处理时效 |
|------|------|----------|----------|
| **服务不可用** | 健康检查失败 | 短信/邮件/钉钉 | 5分钟内 |
| **性能异常** | 响应时间 > 5秒 | 邮件/钉钉 | 15分钟内 |
| **错误率过高** | 错误率 > 5% | 邮件/钉钉 | 30分钟内 |
| **资源不足** | CPU > 80% | 邮件/钉钉 | 30分钟内 |

## 🛠️ 运维工具

### 📋 服务管理
```bash
# 查看服务状态
./scripts/ops/service-status.sh

# 重启服务
./scripts/ops/restart-service.sh app

# 滚动更新
./scripts/ops/rolling-update.sh

# 健康检查
./scripts/ops/health-check.sh
```

### 📊 监控检查
```bash
# 系统监控
./scripts/ops/system-monitor.sh

# 应用监控
./scripts/ops/app-monitor.sh

# 数据库监控
./scripts/ops/db-monitor.sh

# 网络监控
./scripts/ops/network-monitor.sh
```

### 🔧 故障处理
```bash
# 故障诊断
./scripts/ops/diagnose-issue.sh

# 服务恢复
./scripts/ops/recover-service.sh

# 数据恢复
./scripts/ops/data-recovery.sh

# 紧急回滚
./scripts/ops/emergency-rollback.sh
```

## 📈 性能监控

### 关键指标
| 指标类别 | 指标名称 | 正常范围 | 告警阈值 |
|------|------|----------|----------|
| **应用性能** | 响应时间 | < 2秒 | > 5秒 |
| **应用性能** | 吞吐量 | > 100 TPS | < 50 TPS |
| **应用性能** | 错误率 | < 1% | > 5% |
| **系统资源** | CPU 使用率 | < 70% | > 85% |
| **系统资源** | 内存使用率 | < 80% | > 90% |
| **系统资源** | 磁盘使用率 | < 80% | > 90% |

### 监控面板
- **Grafana 应用监控**：http://localhost:3000/d/app-overview
- **Grafana 系统监控**：http://localhost:3000/d/system-overview
- **Prometheus 指标**：http://localhost:9090
- **应用健康检查**：http://localhost:8011/actuator/health

### 性能调优
```bash
# JVM 调优
./scripts/ops/jvm-tune.sh

# 数据库调优
./scripts/ops/db-tune.sh

# 网络调优
./scripts/ops/network-tune.sh

# 系统调优
./scripts/ops/system-tune.sh
```

## 🔍 故障排查

### 常见故障类型

#### 1. 应用启动失败
**症状**：
- 应用无法启动
- 健康检查失败
- 日志显示启动错误

**排查步骤**：
```bash
# 1. 检查应用日志
docker logs file-batch-app

# 2. 检查配置文件
cat application.yml

# 3. 检查数据库连接
./scripts/ops/check-db-connection.sh

# 4. 检查端口占用
lsof -i :8011

# 5. 检查资源使用
docker stats
```

**解决方案**：
- 配置错误：修正配置文件
- 数据库连接失败：检查数据库状态
- 端口冲突：修改端口映射
- 资源不足：增加容器资源限制

#### 2. 性能下降
**症状**：
- 响应时间变长
- 吞吐量下降
- CPU/内存使用率升高

**排查步骤**：
```bash
# 1. 检查应用指标
curl http://localhost:8011/actuator/metrics

# 2. 分析慢查询
./scripts/ops/analyze-slow-queries.sh

# 3. 检查 GC 情况
./scripts/ops/check-gc.sh

# 4. 分析线程状态
./scripts/ops/analyze-threads.sh
```

**解决方案**：
- JVM 调优：调整堆大小、GC 参数
- 数据库优化：添加索引、优化查询
- 缓存优化：调整缓存策略
- 代码优化：优化算法、减少计算

#### 3. 数据库问题
**症状**：
- 数据库连接超时
- 查询执行缓慢
- 连接池耗尽

**排查步骤**：
```bash
# 1. 检查数据库状态
docker exec postgres pg_isready

# 2. 分析连接数
./scripts/ops/check-db-connections.sh

# 3. 检查锁等待
./scripts/ops/check-db-locks.sh

# 4. 分析慢查询日志
./scripts/ops/analyze-slow-queries.sh
```

**解决方案**：
- 连接池调优：增加最大连接数
- 查询优化：添加索引、重写查询
- 数据库调优：调整配置参数
- 硬件升级：增加内存、CPU

## 🚨 应急预案

### 服务不可用
**响应流程**：
1. **5分钟内**：确认故障，启动应急响应
2. **15分钟内**：定位问题，执行恢复操作
3. **30分钟内**：服务恢复，通知相关人员
4. **1小时内**：根因分析，制定预防措施

**应急操作**：
```bash
# 快速重启服务
./scripts/ops/emergency-restart.sh

# 切换到备用服务
./scripts/ops/switch-to-backup.sh

# 回滚到上一个版本
./scripts/ops/rollback-last-version.sh

# 启动应急模式
./scripts/ops/start-emergency-mode.sh
```

### 数据丢失
**响应流程**：
1. **立即**：停止相关服务，保护现场
2. **30分钟内**：评估数据丢失范围
3. **2小时内**：执行数据恢复
4. **24小时内**：完成数据验证

**恢复操作**：
```bash
# 从备份恢复
./scripts/ops/restore-from-backup.sh

# 启动数据修复
./scripts/ops/start-data-repair.sh

# 验证数据完整性
./scripts/ops/verify-data-integrity.sh
```

## 📋 运维流程

### 日常运维
**每日检查**：
- [ ] 服务健康状态检查
- [ ] 系统资源使用检查
- [ ] 错误日志分析
- [ ] 备份执行状态检查
- [ ] 性能指标趋势分析

**每周检查**：
- [ ] 系统更新和补丁检查
- [ ] 容量规划评估
- [ ] 备份策略验证
- [ ] 安全扫描执行
- [ ] 运维报告生成

**每月检查**：
- [ ] 灾备演练执行
- [ ] 性能基准测试
- [ ] 容量扩展计划
- [ ] 安全审计执行
- [ ] 运维流程优化

### 变更管理
**变更流程**：
1. **变更申请**：提交变更请求
2. **影响评估**：评估变更影响范围
3. **变更审批**：相关负责人审批
4. **变更实施**：在维护窗口执行
5. **变更验证**：验证变更效果
6. **变更记录**：记录变更详情

**变更类型**：
- **紧急变更**：安全漏洞修复、关键故障修复
- **标准变更**：功能更新、性能优化
- **计划变更**：版本升级、架构调整

## 📊 监控配置

### Prometheus 配置
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'file-batch-app'
    static_configs:
      - targets: ['app:8011']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

### Grafana 仪表板
- **应用概览**：应用健康、性能指标
- **系统概览**：系统资源、网络状态
- **业务指标**：任务执行、数据处理
- **告警概览**：告警状态、处理进度

### 告警规则
```yaml
# batch-alert-rules.yml
groups:
  - name: file-batch-alerts
    rules:
      - alert: ApplicationDown
        expr: up{job="file-batch-app"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "应用服务不可用"
      
      - alert: HighErrorRate
        expr: rate(http_requests_total{status="5"}[5m]) > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "错误率过高"
```

## 🔐 安全运维

### 安全检查
```bash
# 安全扫描
./scripts/ops/security-scan.sh

# 漏洞检查
./scripts/ops/vulnerability-check.sh

# 访问控制检查
./scripts/ops/access-control-check.sh

# 日志安全分析
./scripts/ops/security-log-analysis.sh
```

### 安全配置
- **访问控制**：基于角色的访问控制
- **数据加密**：敏感数据加密存储
- **网络安全**：防火墙、VPN 配置
- **审计日志**：操作审计、访问日志

## 📚 运维文档

### 操作手册
- [服务部署指南](deployment.md)
- [监控配置指南](monitoring.md)
- [故障处理手册](troubleshooting.md)
- [性能调优指南](performance-tuning.md)

### 应急预案
- [应急响应流程](emergency-response.md)
- [灾难恢复计划](disaster-recovery.md)
- [数据恢复流程](data-recovery.md)
- [通信联络表](contact-list.md)

## 📞 运维支持

### 问题报告
- **紧急故障**：电话 + 短信 + 邮件
- **一般问题**：工单系统 + 邮件
- **性能问题**：监控告警 + 邮件
- **安全问题**：安全热线 + 邮件

### 联系方式
- **运维团队**：ops@company.com
- **开发团队**：dev@company.com
- **安全团队**：security@company.com
- **值班电话**：+86-xxx-xxxx-xxxx

---

**🔧 所有运维工具都经过实战验证，确保系统稳定可靠运行**
