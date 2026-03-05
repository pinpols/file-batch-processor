-- ========================================================
-- V1_6: 多租户 / 业务域 维度扩展
-- 为批量任务与运行记录增加 tenant / biz_domain / env 字段，
-- 先作为可选维度引入，不改变现有主键与行为。
-- ========================================================

-- 任务定义：为后续按租户/业务域配置任务做准备
ALTER TABLE task_definition
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS biz_domain VARCHAR(64),
    ADD COLUMN IF NOT EXISTS env VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_task_definition_tenant
    ON task_definition (tenant_id);

CREATE INDEX IF NOT EXISTS idx_task_definition_biz_domain
    ON task_definition (biz_domain);

-- 任务执行状态机：用于按租户/业务域观察与治理任务执行
ALTER TABLE task_execution_state
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS biz_domain VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_task_exec_state_tenant
    ON task_execution_state (tenant_id);

CREATE INDEX IF NOT EXISTS idx_task_exec_state_biz_domain
    ON task_execution_state (biz_domain);

-- 批次运行审计记录：按租户/业务域聚合批次质量与性能
ALTER TABLE batch_run_records
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS biz_domain VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_batch_run_tenant
    ON batch_run_records (tenant_id);

CREATE INDEX IF NOT EXISTS idx_batch_run_biz_domain
    ON batch_run_records (biz_domain);

-- 文件分发任务：便于按租户/业务域做下游路由与限流
ALTER TABLE file_distribution_task
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS biz_domain VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_file_dist_tenant
    ON file_distribution_task (tenant_id);

CREATE INDEX IF NOT EXISTS idx_file_dist_biz_domain
    ON file_distribution_task (biz_domain);

