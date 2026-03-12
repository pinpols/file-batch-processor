-- Seed imported_records_partition and imported_records for manual testing.
-- Assumes schema already created by Flyway.

-- Replace batch_date as needed
-- \set batch_date '2026-03-01'

insert into imported_records (business_key, name, description, batch_date, created_at)
values
  ('Alice:2026-03-01', 'ALICE', 'first', '2026-03-01', now()),
  ('Bob:2026-03-01', 'BOB', 'second', '2026-03-01', now());

insert into imported_records_partition (business_key, name, description, batch_date, created_at)
values
  ('Alice:2026-03-01', 'ALICE', 'first', '2026-03-01', now()),
  ('Bob:2026-03-01', 'BOB', 'second', '2026-03-01', now());
