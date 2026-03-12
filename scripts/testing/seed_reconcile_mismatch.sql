-- Seed DB so that reconcile against reconcile_source_large.csv will FAIL.

delete from imported_records_partition where batch_date = '2026-03-02';

insert into imported_records_partition (business_key, name, description, batch_date, created_at)
values
  ('Alice:2026-03-02', 'ALICE', 'x', '2026-03-02', now());
