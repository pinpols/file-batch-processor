# 上线检查清单（Go-Live Checklist）
> 中文名：上线检查清单

## 1. 环境与依赖
- [ ] JDK 25 与 Maven 3.9+ 已确认
- [ ] Docker / Docker Compose 可用
- [ ] PostgreSQL 17 实例可访问（网络、账号、库名）
- [ ] 时区统一（建议 Asia/Shanghai）

## 2. 配置项
- [ ] `.env` 已从 `.env.example` 复制并替换敏感信息
- [ ] `SPRING_DATASOURCE_*` 与目标数据库一致
- [ ] `BATCH_ALERT_*` 阈值和通知开关已确认

## 3. 数据与迁移
- [ ] 首次启动完成全部 SQL 迁移
- [ ] 核心表存在：`task_config`、`task_execution`、`imported_records`、`dlq_records`、`batch_run_record`
- [ ] 索引与唯一键有效（特别是导入幂等唯一键）

## 4. 任务与调度
- [ ] 导入任务可触发并成功入库
- [ ] 导出任务可生成文件并落盘
- [ ] 依赖超时窗口与补跑窗口参数已配置
- [ ] `dlqReplayJob` 与 `batchRestartJob` 已联调

## 5. 可观测性
- [ ] `/actuator/health` 返回 `UP`
- [ ] `/actuator/prometheus` 可被 Prometheus 抓取
- [ ] 告警规则已加载（Prometheus Rules 页面可见）
- [ ] Alertmanager 通知通道已联通

## 6. 运维预案
- [ ] 备份脚本可执行：`scripts/db/backup.sh`
- [ ] 恢复脚本可执行：`scripts/db/restore.sh`
- [ ] 故障处置手册已演练（DB中断、文件未到、下游失败、DLQ堆积）
- [ ] 发布回滚策略已确认（镜像版本 + 数据恢复点）
