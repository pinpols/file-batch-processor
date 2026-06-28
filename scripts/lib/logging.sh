#!/usr/bin/env bash
# BFP 本地脚本日志工具。只能被 source，不要直接执行。

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "scripts/lib/logging.sh must be sourced, not executed" >&2
  exit 2
fi

bfp_log_ts() {
  date '+%Y-%m-%d %H:%M:%S'
}

bfp_info() {
  printf '[%s] [INFO] %s\n' "$(bfp_log_ts)" "$*"
}

bfp_warn() {
  printf '[%s] [WARN] %s\n' "$(bfp_log_ts)" "$*" >&2
}

bfp_error() {
  printf '[%s] [ERROR] %s\n' "$(bfp_log_ts)" "$*" >&2
}

bfp_run_id() {
  local label="${1:-run}"
  local sha
  sha="$(git -C "$BFP_ROOT_DIR" rev-parse --short HEAD 2>/dev/null || printf 'nogit')"
  printf '%s-%s-%s' "$label" "$(date '+%Y%m%d-%H%M%S')" "$sha"
}

bfp_run_dir() {
  local kind="${1:-run}"
  local label="${2:-$kind}"
  local run_id="${BFP_RUN_ID:-$(bfp_run_id "$label")}"
  local dir="$BFP_ROOT_DIR/logs/runs/$kind/$run_id"
  mkdir -p "$dir"
  ln -sfn "$dir" "$BFP_ROOT_DIR/logs/runs/$kind/latest"
  printf '%s' "$dir"
}
