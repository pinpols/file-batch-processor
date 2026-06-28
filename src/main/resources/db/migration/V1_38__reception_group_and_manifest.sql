-- 清单驱动入库:到达组 + 成员 + 控制文件对账。

CREATE TABLE IF NOT EXISTS reception_group (
    id BIGSERIAL PRIMARY KEY,
    manifest_id VARCHAR(200) NOT NULL UNIQUE,
    source_system VARCHAR(100),
    biz_date VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING_FILES',
    total_members INTEGER NOT NULL DEFAULT 0,
    arrived_members INTEGER NOT NULL DEFAULT 0,
    deadline TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_reception_group_status ON reception_group(status);

CREATE TABLE IF NOT EXISTS reception_group_member (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES reception_group(id),
    expected_file_name VARCHAR(500) NOT NULL,
    expected_record_count BIGINT,
    expected_checksum VARCHAR(128),
    checksum_algorithm VARCHAR(20) DEFAULT 'MD5',
    required BOOLEAN NOT NULL DEFAULT TRUE,
    actual_queue_id BIGINT,
    actual_record_count BIGINT,
    reconcile_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_group_member_file UNIQUE (group_id, expected_file_name)
);
CREATE INDEX IF NOT EXISTS idx_group_member_group ON reception_group_member(group_id);

ALTER TABLE file_reception_queue ADD COLUMN IF NOT EXISTS reception_group_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_file_reception_group ON file_reception_queue(reception_group_id);

-- 扩展 file_alert_log.alert_type CHECK 约束(新增 GROUP_INCOMPLETE / GROUP_RECONCILE_FAIL)。
-- V1_33 中该约束名为 ck_file_alert_log_type,形式为直接 CHECK (alert_type IN (...)),含 7 个已有类型。
-- 此处先 DROP 原约束再以全量类型重建,不破坏已有 7 个类型。
-- 注意:DROP 用 IF EXISTS 而非 pg_constraint.conname 守门 —— conname 不带 schema 限定,
-- 在 schema 隔离(测试容器每次随机 schema)场景下按 conname 查 pg_constraint 会跨 schema 误判;
-- DROP CONSTRAINT IF EXISTS 直接作用于本表本 schema,既幂等又无歧义。
ALTER TABLE file_alert_log DROP CONSTRAINT IF EXISTS ck_file_alert_log_type;
ALTER TABLE file_alert_log
    ADD CONSTRAINT ck_file_alert_log_type
    CHECK (alert_type IN ('FILE_TIMEOUT', 'FILE_UNPROCESSED', 'DISPATCH_NO_ACK',
                       'DISK_SPACE', 'DIRECTORY_BACKLOG', 'ARCHIVE_FAILURE',
                       'METRICS_THRESHOLD',
                       'GROUP_INCOMPLETE', 'GROUP_RECONCILE_FAIL'));

-- seed reception-group-monitor task(默认禁用,FIXED_RATE 2 分钟)
INSERT INTO task_definition (task_id, job_name, description, priority, allow_parallel, enabled)
VALUES ('reception-group-monitor', 'receptionGroupJob', '清单到达组监控:每 2 分钟检查组是否到齐并对账', 'NORMAL', TRUE, FALSE)
ON CONFLICT (task_id) DO NOTHING;

INSERT INTO task_trigger (task_id, trigger_type, fixed_rate_ms, enabled)
SELECT 'reception-group-monitor', 'FIXED_RATE', 120000, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM task_trigger WHERE task_id = 'reception-group-monitor' AND trigger_type = 'FIXED_RATE');
