#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# shellcheck source=../lib/env-common.sh
source "${PROJECT_DIR}/scripts/lib/env-common.sh"
# shellcheck source=../lib/logging.sh
source "${PROJECT_DIR}/scripts/lib/logging.sh"

APP_PID_FILE="${APP_PID_FILE:-${PROJECT_DIR}/logs/pids/file-batch-processor.pid}"

kill_pids() {
  local name="$1"
  local pids="$2"
  if [[ -z "$pids" ]]; then
    return 0
  fi
  bfp_info "停止 ${name}: ${pids}"
  printf '%s\n' "$pids" | xargs kill -15 2>/dev/null || true
  sleep 2
  local still_running=""
  while read -r pid; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      still_running="${still_running}${pid}"$'\n'
    fi
  done <<< "$pids"
  if [[ -n "$still_running" ]]; then
    bfp_warn "强制停止 ${name}: ${still_running//$'\n'/ }"
    printf '%s\n' "$still_running" | xargs kill -9 2>/dev/null || true
  fi
}

if [[ -f "$APP_PID_FILE" ]]; then
  kill_pids "file-batch-processor pid 文件进程" "$(cat "$APP_PID_FILE")"
  rm -f "$APP_PID_FILE"
fi

port_pids="$(lsof -iTCP:"${APP_PORT}" -sTCP:LISTEN -t 2>/dev/null || true)"
kill_pids "file-batch-processor 端口 ${APP_PORT}" "$port_pids"

jar_pids="$(pgrep -f "file-batch-processor.*\\.jar" || true)"
kill_pids "file-batch-processor jar" "$jar_pids"

bfp_info "stop-local 完成"
