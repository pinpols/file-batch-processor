-- Seed trace and DLQ records for Trace API manual demo.

insert into record_trace (business_key, batch_date, job_name, job_execution_id, event_type, status, message, created_at)
values
  ('Alice:2026-03-01', '2026-03-01', 'fileImportJob', 1, 'IMPORT', 'SUCCESS', null, now()),
  ('Alice:2026-03-01', '2026-03-01', 'dataExportJob', 2, 'EXPORT', 'SUCCESS', 'exported', now());

insert into dlq_records (job_name, params, error_message, handled, created_at)
values
  ('fileImportJob', 'businessKey=Alice:2026-03-01&name=Alice&description=dlq&batchDate=2026-03-01&source=record-writer', 'synthetic', false, now());
