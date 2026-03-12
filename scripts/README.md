# Scripts Index

## Database
- `scripts/database/init-db.sql`: Docker/PostgreSQL init hook (creates role and grants on current database).
- `scripts/database/backup.sh`: Database backup.
- `scripts/database/restore.sh`: Database restore.

Flyway migrations are applied by the application on startup, including `V1_20__quartz_postgresql_tables.sql`.

## Local
- `scripts/local/start-local.sh`: Start app (and optional admin components).
- `scripts/local/stop-local.sh`: Stop app (and optional admin components).
- `scripts/local/generate-dag-graph.sh`: Render DAG graph from DB.

## Testing
- `scripts/testing/*`: Seed and load test data for local dev.
