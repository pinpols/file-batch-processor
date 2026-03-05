-- ========================================================
-- V1_18: task_trigger 增加 FIXED_DELAY 落库支持
-- 1) 新增 fixed_delay_ms 列
-- 2) 按 fixed_delay_ms 去重 FIXED_DELAY 历史数据
-- 3) 增加 FIXED_DELAY 唯一索引
-- ========================================================

ALTER TABLE task_trigger
    ADD COLUMN IF NOT EXISTS fixed_delay_ms BIGINT;

-- 清理 FIXED_DELAY 重复数据（保留最小 id）
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY task_id, trigger_type, COALESCE(fixed_delay_ms, -1)
               ORDER BY id
           ) AS rn
    FROM task_trigger
    WHERE trigger_type = 'FIXED_DELAY'
)
DELETE FROM task_trigger t
USING ranked r
WHERE t.id = r.id
  AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_trigger_fixed_delay
ON task_trigger (task_id, trigger_type, fixed_delay_ms)
WHERE trigger_type = 'FIXED_DELAY';
