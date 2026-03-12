#!/usr/bin/env bash
set -euo pipefail

# 综合测试环境初始化脚本
# 包含完整的业务场景测试数据和验证

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查环境
check_environment() {
    log_info "检查测试环境..."
    
    # 检查 PostgreSQL 连接
    if ! command -v psql >/dev/null 2>&1; then
        log_error "PostgreSQL 客户端未安装，请先安装 psql"
        exit 1
    fi
    
    # 检查数据库连接
    export PGPASSWORD="${POSTGRES_PASSWORD:-postgres}"
    if ! psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -c "SELECT 1;" >/dev/null 2>&1; then
        log_error "无法连接到数据库，请检查数据库配置"
        exit 1
    fi
    
    # 检查 Java 环境
    if ! command -v java >/dev/null 2>&1; then
        log_error "Java 未安装或不在 PATH 中"
        exit 1
    fi
    
    log_success "环境检查通过"
}

# 清理现有数据
cleanup_data() {
    log_info "清理现有测试数据..."
    
    export PGPASSWORD="${POSTGRES_PASSWORD:-postgres}"
    psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" <<EOF
-- 清理测试数据
DELETE FROM quality_gate_results WHERE batch_date >= '2026-03-01';
DELETE FROM record_trace WHERE batch_date >= '2026-03-01';
DELETE FROM dlq_records WHERE created_at >= '2026-03-01';
DELETE FROM batch_run_records WHERE batch_date >= '2026-03-01';
DELETE FROM task_execution_state WHERE batch_date >= '2026-03-01';
DELETE FROM imported_records_partition WHERE batch_date >= '2026-03-01';
DELETE FROM imported_records WHERE batch_date >= '2026-03-01';
DELETE FROM task_trigger WHERE task_id IN ('processFileJob', 'dataExportJob', 'reconcileJob');
DELETE FROM task_definition WHERE task_id IN ('processFileJob', 'dataExportJob', 'reconcileJob');
EOF
    
    log_success "数据清理完成"
}

# 加载基础测试数据
load_basic_data() {
    log_info "加载基础测试数据..."
    
    export PGPASSWORD="${POSTGRES_PASSWORD:-postgres}"
    psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -f "${SCRIPT_DIR}/seed_imported_records.sql" >/dev/null
    psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -f "${SCRIPT_DIR}/seed_trace_and_dlq.sql" >/dev/null
    psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -f "${SCRIPT_DIR}/seed_reconcile_mismatch.sql" >/dev/null
    
    log_success "基础测试数据加载完成"
}

# 加载增强测试数据
load_enhanced_data() {
    log_info "加载增强测试数据..."
    
    export PGPASSWORD="${POSTGRES_PASSWORD:-postgres}"
    psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -f "${SCRIPT_DIR}/seed_enhanced_test_data.sql" >/dev/null
    
    log_success "增强测试数据加载完成"
}

# 验证数据加载
verify_data() {
    log_info "验证数据加载结果..."
    
    export PGPASSWORD="${POSTGRES_PASSWORD:-postgres}"
    
    # 检查导入记录
    imported_count=$(psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -t -c "SELECT COUNT(*) FROM imported_records WHERE batch_date = '2026-03-01';")
    log_info "导入记录数: $imported_count"
    
    # 检查任务执行状态
    task_state_count=$(psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -t -c "SELECT COUNT(*) FROM task_execution_state WHERE batch_date = '2026-03-01';")
    log_info "任务执行状态数: $task_state_count"
    
    # 检查死信队列
    dlq_count=$(psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -t -c "SELECT COUNT(*) FROM dlq_records WHERE created_at >= '2026-03-01';")
    log_info "死信队列记录数: $dlq_count"
    
    # 检查质量门禁
    quality_count=$(psql -h "${POSTGRES_HOST:-127.0.0.1}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-file_batch}" -t -c "SELECT COUNT(*) FROM quality_gate_results WHERE batch_date = '2026-03-01';")
    log_info "质量门禁结果数: $quality_count"
    
    log_success "数据验证完成"
}

# 生成测试报告
generate_report() {
    log_info "生成测试数据报告..."
    
    cat > "${PROJECT_DIR}/test-data-report.md" <<EOF
# 测试数据报告

## 数据概览

### 基础数据
- **导入记录**: 包含正常、失败、待处理等多种状态
- **任务执行状态**: 覆盖 COMPLETED、READY、FAILED 状态
- **死信队列**: 包含解析错误、数据库超时等场景
- **记录追踪**: 完整的导入导出链路

### 增强数据
- **任务定义**: processFileJob、dataExportJob、reconcileJob
- **触发器配置**: CRON、FIXED_DELAY、ONE_TIME 类型
- **批次运行记录**: 成功、运行中、失败状态
- **质量门禁**: 解析错误率、导出成功率、对账匹配率

## 测试场景覆盖

### 1. 文件导入测试
- 正常数据导入
- 重复数据幂等性
- 错误数据处理
- 大数据量分片处理

### 2. 数据导出测试
- 正常数据导出
- 数据库连接异常
- 导出成功率验证

### 3. 数据对账测试
- 源文件与数据库对账
- 不匹配数据处理
- 对账报告生成

### 4. 调度器测试
- CRON 调度验证
- 固定延迟调度
- 一次性任务执行
- 任务依赖关系

### 5. 容错机制测试
- 死信队列重放
- 任务重试机制
- 熔断器触发
- 质量门禁检查

## 使用方法

### 运行测试
\`\`\`bash
# 启动应用
./mvnw spring-boot:run

# 触发文件导入
curl -X POST "http://localhost:8011/api/files/upload" \\
  -F "file=@${SCRIPT_DIR}/large_dataset_financial.csv" \\
  -F "taskName=processFileJob"

# 触发数据导出
curl -X POST "http://localhost:8011/api/tasks/trigger" \\
  -H "Content-Type: application/json" \\
  -d '{"taskId": "dataExportJob"}'

# 查看任务状态
curl "http://localhost:8011/ops/dashboard"
\`\`\`

### 验证结果
\`\`\`sql
-- 查看导入结果
SELECT * FROM imported_records WHERE batch_date = '2026-03-01';

-- 查看执行状态
SELECT * FROM task_execution_state WHERE batch_date = '2026-03-01';

-- 查看死信队列
SELECT * FROM dlq_records WHERE created_at >= '2026-03-01';
\`\`\`

---
生成时间: $(date)
EOF
    
    log_success "测试报告已生成: ${PROJECT_DIR}/test-data-report.md"
}

# 主函数
main() {
    echo "=========================================="
    echo "🧪 综合测试环境初始化"
    echo "=========================================="
    
    check_environment
    cleanup_data
    
    if [[ "${1:-}" == "--enhanced" ]]; then
        load_enhanced_data
    else
        load_basic_data
    fi
    
    verify_data
    generate_report
    
    echo "=========================================="
    log_success "测试环境初始化完成！"
    echo ""
    echo "📋 下一步操作："
    echo "  1. 启动应用: ./mvnw spring-boot:run"
    echo "  2. 查看报告: cat test-data-report.md"
    echo "  3. 运行测试: ./mvnw test"
    echo "=========================================="
}

# 执行主函数
main "$@"
