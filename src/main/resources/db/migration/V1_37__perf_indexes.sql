-- ========================================================
-- 性能索引补强:调度查询与历史数据清理
-- ========================================================

-- task_execution_state 热查询 findByTaskIdAndBatchDateAndRerunId 此前只能走单列 task_id 索引,
-- 补 (task_id, batch_date, rerun_id) 复合索引(非唯一,避免历史重复数据导致建索引失败)。
CREATE INDEX IF NOT EXISTS idx_task_exec_state_runkey
    ON task_execution_state (task_id, batch_date, rerun_id);

-- 数据保留清理此前是无索引全表扫删,为各删除条件补索引:
-- execution_dedup_records.deleteByCreatedAtBefore
CREATE INDEX IF NOT EXISTS idx_exec_dedup_created_at
    ON execution_dedup_records (created_at);

-- task_execution_state.deleteByUpdatedAtBeforeAndStatusIn
CREATE INDEX IF NOT EXISTS idx_task_exec_state_updated_status
    ON task_execution_state (updated_at, status);

-- dlq_records.deleteByHandledTrueAndHandledAtBefore
CREATE INDEX IF NOT EXISTS idx_dlq_records_handled_handled_at
    ON dlq_records (handled, handled_at);

-- batch_run_records.deleteByCreatedAtBefore
CREATE INDEX IF NOT EXISTS idx_batch_run_created_at
    ON batch_run_records (created_at);

-- 澄清:imported_records_partition 命名带 partition 但实为普通堆表,不存在真正的分区裁剪。
-- 真分区(按 batch_date RANGE 声明式分区)需重建表 + 数据迁移,属独立专项;先用 DB 注释消除"以为有分区裁剪"的误解,
-- 并已有 batch_date 相关索引兜住按批次查询的性能。
COMMENT ON TABLE imported_records_partition IS
    'NOTE: 普通堆表,非 PostgreSQL 声明式分区表(命名历史遗留);按 batch_date 的过滤走普通索引而非分区裁剪。真分区为独立专项。';
