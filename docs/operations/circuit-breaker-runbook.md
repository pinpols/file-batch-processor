# 熔断/降级运维手册
> 中文名：熔断运行手册

## 概述
系统按 `targetSystem` 维度实现熔断/降级，基于滑动窗口失败率阈值触发冷却（cooldown），状态持久化，重启不丢。

## 关键配置
| 配置项 | 说明 | 默认 | 生产建议 |
|---|---|---|---|
| `orchestration.circuit-breaker.window-size` | 滑动窗口大小（任务次数） | 10 | 10~50 |
| `orchestration.circuit-breaker.failure-rate-threshold` | 失败率阈值 | 0.5 | 0.3~0.7 |
| `orchestration.circuit-breaker.cooldown-duration-ms` | 冷却时间（毫秒） | 300000 | 180000~600000 |

## 状态流转
- **CLOSED**：正常，允许所有任务。
- **OPEN**：失败率超阈值，进入冷却，拒绝所有任务。
- **HALF_OPEN**：冷却结束，允许少量任务试探，成功则转 CLOSED，失败仍转 OPEN。

## 监控指标
- `circuit_open_total`：熔断打开次数（按 targetSystem 标签）
- `circuit_closed_total`：熔断关闭次数
- `circuit_half_open_total`：进入半开状态次数
- `scheduler_circuit_rejected_total`：任务被熔断拒绝次数

## 排障步骤

### 1) 确认熔断状态
```sql
SELECT target_system, status, window_failure_count, failure_rate_threshold, cooldown_until, updated_at
FROM target_system_circuit_state
WHERE status != 'CLOSED';
```

### 2) 查看近期失败日志
```bash
grep "circuit opened for targetSystem" logs/application.log | tail -20
```

### 3) 检查下游系统
- 网络连通性
- 目标服务健康状态
- 认证与权限

### 4) 手动恢复（可选）
如确认下游恢复，可手动关闭熔断：
```sql
UPDATE target_system_circuit_state
SET status = 'CLOSED', window_failure_count = 0, cooldown_until = NULL, updated_at = NOW()
WHERE target_system = '<YOUR_TARGET>';
```

### 5) 调整阈值（长期）
若误熔断频繁，可适当调高阈值或窗口大小；若故障恢复慢，可加长冷却时间。

## 告警建议
- 告警：`circuit_open_total` 增加
- 恢复：`circuit_closed_total` 增加
- 持续拒绝：`scheduler_circuit_rejected_total` 持续增长

## 相关文档
- 配置矩阵：`docs/configuration-matrix.md`
- 全局运维手册：`docs/ops/runbook.md`
