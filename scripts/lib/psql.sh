#!/usr/bin/env bash
# BFP PostgreSQL 执行工具。优先使用宿主机 psql；缺失时回退到本地 Docker Postgres 容器。

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "scripts/lib/psql.sh must be sourced, not executed" >&2
  exit 2
fi

bfp_psql_mode() {
  if command -v "${PSQL_BIN:-psql}" >/dev/null 2>&1; then
    printf 'local'
    return 0
  fi
  if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
    printf 'docker'
    return 0
  fi
  return 1
}

bfp_psql() {
  local mode
  mode="$(bfp_psql_mode)" || {
    echo "psql not found and Docker container ${POSTGRES_CONTAINER} is not running" >&2
    return 127
  }

  if [[ "$mode" == "local" ]]; then
    PGPASSWORD="$POSTGRES_PASSWORD" "${PSQL_BIN:-psql}" \
      -h "$POSTGRES_HOST" \
      -p "$POSTGRES_PORT" \
      -U "$POSTGRES_USER" \
      -d "$POSTGRES_DB" \
      -v ON_ERROR_STOP=1 \
      "$@"
  else
    docker exec -i \
      -e PGPASSWORD="$POSTGRES_PASSWORD" \
      "$POSTGRES_CONTAINER" \
      psql \
      -U "$POSTGRES_USER" \
      -d "$POSTGRES_DB" \
      -v ON_ERROR_STOP=1 \
      "$@"
  fi
}

bfp_psql_file() {
  local sql_file="$1"
  if [[ ! -f "$sql_file" ]]; then
    echo "SQL file not found: $sql_file" >&2
    return 2
  fi
  bfp_psql < "$sql_file"
}

bfp_psql_check() {
  bfp_psql -c "select 1;" >/dev/null
}
