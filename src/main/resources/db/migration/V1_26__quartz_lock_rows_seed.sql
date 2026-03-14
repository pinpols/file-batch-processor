-- Ensure Quartz lock rows exist for each scheduler name.
-- Missing rows can cause lock queries to fail under JDBC JobStore.

WITH scheduler_names AS (
    SELECT DISTINCT sched_name
    FROM (
        SELECT sched_name FROM qrtz_locks
        UNION
        SELECT sched_name FROM qrtz_scheduler_state
        UNION
        SELECT sched_name FROM qrtz_triggers
        UNION
        SELECT sched_name FROM qrtz_job_details
        UNION
        SELECT 'fileBatchQuartzScheduler'::varchar
    ) s
    WHERE sched_name IS NOT NULL
),
required_lock_names AS (
    SELECT unnest(ARRAY[
        'TRIGGER_ACCESS',
        'JOB_ACCESS',
        'CALENDAR_ACCESS',
        'STATE_ACCESS',
        'MISFIRE_ACCESS'
    ]) AS lock_name
)
INSERT INTO qrtz_locks (sched_name, lock_name)
SELECT s.sched_name, l.lock_name
FROM scheduler_names s
CROSS JOIN required_lock_names l
ON CONFLICT DO NOTHING;
