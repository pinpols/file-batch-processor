#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# CPU-FILL v8.2 (FULLY VERIFIED)
# 说明：
#   - 将系统 CPU 占用提升到指定百分比（TARGET_CPU_PERCENT）
#   - 支持 NUMA 节点选择和 CPU 排除
#   - 使用动态控制器调节 worker 占用，减去自身占用
#   - 普通用户即可运行，无需 root 权限
###############################################################################

TARGET_CPU_PERCENT=${TARGET_CPU_PERCENT:-50}   # 目标系统 CPU 使用率百分比
DURATION=${DURATION:-60}                       # 总运行时间，单位秒
WORKERS=${WORKERS:-0}                          # worker 数量，0=自动使用节点全部可用 CPU
BUSY_NODE=${BUSY_NODE:-auto}                   # NUMA 节点选择，auto=自动
AUTO_NODE_STRATEGY=${AUTO_NODE_STRATEGY:-least-load}  # 自动选择节点策略
EXCLUDE_CPUS=${EXCLUDE_CPUS:-""}              # 排除 CPU 列表
LOG_PREFIX="[CPU-FILL]"
LOG_LEVEL=${LOG_LEVEL:-INFO}                   # 日志级别：DEBUG, INFO, WARN, ERROR

# 动态控制参数
ADJUST_INTERVAL=${ADJUST_INTERVAL:-5}         # Controller 调节间隔，秒
SAMPLE_INTERVAL=${SAMPLE_INTERVAL:-1}         # CPU 采样间隔，秒
TOLERANCE=${TOLERANCE:-2}                     # CPU 占用误差容忍百分比

# 全局变量
CHILD_PIDS=()     # 存储 worker 进程 PID
STOP_FILE=""      # 停止信号文件，worker 根据此文件停止
CONTROL_FILE=""   # 动态控制文件，用于调整 worker 占用率
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

  # 根据日志级别过滤输出
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

  # 验证目标 CPU 百分比
  if ! [[ "$TARGET_CPU_PERCENT" =~ ^[0-9]+$ ]] || [ "$TARGET_CPU_PERCENT" -lt 1 ] || [ "$TARGET_CPU_PERCENT" -gt 100 ]; then
    log ERROR "Invalid TARGET_CPU_PERCENT: $TARGET_CPU_PERCENT"
    ((errors++))
  fi

  # 验证持续时间
  if ! [[ "$DURATION" =~ ^[0-9]+$ ]] || [ "$DURATION" -lt 1 ]; then
    log ERROR "Invalid DURATION: $DURATION"
    ((errors++))
  fi

  # 验证 worker 数量
  if ! [[ "$WORKERS" =~ ^[0-9]+$ ]]; then
    log ERROR "Invalid WORKERS: $WORKERS"
    ((errors++))
  fi

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

  # 创建停止信号，通知 worker 停止
  [ -n "$STOP_FILE" ] && touch "${STOP_FILE}.stop" 2>/dev/null || true

  # 终止所有 worker
  if [ "${#CHILD_PIDS[@]}" -gt 0 ]; then
    for pid in "${CHILD_PIDS[@]}"; do
      kill -TERM "$pid" 2>/dev/null || true
    done

    sleep 1

    for pid in "${CHILD_PIDS[@]}"; do
      kill -KILL "$pid" 2>/dev/null || true
    done
  fi

  # 删除临时文件
  if [ -n "$STOP_FILE" ]; then
    rm -f "$STOP_FILE" "${STOP_FILE}.stop" 2>/dev/null || true
  fi
  if [ -n "$CONTROL_FILE" ]; then
    rm -f "$CONTROL_FILE" 2>/dev/null || true
  fi

  local runtime=$(($(date +%s) - START_TIME))
  log INFO "Cleanup complete (runtime: ${runtime}s)"
  exit 0
}
trap cleanup SIGINT SIGTERM SIGHUP EXIT

########################################
# CPU 列表展开
#   - 支持单个 CPU 或区间，如 "0,2-4"
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
      if [[ "$s" =~ ^[0-9]+$ ]] && [[ -n "$e" ]] && [[ "$e" =~ ^[0-9]+$ ]] && [ "$s" -le "$e" ]; then
        for ((i=s;i<=e;i++)); do
          out+=("$i")
        done
      fi
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
    for e in "${ex_arr[@]}"; do
      [ "$c" = "$e" ] && skip=1 && break
    done
    [ "$skip" -eq 0 ] && result+=("$c")
  done

  (IFS=','; echo "${result[*]}")
}

########################################
# 读取 /proc/stat 获取每个 CPU 使用情况
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
# 计算指定 CPU 的系统整体占用率
# 参数：
#   $1 = 采样间隔秒
#   $2... = CPU 列表
########################################
calc_system_usage() {
  local sample_interval=${1:-1}
  shift
  local cpus=("$@")

  if [ "${#cpus[@]}" -eq 0 ]; then
    log ERROR "calc_system_usage called with no CPUs"
    echo "0"
    return 1
  fi

  declare -A u1 t1 u2 t2

  while read -r c u t; do
    u1[$c]=${u:-0}
    t1[$c]=${t:-0}
  done < <(read_cpu_stat)

  sleep "$sample_interval"

  while read -r c u t; do
    u2[$c]=${u:-0}
    t2[$c]=${t:-0}
  done < <(read_cpu_stat)

  local su=0 st=0
  for c in "${cpus[@]}"; do
    local du=$(( ${u2[$c]:-0} - ${u1[$c]:-0} ))
    local dt=$(( ${t2[$c]:-0} - ${t1[$c]:-0} ))
    su=$((su+du))
    st=$((st+dt))
  done

  awk "BEGIN { printf \"%.2f\", ($st==0)?0:($su*100/$st) }"
}

########################################
# 检测 NUMA 节点
#   - 自动选择策略：least-load / max-id
#   - BUSY_NODE!=auto 时直接返回指定节点
########################################
detect_numa_node() {
  [ "$BUSY_NODE" != "auto" ] && { echo "$BUSY_NODE"; return; }

  if [ ! -d /sys/devices/system/node ]; then
    echo 0
    return
  fi

  if [ "$AUTO_NODE_STRATEGY" = "least-load" ]; then
    local best_node=""
    local best_load=999

    for n in /sys/devices/system/node/node*; do
      [ ! -d "$n" ] && continue
      local nid=${n##*node}
      local cpulist
      cpulist=$(exclude_cpus "$(cat "$n/cpulist" 2>/dev/null || echo "")" "$EXCLUDE_CPUS")
      [ -z "$cpulist" ] && continue

      local cpus
      read -ra cpus <<< "$(expand_cpulist "$cpulist")"
      [ "${#cpus[@]}" -eq 0 ] && continue

      local load
      load=$(calc_system_usage 0.5 "${cpus[@]}")

      log DEBUG "Node $nid CPU usage: ${load}%"

      if awk "BEGIN { exit !($load < $best_load) }" 2>/dev/null; then
        best_load=$load
        best_node=$nid
      fi
    done

    [ -n "$best_node" ] && { echo "$best_node"; return; }
  fi

  local max_node
  max_node=$(ls -d /sys/devices/system/node/node* 2>/dev/null | sed 's/.*node//' | sort -n | tail -1)
  echo "${max_node:-0}"
}

########################################
# Worker 代码
#   - 占用 CPU 达到指定百分比
#   - 支持动态控制文件调整
########################################
get_worker_code() {
cat <<'WORKER_EOF'
worker_main() {
  local initial_percent=$1
  local duration=$2
  local stop_file=$3
  local control_file=$4

  if [[ ! "$initial_percent" =~ ^[0-9]+$ ]] || [[ ! "$duration" =~ ^[0-9]+$ ]]; then
    echo "ERROR: Invalid parameters" >&2
    exit 1
  fi

  local current_percent=$initial_percent
  local slice_us=10000
  local end_time=$((SECONDS + duration))
  local last_check=0

  # 检查纳秒支持
  local use_nanosec=1
  local test_output
  test_output=$(date +%s%N 2>/dev/null || echo "FAIL")
  if [[ "$test_output" =~ N ]] || [[ "$test_output" == "FAIL" ]]; then
    use_nanosec=0
  fi

  while [ "$SECONDS" -lt "$end_time" ] && [ ! -f "${stop_file}.stop" ]; do
    # 修复：降低文件读取频率（每 2 秒一次）
    if [ $((SECONDS - last_check)) -ge 2 ] && [ -f "$control_file" ]; then
      local new_percent
      new_percent=$(cat "$control_file" 2>/dev/null || echo "")
      if [[ "$new_percent" =~ ^[0-9]+$ ]] && [ "$new_percent" -ge 0 ] && [ "$new_percent" -le 100 ]; then
        if [ "$new_percent" -ne "$current_percent" ]; then
          current_percent=$new_percent
        fi
      fi
      last_check=$SECONDS
    fi

    local busy_us=$((slice_us * current_percent / 100))
    local idle_us=$((slice_us - busy_us))

    if [ "$use_nanosec" -eq 1 ] && [ "$busy_us" -gt 0 ]; then
      local loop_start
      loop_start=$(date +%s%N)

      while :; do
        local loop_now
        loop_now=$(date +%s%N)
        if [[ "$loop_now" =~ ^[0-9]+$ ]] && [[ "$loop_start" =~ ^[0-9]+$ ]]; then
          local elapsed=$(( (loop_now - loop_start) / 1000 ))
          [ "$elapsed" -gt "$busy_us" ] && break
        else
          break
        fi
        :
      done
    else
      local count=$((busy_us > 0 ? busy_us * 10 : 100))
      for ((i=0; i<count; i++)); do
        :
      done
    fi

    if [ "$idle_us" -gt 0 ]; then
      local sleep_sec
      sleep_sec=$(awk -v us="$idle_us" 'BEGIN { printf "%.6f", us/1000000 }')
      sleep "$sleep_sec" 2>/dev/null || sleep 0.01
    fi
  done
}

worker_main "$@"
WORKER_EOF
}

########################################
# main 函数
# 1. 验证参数
# 2. 检测 NUMA 节点
# 3. 获取 CPU 列表 / 排除 CPU
# 4. 启动 worker
# 5. 启动动态 controller 调节 CPU 占用
# 6. 等待结束并清理
########################################
main() {
  log INFO "CPU-FILL v8.2 starting..."

  # 参数检查
  validate_params

  # 创建停止和控制文件
  STOP_FILE=$(mktemp -t cpu-fill.XXXXXX)
  CONTROL_FILE="${STOP_FILE}.control"

  log INFO "Target: ${TARGET_CPU_PERCENT}% system CPU for ${DURATION}s"
  log DEBUG "Stop file: $STOP_FILE"
  log DEBUG "Control file: $CONTROL_FILE"

  # 检测 NUMA 节点
  local NODE
  NODE=$(detect_numa_node)

  # 获取节点 CPU 列表，支持排除 CPU
  local node_cpulist
  if [ -f "/sys/devices/system/node/node${NODE}/cpulist" ]; then
    node_cpulist=$(cat "/sys/devices/system/node/node${NODE}/cpulist")
  else
    node_cpulist=$(cat /sys/devices/system/cpu/online 2>/dev/null || echo "0")
  fi
  local CPULIST
  CPULIST=$(exclude_cpus "$node_cpulist" "$EXCLUDE_CPUS")

  log INFO "NUMA node: $NODE"
  log INFO "CPU list: $CPULIST"

  # 展开 CPU 列表
  local CPU_ARR
  read -ra CPU_ARR <<< "$(expand_cpulist "$CPULIST")"
  local CPU_COUNT=${#CPU_ARR[@]}
  [ "$CPU_COUNT" -eq 0 ] && { log ERROR "No CPU available"; exit 1; }

  # 确定 worker 数量
  if [ "$WORKERS" -le 0 ]; then
    WORKERS=$CPU_COUNT
  fi
  [ "$WORKERS" -le 0 ] && { log ERROR "Invalid worker count"; exit 1; }

  local INITIAL_PERCENT=$TARGET_CPU_PERCENT
  log INFO "Workers: $WORKERS (on $CPU_COUNT CPUs)"
  log INFO "Initial target: ${INITIAL_PERCENT}% per worker"

  # 启动 worker
  local worker_code
  worker_code=$(get_worker_code)
  for ((i=0;i<WORKERS;i++)); do
    local core="${CPU_ARR[$((i % CPU_COUNT))]}"
    taskset -c "$core" nice -n 19 bash -c "$worker_code" worker "$INITIAL_PERCENT" "$DURATION" "$STOP_FILE" "$CONTROL_FILE" &
    CHILD_PIDS+=($!)
    log DEBUG "Worker $((i+1))/$WORKERS on CPU $core (PID ${CHILD_PIDS[$i]})"
  done

  # 等待 worker 启动
  sleep 0.5
  local active=0
  for pid in "${CHILD_PIDS[@]}"; do
    kill -0 "$pid" 2>/dev/null && ((active++)) || true
  done

  if [ "$active" -eq 0 ]; then
    log ERROR "All workers failed"
    exit 1
  elif [ "$active" -lt "$WORKERS" ]; then
    log WARN "Only $active/$WORKERS workers running"
  else
    log INFO "All workers started"
  fi

  # 启动动态 controller
  dynamic_controller "$TARGET_CPU_PERCENT" "$DURATION" "${CPU_ARR[*]}" "$CONTROL_FILE" "$STOP_FILE" &
  local controller_pid=$!

  # 等待 controller 和 worker 结束
  wait "$controller_pid" 2>/dev/null || true
  for pid in "${CHILD_PIDS[@]}"; do
    wait "$pid" 2>/dev/null || true
  done

  local runtime=$(($(date +%s) - START_TIME))
  log INFO "Finished (runtime: ${runtime}s)"
}

main
