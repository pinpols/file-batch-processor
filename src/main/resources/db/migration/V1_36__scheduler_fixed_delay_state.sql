-- ========================================================
-- FIXED_DELAY 触发器的退避状态持久化
-- 此前 failureStreak/lastScheduledAt 仅存于内存 ConcurrentMap，
-- 应用重启即丢失，退避从头累积。改为写穿到本表以跨重启保留。
-- 主键 = task_id（每个 FIXED_DELAY 任务一行）。
-- ========================================================
CREATE TABLE IF NOT EXISTS scheduler_fixed_delay_state (
    task_id VARCHAR(100) PRIMARY KEY,
    failure_streak INTEGER NOT NULL DEFAULT 0,
    last_scheduled_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
