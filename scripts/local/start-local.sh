#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

APP_PORT="${APP_PORT:-8011}"
BUILD_APP="${BUILD_APP:-true}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
mkdir -p "${PROJECT_DIR}/logs"

check_port() {
  local port="$1"
  local service="$2"
  if lsof -iTCP:"${port}" -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "[WARN] port ${port} is occupied (${service}), stopping existing process"
    lsof -iTCP:"${port}" -sTCP:LISTEN -t | xargs kill -9 2>/dev/null || true
    sleep 1
  fi
}

cd "${PROJECT_DIR}"

check_port "${APP_PORT}" "file-batch-processor"

if [ "${BUILD_APP}" = "true" ]; then
  echo "[INFO] building application jar"
  ./mvnw -q -DskipTests package
fi

APP_JAR="${APP_JAR:-$(ls -1 target/*.jar 2>/dev/null | grep -v 'original' | head -n 1 || true)}"
if [ -z "${APP_JAR}" ] || [ ! -f "${APP_JAR}" ]; then
  echo "[ERROR] application jar not found in target/. Run: ./mvnw -DskipTests package"
  exit 1
fi

echo "[INFO] starting app jar: ${APP_JAR}"
export SPRING_PROFILES_ACTIVE
java ${APP_JAVA_OPTS:-} -jar "${APP_JAR}"
