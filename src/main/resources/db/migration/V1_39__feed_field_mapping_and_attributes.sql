-- 声明式映射地基:feed 定义 + 字段映射 + imported_records_partition 加 attributes(#2 Phase 1)

CREATE TABLE IF NOT EXISTS feed_definition (
    feed_id VARCHAR(100) PRIMARY KEY,
    feed_name VARCHAR(200),
    format VARCHAR(20) NOT NULL DEFAULT 'CSV',
    delimiter VARCHAR(8) DEFAULT ',',
    has_header BOOLEAN NOT NULL DEFAULT TRUE,
    target_table VARCHAR(100) NOT NULL DEFAULT 'imported_records_partition',
    business_key_fields VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS field_mapping (
    id BIGSERIAL PRIMARY KEY,
    feed_id VARCHAR(100) NOT NULL REFERENCES feed_definition(feed_id),
    source_column VARCHAR(200) NOT NULL,
    target_field VARCHAR(200) NOT NULL,
    transform_op VARCHAR(20) NOT NULL DEFAULT 'NONE',
    transform_arg VARCHAR(200),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    order_no INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_field_mapping_target UNIQUE (feed_id, target_field)
);
CREATE INDEX IF NOT EXISTS idx_field_mapping_feed ON field_mapping(feed_id);

ALTER TABLE imported_records_partition ADD COLUMN IF NOT EXISTS attributes JSONB;
