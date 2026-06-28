-- Seed DB so that reconcile against reconcile_source_large.csv will FAIL.

delete from imported_records_partition where batch_date = '2026-03-02';

insert into imported_records_partition (business_key, name, description, batch_date, partition_key, created_at)
values
  ('Alice:2026-03-02', 'ALICE', 'x', '2026-03-02', 'p0', now())
on conflict (business_key, batch_date, partition_key) do update
set name = excluded.name,
    description = excluded.description,
    updated_at = now();
