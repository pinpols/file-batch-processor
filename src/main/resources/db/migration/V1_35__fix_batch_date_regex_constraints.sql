UPDATE imported_records
SET batch_date_d = CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS DISTINCT FROM CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END;

ALTER TABLE imported_records
    DROP CONSTRAINT IF EXISTS ck_imported_records_batch_date_format;

ALTER TABLE imported_records
    ADD CONSTRAINT ck_imported_records_batch_date_format
    CHECK (batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') NOT VALID;

UPDATE imported_records_partition
SET batch_date_d = CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS DISTINCT FROM CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END;

ALTER TABLE imported_records_partition
    DROP CONSTRAINT IF EXISTS ck_imported_records_partition_batch_date_format;

ALTER TABLE imported_records_partition
    ADD CONSTRAINT ck_imported_records_partition_batch_date_format
    CHECK (batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') NOT VALID;

UPDATE execution_dedup_records
SET batch_date_d = CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS DISTINCT FROM CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END;

ALTER TABLE execution_dedup_records
    DROP CONSTRAINT IF EXISTS ck_exec_dedup_batch_date_format;

ALTER TABLE execution_dedup_records
    ADD CONSTRAINT ck_exec_dedup_batch_date_format
    CHECK (batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') NOT VALID;

UPDATE task_execution_state
SET batch_date_d = CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END
WHERE batch_date_d IS DISTINCT FROM CASE
    WHEN batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN batch_date::DATE
    ELSE NULL
END;

ALTER TABLE task_execution_state
    DROP CONSTRAINT IF EXISTS ck_task_exec_state_batch_date_format;

ALTER TABLE task_execution_state
    ADD CONSTRAINT ck_task_exec_state_batch_date_format
    CHECK (batch_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') NOT VALID;
