#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE=${1:-}
if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 <backup_file.dump>" >&2
  exit 1
fi

DB_NAME=${POSTGRES_DB:-file_batch}
DB_HOST=${POSTGRES_HOST:-localhost}
DB_PORT=${POSTGRES_PORT:-5432}
DB_USER=${POSTGRES_USER:-filebatch}

if [ -z "${POSTGRES_PASSWORD:-}" ]; then
  echo "POSTGRES_PASSWORD is required" >&2
  exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

export PGPASSWORD="$POSTGRES_PASSWORD"
pg_restore --clean --if-exists -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$BACKUP_FILE"
unset PGPASSWORD

echo "Restore completed from: $BACKUP_FILE"
