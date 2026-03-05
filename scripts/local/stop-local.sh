#!/usr/bin/env bash
set -euo pipefail

APP_PORT="${APP_PORT:-8011}"

kill_port() {
  local port="$1"
  local name="$2"
  local pids
  pids=$(lsof -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null || true)
  if [ -n "${pids}" ]; then
    echo "[INFO] stopping ${name} on port ${port}: ${pids}"
    echo "${pids}" | xargs kill -15 2>/dev/null || true
    sleep 1
    pids=$(lsof -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null || true)
    if [ -n "${pids}" ]; then
      echo "[WARN] force killing ${name} on port ${port}: ${pids}"
      echo "${pids}" | xargs kill -9 2>/dev/null || true
    fi
  else
    echo "[INFO] ${name} not listening on port ${port}"
  fi
}

kill_match() {
  local pattern="$1"
  local name="$2"
  local pids
  pids=$(pgrep -f "${pattern}" || true)
  if [ -n "${pids}" ]; then
    echo "[INFO] stopping ${name} by pattern '${pattern}': ${pids}"
    echo "${pids}" | xargs kill -15 2>/dev/null || true
    sleep 1
    pids=$(pgrep -f "${pattern}" || true)
    if [ -n "${pids}" ]; then
      echo "[WARN] force killing ${name}: ${pids}"
      echo "${pids}" | xargs kill -9 2>/dev/null || true
    fi
  fi
}

kill_port "${APP_PORT}" "file-batch-processor app"
# 补充兜底：按常见命令行模式清理
kill_match "file-batch-processor.*\\.jar" "file-batch-processor app"

echo "[INFO] stop-local done"
