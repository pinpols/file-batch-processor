# 批量调度系统能力补齐计划 - 执行状态

> 更新日期: 2026-03-15

## 执行摘要

所有 10 个阶段已实现基础设施，后续需配合业务逐步推进切换。

---

## 阶段完成状态

| 阶段 | 内容 | 状态 | 说明 |
|------|------|------|------|
| 阶段 1 | 统一领域模型设计 | ✅ 完成 | 文档: docs/architecture/stage1-domain-model-design.md |
| 阶段 2 | 文件资产主表 | ✅ 完成 | V1_27 + FileAssetRecord 实体/服务 |
| 阶段 3 | 文件状态机 | ✅ 完成 | V1_28 + FileAssetStateMachineService + 状态变更写 log |
| 阶段 4 | 半文件防护与幂等 | ✅ 完成 | V1_28 + FileReceptionGuardService |
| 阶段 5 | 任务实例服务 | ✅ 完成 | V1_29 + JobInstanceService |
| 阶段 6 | 重试补偿统一化 | ✅ 完成 | V1_30, V1_32 + CompensationRecord |
| 阶段 7 | 出站分发与回执闭环 | ✅ 完成 | V1_31 |
| 阶段 8 | 运维台与审计台 | ✅ 完成 | OpsFileController, OpsBatchController, 审计日志 |
| 阶段 9 | 告警、监控、归档清理 | ✅ 完成 | FileAlertService, FileMetricsService, FileArchivalService |
| 阶段 10 | 切流与旧结构退役 | ⚠️ 框架完成 | MigrationService + Controller，业务切换需逐步推进 |

---

## 数据库迁移版本

当前最新: **V1_34**

| 版本 | 内容 |
|------|------|
| V1_27 | file_asset_record 主表 |
| V1_28 | file_process_log, file_reception_guard |
| V1_29 | business_job_instance |
| V1_30 | compensation_record (retry) |
| V1_31 | file_dispatch_record, dispatch ack |
| V1_32 | expand_compensation_action_types |
| V1_33 | file_alert_log, file_metrics_snapshot, file_retention_policy |
| V1_34 | migration_status, legacy_status |

---

## 阶段 10 特别说明

阶段 10 是流程性工作，基础设施已就绪：

### 已实现
- [x] migration_status 表 + 追踪能力
- [x] legacy_status 旧表标记
- [x] MigrationService.backfillFileRecords() 数据回填
- [x] MigrationService.switchToNewModel() 读路径切换
- [x] MigrationService.deprecateLegacyTable() 废弃标记
- [x] MigrationController API

### 待业务配合
- [ ] 双写检查：需在业务代码中同时写新旧表
- [ ] 读路径切换：逐步将查询切换到新表
- [ ] 退役旧逻辑：确认稳定后删除旧代码

### 切换顺序建议
1. 启用双写 (migration.enabled=true)
2. 观察新表数据完整性
3. 运维台/审计台切到读新表
4. 业务服务逐步切读新表
5. 停止旧写入
6. 退役旧表

---

## 配置参数

### 阶段 8-10 相关配置
```properties
# 运维台
ops.audit.enabled=true

# 告警
file.alert.enabled=true
file.alert.timeout.minutes=120
file.alert.unprocessed.threshold=100
file.alert.dispatch-ack-timeout.minutes=60

# 监控指标
file.metrics.enabled=true
file.metrics.cron=0 0 * * * *

# 归档
file.archive.enabled=true
file.archive.dry-run=true
file.archive.cron=0 0 2 * * *

# 迁移
migration.enabled=false
migration.batch-size=100
```

---

## API 端点

### 运维台 (阶段 8)
- `GET /ops/files` - 文件查询
- `GET /ops/files/{id}` - 文件详情
- `GET /ops/files/{id}/logs` - 处理日志
- `GET /ops/files/{id}/dispatches` - 分发记录
- `POST /ops/files/{id}/reprocess` - 文件重处理
- `POST /ops/batch/rerun` - 批次补跑
- `POST /ops/batch/compensate` - 补偿操作
- `POST /ops/batch/retry` - 重试操作

### 迁移 (阶段 10)
- `GET /ops/migration/status` - 所有迁移状态
- `GET /ops/migration/status/{name}` - 特定迁移
- `GET /ops/migration/health` - 迁移健康度
- `POST /ops/migration/backfill` - 触发回填
- `POST /ops/migration/switch/{type}` - 切换新模型
- `POST /ops/migration/deprecate/{table}` - 废弃旧表
