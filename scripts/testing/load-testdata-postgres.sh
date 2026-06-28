#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# shellcheck source=../lib/env-common.sh
source "${ROOT_DIR}/scripts/lib/env-common.sh"
# shellcheck source=../lib/logging.sh
source "${ROOT_DIR}/scripts/lib/logging.sh"
# shellcheck source=../lib/psql.sh
source "${ROOT_DIR}/scripts/lib/psql.sh"

SEED_FILES_DEFAULT=(
  "seed_imported_records.sql"
  "seed_trace_and_dlq.sql"
  "seed_reconcile_mismatch.sql"
)

if [[ $# -gt 0 ]]; then
  SEED_FILES_ARR=("$@")
elif [[ -n "${SEED_FILES:-}" ]]; then
  IFS=',' read -r -a SEED_FILES_ARR <<< "${SEED_FILES}"
else
  SEED_FILES_ARR=("${SEED_FILES_DEFAULT[@]}")
fi

bfp_info "检查 PostgreSQL 连接: ${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
bfp_psql_check

for file in "${SEED_FILES_ARR[@]}"; do
  sql_file="${file}"
  if [[ "$sql_file" != /* ]]; then
    sql_file="${SCRIPT_DIR}/${sql_file}"
  fi
  bfp_info "加载测试 SQL: ${sql_file}"
  bfp_psql_file "${sql_file}" >/dev/null
done

bfp_info "测试数据加载完成"
