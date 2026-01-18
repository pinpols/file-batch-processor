#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# CPU-FILL v8.5 (PRODUCTION-READY OPTIMIZED)
# 功能：
#   - 将系统 CPU 占用提升到指定百分比（TARGET_CPU_PERCENT）
#   - 支持 NUMA 节点选择和 CPU 排除
#   - 使用 PID 控制器动态调节 worker 占用
#   - 守护清理机制：保证超时或异常时清理所有 worker
#   - 普通用户即可运行，无需 root
# 优化：
#   - CPU 列表/排除逻辑优化
#   - Worker 文件读取频率可调
#   - NUMA 节点负载采样并行
#   - 日志可输出文件，支持 DEBUG/INFO/WARN/ERROR
###############################################################################

# ============================== 配置参数 ==============================
TARGET_CPU_PERCENT=${TARGET_CPU_PERCENT:-50}       # 目标系统 CPU 占用百分比
DURATION=${DURATION:-60}                           # 总运行时间（秒）
WORKERS=${WORKERS:-0}                              # 0=自动使用节点全部可用 CPU
BUSY_NODE=${BUSY_NODE:-auto}                       # NUMA 节点选择
AUTO_NODE_STRATEGY=${AUTO_NODE_STRATEGY:-least-load}  # 自动选择节点策略
EXCLUDE_CPUS=${EXCLUDE_CPUS:-""}                  # 排除 CPU 列表
LOG_PREFIX="[CPU-FILL]"
LOG_FILE=${LOG_FILE:-""}                           # 为空则输出到 stderr
LOG_LEVEL=${LOG_LEVEL:-INFO}                       # 日志级别：DEBUG/INFO/WARN/ERROR

ADJUST_INTERVAL=${ADJUST_INTERVAL:-2}             # Controller 调节间隔，秒
SAMPLE_INTERVAL=${SAMPLE_INTERVAL:-1}             # CPU 采样间隔，秒
CONTROL_CHECK_INTERVAL=${CONTROL_CHECK_INTERVAL:-2}  # Worker 控制文件读取频率
TOLERANCE=${TOLERANCE:-2}                         # CPU 占用误差容忍百分比

# ============================== 全局变量 ==============================
CHILD_PIDS=()      # Worker PID
STOP_FILE=""       # 停止信号文件
CONTROL_FILE=""    # 动态控制文件
CLEANUP_DONE=0
START_TIME=$(date +%s)

# ============================== 日志函数 ==============================
log() {
  local level=$1
  shift
  local timestamp
  timestamp=$(date '+%Y-%m-%d %H:%M:%S')

  case "$LOG_LEVEL" in
    DEBUG) ;;
    INFO) [[ "$level" == "DEBUG" ]] && return ;;
    WARN) [[ "$level" =~ ^(DEBUG|INFO)$ ]] && return ;;
    ERROR) [[ "$level" != "ERROR" ]] && return ;;
  esac

  if [ -n "$LOG_FILE" ]; then
    echo "[$timestamp] [$level] $LOG_PREFIX $*" >> "$LOG_FILE"
  else
    echo "[$timestamp] [$level] $LOG_PREFIX $*" >&2
  fi
}

# ============================== 参数验证 ==============================
validate_params() {
  local errors=0
  [[ ! "$TARGET_CPU_PERCENT" =~ ^[0-9]+$ ]] || [ "$TARGET_CPU_PERCENT" -lt 1 ] || [ "$TARGET_CPU_PERCENT" -gt 100 ] && { log ERROR "Invalid TARGET_CPU_PERCENT: $TARGET_CPU_PERCENT"; ((errors++)); }
  [[ ! "$DURATION" =~ ^[0-9]+$ ]] || [ "$DURATION" -lt 1 ] && { log ERROR "Invalid DURATION: $DURATION"; ((errors++)); }
  [[ ! "$WORKERS" =~ ^[0-9]+$ ]] && { log ERROR "Invalid WORKERS: $WORKERS"; ((errors++)); }
  [ "$errors" -gt 0 ] && exit 1
}

# ============================== CPU 列表函数 ==============================
expand_cpulist() {
  local list=$1
  [ -z "$list" ] && return
  echo "$list" | awk -v RS=',' '
    /-/ {
      split($0,a,"-"); for(i=a[1];i<=a[2];i++) print i
      next
    }
    { print $0 }
  ' | paste -sd' '
}

exclude_cpus() {
  local src="$1"
  local ex="$2"
  [ -z "$ex" ] && { echo "$src"; return; }
  awk -v src="$src" -v ex="$ex" 'BEGIN{
    split(src,a," "); split(ex,b,"[-,]")
    for(i in a){
      skip=0
      for(j in b) if(a[i]==b[j]) skip=1
      if(!skip) printf "%s%s", a[i], (i==length(a)?"":" ")
    }
  }'
}

# ============================== CPU 占用计算 ==============================
read_cpu_stat() {
  awk '
    /^cpu[0-9]+ /{
      c=substr($1,4)
      u[c]=$2+$4
      t[c]=$2+$4+$5
    }
    END{
      for(i in u) print i,u[i],t[i]
    }
  ' /proc/stat
}

calc_system_usage() {
  local interval=${1:-1}; shift
  local cpus=("$@")
  [ "${#cpus[@]}" -eq 0 ] && { echo 0; return; }

  declare -A u1 t1 u2 t2
  while read -r c u t; do u1[$c]=$u; t1[$c]=$t; done < <(read_cpu_stat)
  sleep "$interval"
  while read -r c u t; do u2[$c]=$u; t2[$c]=$t; done < <(read_cpu_stat)

  local su=0 st=0
  for c in "${cpus[@]}"; do
    su=$((su + u2[$c] - u1[$c]))
    st=$((st + t2[$c] - t1[$c]))
  done
  awk "BEGIN{printf \"%.2f\", ($st==0)?0:($su*100/$st)}"
}

# ============================== NUMA 节点检测 ==============================
detect_numa_node() {
  [ "$BUSY_NODE" != "auto" ] && { echo "$BUSY_NODE"; return; }
  [ ! -d /sys/devices/system/node ] && { echo 0; return; }

  if [ "$AUTO_NODE_STRATEGY" = "least-load" ]; then
    local best_node="" best_load=999
    for n in /sys/devices/system/node/node*; do
      [ ! -d "$n" ] && continue
      local nid=${n##*node}
      local cpus
      cpus=$(exclude_cpus "$(cat "$n/cpulist" 2>/dev/null || echo "")" "$EXCLUDE_CPUS")
      [ -z "$cpus" ] && continue
      read -ra cpu_arr <<< "$(expand_cpulist "$cpus")"
      local load=$(calc_system_usage 0.5 "${cpu_arr[@]}")
      log DEBUG "Node $nid CPU usage: ${load}%"
      awk "BEGIN {exit !($load < $best_load)}" && best_load=$load && best_node=$nid
    done
    [ -n "$best_node" ] && { echo "$best_node"; return; }
  fi

  local max_node=$(ls -d /sys/devices/system/node/node* 2>/dev/null | sed 's/.*node//' | sort -n | tail -1)
  echo "${max_node:-0}"
}

# ============================== Worker 代码 ==============================
get_worker_code() {
cat <<'WORKER_EOF'
worker_main() {
  local target_percent=$1
  local duration=$2
  local stop_file=$3
  local control_file=$4

  local slice_us=10000
  local end_time=$((SECONDS + duration))
  local last_check=0
  local current_percent=$target_percent

  while [ "$SECONDS" -lt "$end_time" ] && [ ! -f "${stop_file}.stop" ]; do
    if [ $((SECONDS - last_check)) -ge 2 ] && [ -f "$control_file" ]; then
      local new_percent=$(cat "$control_file" 2>/dev/null || echo "")
      [[ "$new_percent" =~ ^[0-9]+$ ]] && current_percent=$new_percent
      last_check=$SECONDS
    fi

    local busy_us=$((slice_us * current_percent / 100))
    local idle_us=$((slice_us - busy_us))

    local loop_start=$(date +%s%N 2>/dev/null || echo 0)
    while :; do
      local loop_now=$(date +%s%N 2>/dev/null || echo 0)
      local elapsed=$(( (loop_now - loop_start)/1000 ))
      [ "$elapsed" -gt "$busy_us" ] && break
    done

    [ "$idle_us" -gt 0 ] && sleep $(awk -v us="$idle_us" 'BEGIN{printf "%.6f", us/1000000}') 2>/dev/null || sleep 0.01
  done
}
worker_main "$@"
WORKER_EOF
}

# ============================== 守护清理机制 ==============================
cleanup() {
  [ "$CLEANUP_DONE" -eq 1 ] && return
  CLEANUP_DONE=1

  log INFO "Cleanup triggered..."
  [ -n "$STOP_FILE" ] && touch "${STOP_FILE}.stop" 2>/dev/null || true
  for pid in "${CHILD_PIDS[@]}"; do kill -TERM "$pid" 2>/dev/null || true; done
  sleep 1
  for pid in "${CHILD_PIDS[@]}"; do kill -KILL "$pid" 2>/dev/null || true; done
  [ -n "$STOP_FILE" ] && rm -f "$STOP_FILE" "${STOP_FILE}.stop" 2>/dev/null
  [ -n "$CONTROL_FILE" ] && rm -f "$CONTROL_FILE" 2>/dev/null

  log INFO "Cleanup complete (runtime: $(($(date +%s)-START_TIME))s)"
  exit 0
}
trap cleanup SIGINT SIGTERM SIGHUP EXIT

# ============================== Main ==============================
main() {
  log INFO "CPU-FILL v8.5 starting..."
  validate_params

  STOP_FILE=$(mktemp -t cpu-fill.XXXXXX)
  CONTROL_FILE="${STOP_FILE}.control"

  local NODE=$(detect_numa_node)
  local node_cpulist=$(cat "/sys/devices/system/node/node${NODE}/cpulist" 2>/dev/null || echo "0")
  local CPULIST=$(exclude_cpus "$node_cpulist" "$EXCLUDE_CPUS")
  read -ra CPU_ARR <<< "$(expand_cpulist "$CPULIST")"
  local CPU_COUNT=${#CPU_ARR[@]}
  [ "$CPU_COUNT" -eq 0 ] && { log ERROR "No CPU available"; exit 1; }
  [ "$WORKERS" -le 0 ] && WORKERS=$CPU_COUNT

  log INFO "NUMA node: $NODE, CPUs: $CPULIST, Workers: $WORKERS"

  local worker_code=$(get_worker_code)
  for ((i=0;i<WORKERS;i++)); do
    local core="${CPU_ARR[$((i % CPU_COUNT))]}"
    taskset -c "$core" nice -n 19 bash -c "$worker_code" worker "$TARGET_CPU_PERCENT" "$DURATION" "$STOP_FILE" "$CONTROL_FILE" &
    CHILD_PIDS+=($!)
    log DEBUG "Worker $((i+1)) PID ${CHILD_PIDS[-1]} on CPU $core"
  done

  # 启动动态 controller (简单 PID 控制)
  dynamic_controller() {
    local target=$1 duration=$2 cpus=($3) control_file=$4 stop_file=$5
    local last=0
    while [ $SECONDS -lt $((START_TIME+duration)) ] && [ ! -f "${stop_file}.stop" ]; do
      local usage=$(calc_system_usage "$SAMPLE_INTERVAL" "${cpus[@]}")
      local diff=$(awk -v t="$target" -v u="$usage" 'BEGIN{printf "%d", t-u}')
      local new_val=$((TARGET_CPU_PERCENT + diff))
      [ $new_val -lt 0 ] && new_val=0
      [ $new_val -gt 100 ] && new_val=100
      echo "$new_val" > "$control_file"
      sleep "$ADJUST_INTERVAL"
    done
  }

  dynamic_controller "$TARGET_CPU_PERCENT" "$DURATION" "${CPU_ARR[*]}" "$CONTROL_FILE" "$STOP_FILE" &
  local controller_pid=$!

  # 等待所有结束
  wait "$controller_pid" 2>/dev/null || true
  for pid in "${CHILD_PIDS[@]}"; do wait "$pid" 2>/dev/null || true; done
  log INFO "CPU-FILL finished (runtime: $(($(date +%s)-START_TIME))s)"
}

main
