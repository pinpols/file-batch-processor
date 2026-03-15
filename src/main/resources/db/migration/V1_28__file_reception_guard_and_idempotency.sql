-- Phase 4: half-file guardrails and inbound file idempotency

ALTER TABLE file_record
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(256);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_record_idempotency_key
    ON file_record(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN file_record.idempotency_key IS 'Inbound file dedup key, e.g. INBOUND|ERP|20260315|<hash>';
