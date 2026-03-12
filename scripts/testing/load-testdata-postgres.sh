#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-file_batch}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
PSQL_BIN="${PSQL_BIN:-psql}"

SEED_FILES_DEFAULT=(
  "seed_imported_records.sql"
  "seed_trace_and_dlq.sql"
  "seed_reconcile_mismatch.sql"
)

if ! command -v "${PSQL_BIN}" >/dev/null 2>&1; then
  echo "[ERROR] psql not found. Install PostgreSQL client first." >&2
  exit 1
fi

if [[ "${SEED_FILES:-}" != "" ]]; then
  IFS=',' read -r -a SEED_FILES_ARR <<< "${SEED_FILES}"
else
  SEED_FILES_ARR=("${SEED_FILES_DEFAULT[@]}")
fi

export PGPASSWORD="${POSTGRES_PASSWORD}"

echo "[INFO] checking PostgreSQL connectivity: ${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
"${PSQL_BIN}" -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -c "SELECT 1;" >/dev/null

for file in "${SEED_FILES_ARR[@]}"; do
  sql_file="${SCRIPT_DIR}/${file}"
  if [[ ! -f "${sql_file}" ]]; then
    echo "[ERROR] seed file not found: ${sql_file}" >&2
    exit 1
  fi
  echo "[INFO] applying ${file}"
  "${PSQL_BIN}" -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -f "${sql_file}" >/dev/null
done

unset PGPASSWORD
echo "[OK] PostgreSQL test data imported."
