#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8011}
TIMEOUT=${TIMEOUT:-30}

check() {
  local name=$1
  local cmd=$2
  echo "[SMOKE] $name"
  eval "$cmd"
}

check "health endpoint" "curl -fsS --max-time $TIMEOUT $BASE_URL/actuator/health >/dev/null"
check "prometheus endpoint" "curl -fsS --max-time $TIMEOUT $BASE_URL/actuator/prometheus | grep -q 'batch_'"
check "prometheus includes scheduler metrics" "curl -fsS --max-time $TIMEOUT $BASE_URL/actuator/prometheus | grep -q 'scheduler_enqueue_total'"
check "prometheus includes distribution metrics" "curl -fsS --max-time $TIMEOUT $BASE_URL/actuator/prometheus | grep -q 'distribution_task_total'"
check "prometheus includes import quality gate metrics" "curl -fsS --max-time $TIMEOUT $BASE_URL/actuator/prometheus | grep -q 'import_parse_error_gate_failed_total'"

if command -v psql >/dev/null 2>&1; then
  DB_HOST=${POSTGRES_HOST:-localhost}
  DB_PORT=${POSTGRES_PORT:-5432}
  DB_NAME=${POSTGRES_DB:-postgres}
  DB_USER=${POSTGRES_USER:-postgres}
  export PGPASSWORD=${POSTGRES_PASSWORD:-postgres}
  check "db connectivity" "psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c 'select 1;' >/dev/null"
  unset PGPASSWORD
else
  echo "[SMOKE] skip db connectivity (psql not found)"
fi

echo "[SMOKE] PASS"
