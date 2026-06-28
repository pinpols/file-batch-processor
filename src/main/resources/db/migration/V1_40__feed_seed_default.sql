-- 对照 feed:复刻默认导入语义(name 转大写 + description 原样 + business_key=name:batchDate),供接线回归比对。
INSERT INTO feed_definition (feed_id, feed_name, format, delimiter, has_header, target_table, business_key_fields, enabled)
VALUES ('default-csv', 'Default CSV (parity feed)', 'CSV', ',', TRUE, 'imported_records_partition', NULL, TRUE)
ON CONFLICT (feed_id) DO NOTHING;

INSERT INTO field_mapping (feed_id, source_column, target_field, transform_op, transform_arg, required, order_no, enabled)
VALUES ('default-csv', 'name', 'name', 'UPPER', NULL, TRUE, 1, TRUE)
ON CONFLICT (feed_id, target_field) DO NOTHING;

INSERT INTO field_mapping (feed_id, source_column, target_field, transform_op, transform_arg, required, order_no, enabled)
VALUES ('default-csv', 'description', 'description', 'NONE', NULL, FALSE, 2, TRUE)
ON CONFLICT (feed_id, target_field) DO NOTHING;
