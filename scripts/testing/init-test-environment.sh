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

MODE="${1:-basic}"

case "$MODE" in
  basic|--basic) MODE="basic" ;;
  enhanced|--enhanced) MODE="enhanced" ;;
  all|--all) MODE="all" ;;
  --help|-h)
    cat <<'EOF'
用法：
  bash scripts/testing/init-test-environment.sh [basic|enhanced|all]

说明：
  basic     清理本地验证数据后加载基础导入、trace、DLQ、对账样例
  enhanced  清理本地验证数据后加载综合业务样例
  all       清理本地验证数据后加载全部样例
EOF
    exit 0
    ;;
  *)
    bfp_error "未知模式: ${MODE}"
    exit 2
    ;;
esac

bfp_info "初始化本地测试环境: mode=${MODE}, db=${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
bfp_psql_check

bfp_info "清理本地验证数据"
bfp_psql_file "${SCRIPT_DIR}/sql/cleanup-local-testdata.sql" >/dev/null

case "$MODE" in
  basic)
    "${SCRIPT_DIR}/load-testdata-postgres.sh" \
      "${SCRIPT_DIR}/seed_imported_records.sql" \
      "${SCRIPT_DIR}/seed_trace_and_dlq.sql" \
      "${SCRIPT_DIR}/seed_reconcile_mismatch.sql"
    ;;
  enhanced)
    "${SCRIPT_DIR}/load-testdata-postgres.sh" "${SCRIPT_DIR}/seed_enhanced_test_data.sql"
    ;;
  all)
    "${SCRIPT_DIR}/load-all-testdata.sh"
    ;;
esac

bfp_info "验证本地测试数据"
bfp_psql_file "${SCRIPT_DIR}/sql/verify-local-testdata.sql"
bfp_info "本地测试环境初始化完成"
