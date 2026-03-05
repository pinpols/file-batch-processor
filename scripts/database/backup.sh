#!/usr/bin/env bash
set -euo pipefail

OUT_DIR=${1:-./backup}
TS=$(date +%Y%m%d_%H%M%S)
DB_NAME=${POSTGRES_DB:-file_batch}
DB_HOST=${POSTGRES_HOST:-localhost}
DB_PORT=${POSTGRES_PORT:-5432}
DB_USER=${POSTGRES_USER:-filebatch}

mkdir -p "$OUT_DIR"
TARGET_FILE="$OUT_DIR/${DB_NAME}_${TS}.dump"

if [ -z "${POSTGRES_PASSWORD:-}" ]; then
  echo "POSTGRES_PASSWORD is required" >&2
  exit 1
fi

export PGPASSWORD="$POSTGRES_PASSWORD"
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -Fc -f "$TARGET_FILE"
unset PGPASSWORD

echo "Backup created: $TARGET_FILE"
