#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/load-testdata-postgres.sh" \
  "${SCRIPT_DIR}/seed_imported_records.sql" \
  "${SCRIPT_DIR}/seed_trace_and_dlq.sql" \
  "${SCRIPT_DIR}/seed_reconcile_mismatch.sql" \
  "${SCRIPT_DIR}/seed_enhanced_test_data.sql"
