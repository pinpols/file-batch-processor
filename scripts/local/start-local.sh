#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# shellcheck source=../lib/env-common.sh
source "${PROJECT_DIR}/scripts/lib/env-common.sh"
# shellcheck source=../lib/logging.sh
source "${PROJECT_DIR}/scripts/lib/logging.sh"

BUILD_APP="${BUILD_APP:-true}"
APP_BACKGROUND="${APP_BACKGROUND:-false}"
APP_LOG_DIR="${APP_LOG_DIR:-${PROJECT_DIR}/logs/current/app}"
APP_PID_FILE="${APP_PID_FILE:-${PROJECT_DIR}/logs/pids/file-batch-processor.pid}"

mkdir -p "${APP_LOG_DIR}" "$(dirname "${APP_PID_FILE}")"

check_port() {
  local port="$1"
  local service="$2"
  local pids
  pids="$(lsof -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    bfp_warn "端口 ${port} 已被 ${service} 占用，停止旧进程: ${pids}"
    printf '%s\n' "$pids" | xargs kill -15 2>/dev/null || true
    sleep 2
    pids="$(lsof -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      bfp_warn "旧进程未退出，强制停止: ${pids}"
      printf '%s\n' "$pids" | xargs kill -9 2>/dev/null || true
    fi
  fi
}

wait_for_app() {
  local deadline=$((SECONDS + 90))
  local app_pid="${1:-}"
  until curl -fsS --max-time 5 "${BASE_URL}/actuator/health" >/dev/null 2>&1; do
    if [[ -n "$app_pid" ]] && ! kill -0 "$app_pid" 2>/dev/null; then
      bfp_error "后端进程已退出，日志: ${APP_LOG_DIR}/app.log"
      return 1
    fi
    if (( SECONDS >= deadline )); then
      bfp_error "后端启动超时，日志: ${APP_LOG_DIR}/app.log"
      return 1
    fi
    sleep 2
  done
}

cd "${PROJECT_DIR}"

check_port "${APP_PORT}" "file-batch-processor"

if [[ "${BUILD_APP}" == "true" ]]; then
  bfp_info "构建应用 jar"
  ./mvnw -DskipTests package
fi

APP_JAR="${APP_JAR:-$(ls -1 target/*.jar 2>/dev/null | grep -v 'original' | head -n 1 || true)}"
if [[ -z "${APP_JAR}" || ! -f "${APP_JAR}" ]]; then
  bfp_error "target 下未找到应用 jar，请先执行 ./mvnw -DskipTests package"
  exit 1
fi

bfp_info "启动应用: ${APP_JAR}"
export SPRING_PROFILES_ACTIVE
export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
export BATCH_IO_INPUT_BASE_DIR BATCH_IO_OUTPUT_BASE_DIR
export OPS_SECURITY_ENABLED OPS_VIEWER_USERNAME OPS_VIEWER_PASSWORD OPS_OPERATOR_USERNAME OPS_OPERATOR_PASSWORD OPS_ADMIN_USERNAME OPS_ADMIN_PASSWORD

if [[ "${APP_BACKGROUND}" == "true" ]]; then
  nohup java ${APP_JAVA_OPTS:-} -jar "${APP_JAR}" >"${APP_LOG_DIR}/app.log" 2>&1 </dev/null &
  app_pid="$!"
  disown "$app_pid" 2>/dev/null || true
  printf '%s\n' "$app_pid" >"${APP_PID_FILE}"
  bfp_info "应用后台启动 pid=${app_pid} log=${APP_LOG_DIR}/app.log"
  wait_for_app "$app_pid"
  bfp_info "应用健康检查通过: ${BASE_URL}/actuator/health"
else
  exec java ${APP_JAVA_OPTS:-} -jar "${APP_JAR}"
fi
