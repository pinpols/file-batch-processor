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

RUN_DIR="$(bfp_run_dir scenarios local-scenarios)"
INPUT_FILE="${INPUT_FILE:-${SCRIPT_DIR}/large_dataset_financial.csv}"
OUTPUT_FILE="${OUTPUT_FILE:-${RUN_DIR}/data-export-20260301.csv}"
TIMEOUT="${TIMEOUT:-30}"

curl_get() {
  local path="$1"
  curl -fsS --max-time "$TIMEOUT" -u "${OPS_AUTH_USER}:${OPS_AUTH_PASSWORD}" "${BASE_URL}${path}"
}

curl_post() {
  local path="$1"
  local body="${2:-}"
  if [[ -n "$body" ]]; then
    curl -fsS --max-time "$TIMEOUT" -u "${OPS_AUTH_USER}:${OPS_AUTH_PASSWORD}" \
      -H 'Content-Type: application/json' \
      -d "$body" \
      "${BASE_URL}${path}"
  else
    curl -fsS --max-time "$TIMEOUT" -u "${OPS_AUTH_USER}:${OPS_AUTH_PASSWORD}" \
      -X POST \
      "${BASE_URL}${path}"
  fi
}

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    bfp_error "测试文件不存在: ${file}"
    exit 2
  fi
}

wait_for_app() {
  local deadline=$((SECONDS + 90))
  until curl -fsS --max-time 5 "${BASE_URL}/actuator/health" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      bfp_error "后端健康检查超时: ${BASE_URL}/actuator/health"
      exit 1
    fi
    sleep 2
  done
}

count_sql() {
  local sql="$1"
  bfp_psql -At -c "$sql" | tr -d '[:space:]'
}

latest_task_status() {
  local task_id="$1"
  bfp_psql -At -c "select coalesce((select status from task_execution_state where task_id = '${task_id}' and batch_date = '2026-03-01' order by updated_at desc limit 1), '');" \
    | tr -d '[:space:]'
}

dump_task_state() {
  local task_id="$1"
  bfp_psql -c "select task_id, batch_date, status, attempt, last_error, error_code, updated_at from task_execution_state where task_id = '${task_id}' order by updated_at desc limit 5;"
}

wait_for_task_success() {
  local task_id="$1"
  local label="$2"
  local deadline=$((SECONDS + 90))
  local status=""
  while true; do
    status="$(latest_task_status "$task_id")"
    case "$status" in
      SUCCESS)
        bfp_info "${label} 成功: task_id=${task_id}"
        return 0
        ;;
      FAILED|ABANDONED|CANCELLED|TIMEOUT|REJECTED)
        bfp_error "${label} 失败: task_id=${task_id}, status=${status}"
        dump_task_state "$task_id" >&2
        exit 1
        ;;
    esac

    if (( SECONDS >= deadline )); then
      bfp_error "${label} 等待超时: task_id=${task_id}, last_status=${status:-<empty>}"
      dump_task_state "$task_id" >&2
      exit 1
    fi
    sleep 2
  done
}

bfp_info "本地全场景验证开始，日志目录: ${RUN_DIR}"
require_file "$INPUT_FILE"
mkdir -p "$(dirname "$OUTPUT_FILE")"

bfp_info "检查数据库连接"
bfp_psql_check

bfp_info "加载基础真实数据"
"${SCRIPT_DIR}/init-test-environment.sh" all >"${RUN_DIR}/01-init-testdata.log" 2>&1

bfp_info "写入本地运行态任务参数"
bfp_psql \
  -v "input_file=${INPUT_FILE}" \
  -v "output_file=${OUTPUT_FILE}" \
  < "${SCRIPT_DIR}/sql/upsert-local-runtime-tasks.sql" \
  >"${RUN_DIR}/02-upsert-runtime-tasks.log"

bfp_info "等待后端健康"
wait_for_app

bfp_info "刷新调度器任务配置"
curl_post "/ops/scheduler/reload" "" >"${RUN_DIR}/03-reload-scheduler.json"

bfp_info "执行接口冒烟"
"${SCRIPT_DIR}/smoke-test.sh" >"${RUN_DIR}/04-smoke.log" 2>&1

bfp_info "触发导入任务"
curl_post "/ops/scheduler/trigger/process-file-main" "" >"${RUN_DIR}/05-trigger-import.json"
wait_for_task_success "process-file-main" "导入任务"

bfp_info "触发导出任务"
curl_post "/ops/scheduler/trigger/data-export-main" "" >"${RUN_DIR}/06-trigger-export.json"
wait_for_task_success "data-export-main" "导出任务"

if [[ ! -s "$OUTPUT_FILE" ]]; then
  bfp_error "导出文件不存在或为空: ${OUTPUT_FILE}"
  exit 1
fi

bfp_info "读取运维和业务 API"
curl_get "/ops/dashboard" >"${RUN_DIR}/07-dashboard.json"
curl_get "/api/trace/Alice:2026-03-01" >"${RUN_DIR}/08-trace.json"
curl_get "/api/quality/gates" >"${RUN_DIR}/09-quality-gates.json"
curl_get "/api/reconcile/runs" >"${RUN_DIR}/10-reconcile-runs.json"

bfp_info "执行数据库断言"
imported_count="$(count_sql "select count(*) from imported_records where batch_date = '2026-03-01';")"
partition_count="$(count_sql "select count(*) from imported_records_partition where batch_date = '2026-03-01';")"
dlq_count="$(count_sql "select count(*) from dlq_records where handled = false;")"

{
  printf 'imported_records_20260301=%s\n' "$imported_count"
  printf 'partitioned_records_20260301=%s\n' "$partition_count"
  printf 'dlq_backlog=%s\n' "$dlq_count"
  printf 'output_file=%s\n' "$OUTPUT_FILE"
} | tee "${RUN_DIR}/11-summary.txt"

if (( imported_count < 2 )); then
  bfp_error "导入表数据不足: ${imported_count}"
  exit 1
fi
if (( partition_count < 1 )); then
  bfp_error "分区导入表数据不足: ${partition_count}"
  exit 1
fi

bfp_info "本地全场景验证完成"
