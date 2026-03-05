# Incident SOP（告警处置）
> 中文名：故障处置标准流程

## 流程
1. 接警确认：确认告警名、开始时间、影响范围。
2. 快速分级：P1（全链路不可用）、P2（部分失败）、P3（可延迟处理）。
3. 止血策略：降并发、暂停非关键任务、切换补偿链路。
4. 根因定位：应用日志 + 任务状态 + DB连接 + 上下游连通性。
5. 恢复执行：重启任务/重放DLQ/断点续跑。
6. 复盘闭环：记录原因、影响、修复项、预防项。

## 告警到动作映射
| 告警 | 首要动作 | 二线动作 |
|---|---|---|
| `BatchServiceDown` | 检查进程与端口，立即恢复服务 | 查看最近发布与系统资源 |
| `BatchJobFailureRateHigh` | 拉取失败样本，判断数据或依赖故障 | 暂停相关任务并启用补跑窗口 |
| `BatchDlqBacklogHigh` | 分析 `dlq_records` 原因分布 | 修复后执行 `dlqReplayJob` |
| `BatchThroughputLow` | 检查DB慢查询与锁等待 | 调整并发/分片/批大小 |
| `BatchBlockedTasksHigh` | 检查依赖任务与文件到达情况 | 放宽依赖超时窗口并补跑 |

## 核心排查命令
```bash
# 服务状态
curl -s http://localhost:8011/actuator/health

# 指标
curl -s http://localhost:8011/actuator/prometheus | grep batch_

# systemd 日志
journalctl -u file-batch-processor -f
```
