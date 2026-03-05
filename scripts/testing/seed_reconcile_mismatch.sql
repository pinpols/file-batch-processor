-- Seed DB so that reconcile against reconcile_source_large.csv will FAIL.

delete from imported_record_partitioned where batch_date = '2026-03-02';

insert into imported_record_partitioned (business_key, name, description, batch_date, created_at)
values
  ('Alice:2026-03-02', 'ALICE', 'x', '2026-03-02', now());
