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

TIMEOUT="${TIMEOUT:-30}"

curl_get() {
  local path="$1"
  curl -fsS --max-time "$TIMEOUT" -u "${OPS_AUTH_USER}:${OPS_AUTH_PASSWORD}" "${BASE_URL}${path}"
}

check() {
  local name="$1"
  shift
  bfp_info "SMOKE: ${name}"
  "$@"
}

check "health endpoint" curl -fsS --max-time "$TIMEOUT" "${BASE_URL}/actuator/health" >/dev/null
check "prometheus endpoint" curl_get "/actuator/prometheus" >/dev/null
check "scheduler snapshot" curl_get "/ops/scheduler" >/dev/null
check "ops dashboard" curl_get "/ops/dashboard" >/dev/null
check "quality gates api" curl_get "/api/quality/gates" >/dev/null
check "trace api" curl_get "/api/trace/Alice:2026-03-01" >/dev/null
check "reconcile runs api" curl_get "/api/reconcile/runs" >/dev/null
check "db connectivity" bfp_psql_check

bfp_info "SMOKE PASS"
