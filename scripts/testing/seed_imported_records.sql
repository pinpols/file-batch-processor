-- 本地验证基础导入数据。
-- 依赖 Flyway 已创建 schema；脚本可重复执行。

-- Replace batch_date as needed
-- \set batch_date '2026-03-01'

insert into imported_records (business_key, name, description, batch_date, created_at)
values
  ('Alice:2026-03-01', 'ALICE', 'first', '2026-03-01', now()),
  ('Bob:2026-03-01', 'BOB', 'second', '2026-03-01', now())
on conflict (business_key, batch_date) do update
set name = excluded.name,
    description = excluded.description,
    updated_at = now();

insert into imported_records_partition (business_key, name, description, batch_date, partition_key, created_at)
values
  ('Alice:2026-03-01', 'ALICE', 'first', '2026-03-01', 'p0', now()),
  ('Bob:2026-03-01', 'BOB', 'second', '2026-03-01', 'p0', now())
on conflict (business_key, batch_date, partition_key) do update
set name = excluded.name,
    description = excluded.description,
    updated_at = now();
