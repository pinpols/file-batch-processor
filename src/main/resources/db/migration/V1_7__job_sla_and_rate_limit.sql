-- ========================================================
-- V1_7: 作业 SLA 与限流配置
-- 为任务定义增加可选的 SLA 和速率限制字段。
-- ========================================================

ALTER TABLE task_definition
    ADD COLUMN IF NOT EXISTS sla_max_duration_ms BIGINT,
    ADD COLUMN IF NOT EXISTS sla_max_queue_delay_ms BIGINT,
    ADD COLUMN IF NOT EXISTS rate_limit_per_minute INTEGER;

