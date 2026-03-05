#!/usr/bin/env bash
set -euo pipefail

# 环境快速切换脚本
# 一键在 dev 和 prod 环境之间切换

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

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

# 显示当前环境状态
show_current_env() {
    echo ""
    echo -e "${PURPLE}=========================================="
    echo "🌍 当前环境状态"
    echo "==========================================${NC}"
    
    # 检查运行中的容器
    local dev_running=$(docker ps --filter "name=file-batch-app-dev" --quiet | wc -l)
    local prod_running=$(docker ps --filter "name=file-batch-app" --quiet | wc -l)
    
    if [[ $dev_running -gt 0 ]]; then
        echo -e "${GREEN}🟢 当前环境: DEV${NC}"
        echo -e "${CYAN}📋 访问地址:${NC}"
        echo "  应用: http://localhost:8011"
        echo "  数据库管理: http://localhost:8080"
        echo "  Redis: localhost:6379"
        echo "  调试端口: localhost:5005"
    elif [[ $prod_running -gt 0 ]]; then
        echo -e "${GREEN}🟢 当前环境: PROD${NC}"
        echo -e "${CYAN}📋 访问地址:${NC}"
        echo "  应用: http://localhost:8011"
        echo "  Prometheus: http://localhost:9090"
        echo "  Grafana: http://localhost:3000"
        echo "  AlertManager: http://localhost:9093"
    else
        echo -e "${YELLOW}🟡 当前环境: 未运行${NC}"
    fi
    
    echo ""
    echo -e "${CYAN}🛠️ 快速操作:${NC}"
    echo "  切换到开发环境: ./scripts/switch-env.sh dev"
    echo "  切换到生产环境: ./scripts/switch-env.sh prod"
    echo "  停止所有环境: ./scripts/switch-env.sh stop"
    echo "  查看详细状态: ./scripts/switch-env.sh status"
}

# 切换到指定环境
switch_to_env() {
    local target_env="$1"
    
    echo ""
    echo -e "${PURPLE}=========================================="
    echo "🔄 切换到 ${target_env^^} 环境"
    echo "==========================================${NC}"
    
    # 停止当前运行的环境
    log_info "停止当前环境..."
    docker ps --filter "name=file-batch" --quiet | xargs -r docker stop 2>/dev/null || true
    
    # 清理 docker-compose 服务
    for compose_file in docker-compose.dev.yml docker-compose.prod.yml; do
        if [[ -f "${PROJECT_DIR}/${compose_file}" ]]; then
            docker-compose -f "${PROJECT_DIR}/${compose_file}" down 2>/dev/null || true
        fi
    done
    
    # 启动目标环境
    log_info "启动 ${target_env} 环境..."
    
    local compose_file="${PROJECT_DIR}/docker-compose.${target_env}.yml"
    local env_file="${PROJECT_DIR}/.env.${target_env}"
    
    if [[ ! -f "${compose_file}" ]]; then
        log_error "配置文件不存在: ${compose_file}"
        exit 1
    fi
    
    # 创建必要目录
    mkdir -p "${PROJECT_DIR}/logs"
    mkdir -p "${PROJECT_DIR}/uploads"
    
    # 启动服务
    if [[ -f "${env_file}" ]]; then
        docker-compose -f "${compose_file}" --env-file "${env_file}" up -d
    else
        docker-compose -f "${compose_file}" up -d
    fi
    
    # 等待服务启动
    log_info "等待服务启动..."
    sleep 10
    
    # 验证启动状态
    local container_name="file-batch-app"
    if [[ "${target_env}" == "dev" ]]; then
        container_name="file-batch-app-dev"
    fi
    
    if docker ps --filter "name=${container_name}" --quiet | grep -q .; then
        log_success "${target_env^^} 环境启动成功！"
        show_env_info "${target_env}"
    else
        log_error "${target_env^^} 环境启动失败！"
        docker-compose -f "${compose_file}" ps
        exit 1
    fi
}

# 显示环境信息
show_env_info() {
    local environment="$1"
    
    echo ""
    echo -e "${CYAN}🎯 ${environment^^} 环境信息:${NC}"
    echo "  应用地址: http://localhost:8011"
    echo "  健康检查: http://localhost:8011/actuator/health"
    echo "  API 文档: http://localhost:8011/swagger-ui.html"
    
    if [[ "${environment}" == "dev" ]]; then
        echo "  数据库管理: http://localhost:8080 (adminer)"
        echo "  Redis: localhost:6379"
        echo "  调试端口: localhost:5005"
        echo ""
        echo -e "${GREEN}✅ 开发特性已启用:${NC}"
        echo "  🔥 热重载"
        echo "  🐛 调试模式"
        echo "  📝 详细日志"
        echo "  ⚙️  YAML 配置"
    else
        echo "  Prometheus: http://localhost:9090"
        echo "  Grafana: http://localhost:3000 (admin/admin123)"
        echo "  AlertManager: http://localhost:9093"
        echo ""
        echo -e "${GREEN}✅ 生产特性已启用:${NC}"
        echo "  📊 完整监控"
        echo "  🚨 告警系统"
        echo "  🔒 安全配置"
        echo "  📈 性能优化"
    fi
}

# 停止所有环境
stop_all_env() {
    echo ""
    echo -e "${PURPLE}=========================================="
    echo "🛑 停止所有环境"
    echo "==========================================${NC}"
    
    log_info "停止所有 file-batch 容器..."
    docker ps --filter "name=file-batch" --quiet | xargs -r docker stop 2>/dev/null || true
    
    log_info "清理 docker-compose 服务..."
    for compose_file in docker-compose.dev.yml docker-compose.prod.yml; do
        if [[ -f "${PROJECT_DIR}/${compose_file}" ]]; then
            docker-compose -f "${PROJECT_DIR}/${compose_file}" down 2>/dev/null || true
        fi
    done
    
    log_success "所有环境已停止"
}

# 显示详细状态
show_detailed_status() {
    echo ""
    echo -e "${PURPLE}=========================================="
    echo "📊 详细环境状态"
    echo "==========================================${NC}"
    
    echo -e "${CYAN}🐳 容器状态:${NC}"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep "file-batch" || echo "  无运行中的容器"
    
    echo ""
    echo -e "${CYAN}📁 数据卷状态:${NC}"
    docker volume ls | grep "file-batch" || echo "  无相关数据卷"
    
    echo ""
    echo -e "${CYAN}🌐 网络状态:${NC}"
    docker network ls | grep "file-batch" || echo "  无相关网络"
    
    echo ""
    echo -e "${CYAN}📊 资源使用:${NC}"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" | grep "file-batch" || echo "  无运行中的容器"
}

# 显示帮助信息
show_help() {
    cat <<EOF
环境快速切换脚本

用法:
    $0 [命令] [环境]

命令:
    (无参数)    显示当前环境状态
    dev         切换到开发环境
    prod        切换到生产环境
    stop        停止所有环境
    status      显示详细状态
    help        显示帮助信息

示例:
    $0              # 显示当前状态
    $0 dev          # 切换到开发环境
    $0 prod         # 切换到生产环境
    $0 stop         # 停止所有环境
    $0 status       # 显示详细状态

特性:
    🔄 一键切换环境
    🛑 自动清理旧环境
    ✅ 健康检查验证
    📊 状态信息显示
    🛠️ 管理命令提示

EOF
}

# 主函数
main() {
    local command="${1:-}"
    
    case "${command}" in
        dev)
            switch_to_env "dev"
            ;;
        prod)
            switch_to_env "prod"
            ;;
        stop)
            stop_all_env
            ;;
        status)
            show_detailed_status
            ;;
        help|--help|-h)
            show_help
            ;;
        "")
            show_current_env
            ;;
        *)
            log_error "未知命令: ${command}"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"
