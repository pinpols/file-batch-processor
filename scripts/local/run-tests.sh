#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# shellcheck source=../lib/env-common.sh
source "${ROOT_DIR}/scripts/lib/env-common.sh"
# shellcheck source=../lib/logging.sh
source "${ROOT_DIR}/scripts/lib/logging.sh"

MODE="${1:---default}"
shift || true

cd "$ROOT_DIR"

case "$MODE" in
  --default)
    bfp_info "运行默认单元测试"
    ./mvnw -B -ntp test "$@"
    ;;
  --unit)
    bfp_info "运行单元测试"
    ./mvnw -B -ntp test -Punit-test "$@"
    ;;
  --it)
    bfp_info "运行集成测试"
    ./mvnw -B -ntp test -Pintegration-test "$@"
    ;;
  --e2e)
    bfp_info "运行 E2E 测试"
    ./mvnw -B -ntp test -Pe2e-test "$@"
    ;;
  --all)
    bfp_info "运行全量测试"
    ./mvnw -B -ntp test -Pfull-test "$@"
    ;;
  --build-only)
    bfp_info "只构建应用"
    ./mvnw -B -ntp -DskipTests package "$@"
    ;;
  --help|-h)
    cat <<'EOF'
用法：
  bash scripts/local/run-tests.sh [--default|--unit|--it|--e2e|--all|--build-only] [extra maven args...]
EOF
    ;;
  *)
    bfp_error "未知模式: ${MODE}"
    exit 2
    ;;
esac
