#!/usr/bin/env bash
# BFP 本地脚本统一环境入口。只能被 source，不要直接执行。

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "scripts/lib/env-common.sh must be sourced, not executed" >&2
  exit 2
fi

if [[ -z "${BFP_ROOT_DIR:-}" ]]; then
  BFP_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

bfp_source_env_file() {
  local env_file="${1:-${BFP_ENV_FILE:-$BFP_ROOT_DIR/.env.local}}"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

bfp_load_default_env() {
  if [[ -n "${BFP_ENV_LOADED:-}" ]]; then
    return 0
  fi

  BFP_ENV_FILE="${BFP_ENV_FILE:-$BFP_ROOT_DIR/.env.local}"
  if [[ "$BFP_ENV_FILE" != "$BFP_ROOT_DIR/.env" && -f "$BFP_ROOT_DIR/.env" ]]; then
    bfp_source_env_file "$BFP_ROOT_DIR/.env"
  fi
  bfp_source_env_file "$BFP_ENV_FILE"

  export BFP_TIMEZONE="${BFP_TIMEZONE:-Asia/Shanghai}"
  export TZ="${TZ:-$BFP_TIMEZONE}"
  export LANG="${LANG:-C.UTF-8}"
  export LC_ALL="${LC_ALL:-C.UTF-8}"

  export APP_PORT="${APP_PORT:-8011}"
  export BASE_URL="${BASE_URL:-http://localhost:${APP_PORT}}"
  export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

  export POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
  export POSTGRES_PORT="${POSTGRES_PORT:-5432}"
  export POSTGRES_DB="${POSTGRES_DB:-file_batch}"
  export POSTGRES_USER="${POSTGRES_USER:-postgres}"
  export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
  export POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-file-batch-postgres-dev}"

  export PGHOST="${PGHOST:-$POSTGRES_HOST}"
  export PGPORT="${PGPORT:-$POSTGRES_PORT}"
  export PGDATABASE="${PGDATABASE:-$POSTGRES_DB}"
  export PGUSER="${PGUSER:-$POSTGRES_USER}"
  export PGPASSWORD="${PGPASSWORD:-$POSTGRES_PASSWORD}"

  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$POSTGRES_USER}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$POSTGRES_PASSWORD}"

  export BATCH_IO_INPUT_BASE_DIR="${BATCH_IO_INPUT_BASE_DIR:-$BFP_ROOT_DIR/scripts/testing}"
  export BATCH_IO_OUTPUT_BASE_DIR="${BATCH_IO_OUTPUT_BASE_DIR:-$BFP_ROOT_DIR/logs/runs/scenarios}"

  export OPS_SECURITY_ENABLED="${OPS_SECURITY_ENABLED:-true}"
  export OPS_VIEWER_USERNAME="${OPS_VIEWER_USERNAME:-viewer}"
  local default_viewer_password="{noop}change_me_viewer"
  export OPS_VIEWER_PASSWORD="${OPS_VIEWER_PASSWORD:-$default_viewer_password}"
  export OPS_OPERATOR_USERNAME="${OPS_OPERATOR_USERNAME:-operator}"
  local default_operator_password="{noop}change_me_operator"
  export OPS_OPERATOR_PASSWORD="${OPS_OPERATOR_PASSWORD:-$default_operator_password}"
  export OPS_ADMIN_USERNAME="${OPS_ADMIN_USERNAME:-admin}"
  local default_admin_password="{noop}change_me_admin"
  export OPS_ADMIN_PASSWORD="${OPS_ADMIN_PASSWORD:-$default_admin_password}"
  export OPS_AUTH_USER="${OPS_AUTH_USER:-$OPS_ADMIN_USERNAME}"
  local default_ops_auth_password="$OPS_ADMIN_PASSWORD"
  default_ops_auth_password="${default_ops_auth_password#\{noop\}}"
  export OPS_AUTH_PASSWORD="${OPS_AUTH_PASSWORD:-$default_ops_auth_password}"

  export BFP_ENV_LOADED=1
}

bfp_load_default_env
