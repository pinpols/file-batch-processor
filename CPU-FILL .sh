#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# CPU-FILL v8.4 (WITH GUARDIAN)
# 功能：
#   - 将系统 CPU 占用提升到指定百分比（TARGET_CPU_PERCENT）
#   - 支持 NUMA 节点选择和 CPU 排除
#   - 使用动态控制器调节 worker 占用
#   - 守护清理机制：保证超时或异常时清理所有 worker
#   - 普通用户即可运行，无需 root
###############################################################################

TARGET_CPU_PERCENT=${TARGET_CPU_PERCENT:-50}    # 目标系统 CPU 使用率百分比
DURATION=${DURATION:-60}                        # 总运行时间，单位秒
WORKERS=${WORKERS:-0}                           # worker 数量，0=自动使用节点全部可用 CPU
BUSY_NODE=${BUSY_NODE:-auto}                    # NUMA 节点选择，auto=自动
AUTO_NODE_STRATEGY=${AUTO_NODE_STRATEGY:-least-load}  # 自动选择节点策略
EXCLUDE_CPUS=${EXCLUDE_CPUS:-""}               # 排除 CPU 列表
LOG_PREFIX="[CPU-FILL]"
LOG_LEVEL=${LOG_LEVEL:-INFO}                    # 日志级别：DEBUG, INFO, WARN, ERROR

# 动态控制参数
ADJUST_INTERVAL=${ADJUST_INTERVAL:-5}          # Controller 调节间隔，秒
SAMPLE_INTERVAL=${SAMPLE_INTERVAL:-1}          # CPU 采样间隔，秒
TOLERANCE=${TOLERANCE:-2}                      # CPU 占用误差容忍百分比

# 全局变量
CHILD_PIDS=()      # 存储 worker 进程 PID
STOP_FILE=""       # 停止信号文件，worker 根据此文件停止
CONTROL_FILE=""    # 动态控制文件，用于调整 worker 占用率
CLEANUP_DONE=0
START_TIME=$(date +%s)

########################################
# 日志函数
########################################
log() {
  local level=$1
  shift
  local timestamp
  timestamp=$(date '+%Y-%m-%d %H:%M:%S')
  case "$LOG_LEVEL" in
    DEBUG) ;;
    INFO)  [[ "$level" == "DEBUG" ]] && return ;;
    WARN)  [[ "$level" =~ ^(DEBUG|INFO)$ ]] && return ;;
    ERROR) [[ "$level" != "ERROR" ]] && return ;;
  esac
  echo "[$timestamp] [$level] $LOG_PREFIX $*" >&2
}

########################################
# 参数验证
########################################
validate_params() {
  local errors=0
  [[ ! "$TARGET_CPU_PERCENT" =~ ^[0-9]+$ ]] || [ "$TARGET_CPU_PERCENT" -lt 1 ] || [ "$TARGET_CPU_PERCENT" -gt 100 ] && \
    { log ERROR "Invalid TARGET_CPU_PERCENT: $TARGET_CPU_PERCENT"; ((errors++)); }
  [[ ! "$DURATION" =~ ^[0-9]+$ ]] || [ "$DURATION" -lt 1 ] && { log ERROR "Invalid DURATION: $DURATION"; ((errors++)); }
  [[ ! "$WORKERS" =~ ^[0-9]+$ ]] && { log ERROR "Invalid WORKERS: $WORKERS"; ((errors++)); }
  [ "$errors" -gt 0 ] && exit 1
}

########################################
# 清理函数
#   - 停止所有 worker
#   - 删除临时文件
#   - 捕获 SIGINT/SIGTERM/SIGHUP/EXIT
########################################
cleanup() {
  [ "$CLEANUP_DONE" -eq 1 ] && return
  CLEANUP_DONE=1
  log INFO "Initiating cleanup..."
  [ -n "$STOP_FILE" ] && touch "${STOP_FILE}.stop" 2>/dev/null || true
  if [ "${#CHILD_PIDS[@]}" -gt 0 ]; then
    for pid in "${CHILD_PIDS[@]}"; do kill -TERM "$pid" 2>/dev/null || true; done
    sleep 1
    for pid in "${CHILD_PIDS[@]}"; do kill -KILL "$pid" 2>/dev/null || true; done
  fi
  [ -n "$STOP_FILE" ] && rm -f "$STOP_FILE" "${STOP_FILE}.control" 2>/dev/null || true
  local runtime=$(($(date +%s) - START_TIME))
  log INFO "Cleanup complete (runtime: ${runtime}s)"
  exit 0
}
trap cleanup SIGINT SIGTERM SIGHUP EXIT

########################################
# CPU 列表展开
########################################
expand_cpulist() {
  local list=$1
  local out=()
  [ -z "$list" ] && return
  IFS=',' read -ra parts <<< "$list"
  for p in "${parts[@]}"; do
    [ -z "$p" ] && continue
    if [[ "$p" == *-* ]]; then
      IFS='-' read -r s e <<< "$p"
      [[ "$s" =~ ^[0-9]+$ ]] && [[ -n "$e" ]] && [[ "$e" =~ ^[0-9]+$ ]] && [ "$s" -le "$e" ] && \
        for ((i=s;i<=e;i++)); do out+=("$i"); done
    elif [[ "$p" =~ ^[0-9]+$ ]]; then
      out+=("$p")
    fi
  done
  echo "${out[@]}"
}

########################################
# 排除指定 CPU
########################################
exclude_cpus() {
  local src="$1"
  local ex="$2"
  [ -z "$ex" ] && { echo "$src"; return; }
  local src_arr ex_arr result=()
  read -ra src_arr <<< "$(expand_cpulist "$src")"
  read -ra ex_arr <<< "$(expand_cpulist "$ex")"
  for c in "${src_arr[@]}"; do
    local skip=0
    for e in "${ex_arr[@]}"; do [ "$c" = "$e" ] && skip=1 && break; done
    [ "$skip" -eq 0 ] && result+=("$c")
  done
  (IFS=','; echo "${result[*]}")
}

########################################
# 读取 /proc/stat 获取 CPU 使用情况
########################################
read_cpu_stat() {
  awk '
    /^cpu[0-9]+ /{
      c=substr($1,4)
      u[c]=$2+$4
      t[c]=$2+$4+$5
    }
    END{
      for (i in u) print i,u[i],t[i]
    }
  ' /proc/stat
}

########################################
# 计算系统占用率
########################################
calc_system_usage() {
  local sample_interval=${1:-1}; shift
  local cpus=("$@")
  [ "${#cpus[@]}" -eq 0 ] && { log ERROR "calc_system_usage called with no CPUs"; echo "0"; return 1; }
  declare -A u1 t1 u2 t2
  while read -r c u t; do u1[$c]=${u:-0}; t1[$c]=${t:-0}; done < <(read_cpu_stat)
  sleep "$sample_interval"
  while read -r c u t; do u2[$c]=${u:-0}; t2[$c]=${t:-0}; done < <(read_cpu_stat)
  local su=0 st=0
  for c in "${cpus[@]}"; do
    local du=$(( ${u2[$c]:-0} - ${u1[$c]:-0} ))
    local dt=$(( ${t2[$c]:-0} - ${t1[$c]:-0} ))
    su=$((su+du)); st=$((st+dt))
  done
  awk "BEGIN { printf \"%.2f\", ($st==0)?0:($su*100/$st) }"
}

########################################
# 检测 NUMA 节点
########################################
detect_numa_node() {
  [ "$BUSY_NODE" != "auto" ] && { echo "$BUSY_NODE"; return; }
  [ ! -d /sys/devices/system/node ] && { echo 0; return; }
  if [ "$AUTO_NODE_STRATEGY" = "least-load" ]; then
    local best_node="" best_load=999
    for n in /sys/devices/system/node/node*; do
      [ ! -d "$n" ] && continue
      local nid=${n##*node}
      local cpulist=$(exclude_cpus "$(cat "$n/cpulist" 2>/dev/null || echo "")" "$EXCLUDE_CPUS")
      [ -z "$cpulist" ] && continue
      read -ra cpus <<< "$(expand_cpulist "$cpulist")"
      [ "${#cpus[@]}" -eq 0 ] && continue
      local load=$(calc_system_usage 0.5 "${cpus[@]}")
      log DEBUG "Node $nid CPU usage: ${load}%"
      if awk "BEGIN { exit !($load < $best_load) }"; then best_load=$load; best_node=$nid; fi
    done
    [ -n "$best_node" ] && { echo "$best_node"; return; }
  fi
  local max_node
  max_node=$(ls -d /sys/devices/system/node/node* 2>/dev/null | sed 's/.*node//' | sort -n | tail -1)
  echo "${max_node:-0}"
}

########################################
# Worker 代码
########################################
get_worker_code() {
cat <<'WORKER_EOF'
worker_main() {
  local initial_percent=$1 duration=$2 stop_file=$3 control_file=$4
  local current_percent=$initial_percent slice_us=10000 end_time=$((SECONDS + duration)) last_check=0
  local use_nanosec=1 test_output=$(date +%s%N 2>/dev/null || echo "FAIL")
  [[ "$test_output" =~ N ]] || [[ "$test_output" == "FAIL" ]] && use_nanosec=0

  while [ "$SECONDS" -lt "$end_time" ] && [ ! -f "${stop_file}.stop" ]; do
    if [ $((SECONDS - last_check)) -ge 2 ] && [ -f "$control_file" ]; then
      local new_percent=$(cat "$control_file" 2>/dev/null || echo "")
      [[ "$new_percent" =~ ^[0-9]+$ ]] && [ "$new_percent" -ge 0 ] && [ "$new_percent" -le 100 ] && current_percent=$new_percent
      last_check=$SECONDS
    fi
    local busy_us=$((slice_us * current_percent / 100))
    local idle_us=$((slice_us - busy_us))

    if [ "$use_nanosec" -eq 1 ] && [ "$busy_us" -gt 0 ]; then
      local loop_start=$(date +%s%N)
      while :; do
        local loop_now=$(date +%s%N)
        [[ "$loop_now" =~ ^[0-9]+$ ]] && [[ "$loop_start" =~ ^[0-9]+$ ]] && \
          [ $(( (loop_now - loop_start)/1000 )) -gt "$busy_us" ] && break
      done
    else
      local count=$((busy_us > 0 ? busy_us * 10 : 100))
      for ((i=0;i<count;i++)); do :; done
    fi

    [ "$idle_us" -gt 0 ] && sleep $(awk -v us="$idle_us" 'BEGIN { printf "%.6f", us/1000000 }') 2>/dev/null || sleep 0.01
  done
}

worker_main "$@"
WORKER_EOF
}

########################################
# 守护清理机制
########################################
start_guardian() {
  local stop_file=$1
  local child_pids=("${!2}")
  local duration=$3
  (
    local end_time=$(( $(date +%s) + duration + 5 ))
    while [ "$(date +%s)" -lt "$end_time" ]; do
      sleep 1
      [ ! -f "$stop_file" ] && break
    done
    # 强制清理残留 worker
    for pid in "${child_pids[@]}"; do kill -0 "$pid" 2>/dev/null && kill -TERM "$pid" 2>/dev/null || true; done
    sleep 1
    for pid in "${child_pids[@]}"; do kill -0 "$pid" 2>/dev/null && kill -KILL "$pid" 2>/dev/null || true; done
    [ -f "$stop_file" ] && rm -f "$stop_file" "${stop_file}.control" 2>/dev/null || true
  ) &
}

########################################
# 动态 controller
########################################
dynamic_controller() {
  local target=$1 duration=$2 cpu_arr_str=$3 control_file=$4 stop_file=$5
  read -ra cpu_arr <<< "$cpu_arr_str"
  local cpu_count=${#cpu_arr[@]}
  [ "$cpu_count" -eq 0 ] && { log ERROR "Controller: no CPUs"; return 1; }
  local end_timestamp=$(($(date +%s) + duration))
  local current_worker_percent=$TARGET_CPU_PERCENT
  echo "$current_worker_percent" > "$control_file"
  log INFO "Controller started (target: ${target}%, CPUs: $cpu_count, workers: $WORKERS)"

  while [ "$(date +%s)" -lt "$end_timestamp" ] && [ ! -f "${stop_file}.stop" ]; do
    local loop_start=$(date +%s)
    local current_usage=$(calc_system_usage "$SAMPLE_INTERVAL" "${cpu_arr[@]}")
    [[ -z "$current_usage" ]] && sleep "$ADJUST_INTERVAL" && continue
    local diff=$(awk -v t="$target" -v c="$current_usage" 'BEGIN { printf "%.2f", t-c }')
    local abs_diff=$(awk -v d="$diff" 'BEGIN { printf "%.2f", (d<0)?-d:d }')
    if awk -v a="$abs_diff" -v t="$TOLERANCE" 'BEGIN { exit !(a>t) }'; then
      local adjustment=$(awk -v d="$diff" -v c="$cpu_count" -v w="$WORKERS" 'BEGIN { printf "%.0f", d*c/w }')
      local new_percent=$((current_worker_percent + adjustment))
      [ "$new_percent" -lt 0 ] && new_percent=0
      [ "$new_percent" -gt 100 ] && new_percent=100
      [ "$new_percent" -ne "$current_worker_percent" ] && echo "$new_percent" > "$control_file" && current_worker_percent=$new_percent
    fi
    local elapsed=$(($(date +%s)-loop_start))
    [ $((ADJUST_INTERVAL-elapsed)) -gt 0 ] && sleep $((ADJUST_INTERVAL-elapsed))
  done
  log INFO "Controller stopped"
}

########################################
# main
########################################
main() {
  log INFO "CPU-FILL v8.4 starting..."
  validate_params

  # 创建 stop/control 文件
  STOP_FILE=$(mktemp -t cpu-fill.XXXXXX)
  CONTROL_FILE="${STOP_FILE}.control"

  log INFO "Target: ${TARGET_CPU_PERCENT}% system CPU for ${DURATION}s"
  log DEBUG "Stop file: $STOP_FILE"
  log DEBUG "Control file: $CONTROL_FILE"

  # NUMA 节点
  local NODE=$(detect_numa_node)
  local node_cpulist
  if [ -f "/sys/devices/system/node/node${NODE}/cpulist" ]; then
    node_cpulist=$(cat "/sys/devices/system/node/node${NODE}/cpulist")
  else
    node_cpulist=$(cat /sys/devices/system/cpu/online 2>/dev/null || echo "0")
  fi
  local CPULIST=$(exclude_cpus "$node_cpulist" "$EXCLUDE_CPUS")
  log INFO "NUMA node: $NODE"
  log INFO "CPU list: $CPULIST"

  # CPU 展开
  local CPU_ARR
  read -ra CPU_ARR <<< "$(expand_cpulist "$CPULIST")"
  local CPU_COUNT=${#CPU_ARR[@]}
  [ "$CPU_COUNT" -eq 0 ] && { log ERROR "No CPU available"; exit 1; }

  # worker 数量
  [ "$WORKERS" -le 0 ] && WORKERS=$CPU_COUNT
  [ "$WORKERS" -le 0 ] && { log ERROR "Invalid worker count"; exit 1; }
  local INITIAL_PERCENT=$TARGET_CPU_PERCENT
  log INFO "Workers: $WORKERS (on $CPU_COUNT CPUs)"
  log INFO "Initial target: ${INITIAL_PERCENT}% per worker"

  # 启动 worker
  local worker_code=$(get_worker_code)
  for ((i=0;i<WORKERS;i++)); do
    local core="${CPU_ARR[$((i % CPU_COUNT))]}"
    taskset -c "$core" nice -n 19 bash -c "$worker_code" worker "$INITIAL_PERCENT" "$DURATION" "$STOP_FILE" "$CONTROL_FILE" &
    CHILD_PIDS+=($!)
    log DEBUG "Worker $((i+1))/$WORKERS on CPU $core (PID ${CHILD_PIDS[$i]})"
  done

  # 启动守护清理
  start_guardian "$STOP_FILE" CHILD_PIDS[@] "$DURATION"

  # 等待 worker 启动
  sleep 0.5
  local active=0
  for pid in "${CHILD_PIDS[@]}"; do kill -0 "$pid" 2>/dev/null && ((active++)) || true; done
  [ "$active" -eq 0 ] && { log ERROR "All workers failed"; exit 1; }
  [ "$active" -lt "$WORKERS" ] && log WARN "Only $active/$WORKERS workers running" || log INFO "All workers started"

  # 启动动态 controller
  dynamic_controller "$TARGET_CPU_PERCENT" "$DURATION" "${CPU_ARR[*]}" "$CONTROL_FILE" "$STOP_FILE" &
  local controller_pid=$!

  # 等待结束
  wait "$controller_pid" 2>/dev/null || true
  for pid in "${CHILD_PIDS[@]}"; do wait "$pid" 2>/dev/null || true; done

  local runtime=$(($(date +%s) - START_TIME))
  log INFO "Finished (runtime: ${runtime}s)"
}

main
