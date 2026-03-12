#!/usr/bin/env bash
set -euo pipefail

# 本地部署脚本 - 支持 dev 和 prod 环境
# 提供一键部署、切换和管理功能

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

log_header() {
    echo -e "${PURPLE}==========================================${NC}"
    echo -e "${PURPLE}$1${NC}"
    echo -e "${PURPLE}==========================================${NC}"
}

# 显示帮助信息
show_help() {
    cat <<EOF
本地部署脚本 - 支持 dev 和 prod 环境

用法:
    $0 [命令] [选项] [环境]

命令:
    deploy      部署指定环境
    switch      切换环境
    status      显示当前状态
    stop        停止所有服务
    clean       清理所有资源
    logs        显示服务日志
    test        测试环境

环境:
    dev         开发环境
    prod        生产环境

选项:
    -h, --help     显示帮助信息
    -b, --build    强制重新构建镜像
    -c, --clean    部署前清理资源
    -v, --verbose   详细输出
    -f, --force     强制执行（跳过确认）

示例:
    $0 deploy dev                    # 部署开发环境
    $0 deploy prod --build           # 重新构建并部署生产环境
    $0 switch dev                   # 切换到开发环境
    $0 status                       # 显示当前状态
    $0 logs dev                     # 查看开发环境日志
    $0 clean --force                # 强制清理所有资源

EOF
}

# 检查当前环境状态
check_current_status() {
    log_info "检查当前环境状态..."
    
    echo ""
    echo -e "${CYAN}🐳 Docker 容器状态:${NC}"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep "file-batch" || echo "  无运行中的容器"
    
    echo ""
    echo -e "${CYAN}📁 数据卷状态:${NC}"
    docker volume ls | grep "file-batch" || echo "  无相关数据卷"
    
    echo ""
    echo -e "${CYAN}🌐 网络状态:${NC}"
    docker network ls | grep "file-batch" || echo "  无相关网络"
    
    echo ""
    echo -e "${CYAN}📊 端口占用:${NC}"
    for port in 8011 5432 8080 3000 9090 9093 6379; do
        if lsof -i :$port >/dev/null 2>&1; then
            echo "  端口 $port: 已占用"
        else
            echo "  端口 $port: 空闲"
        fi
    done
}

# 停止所有服务
stop_all_services() {
    log_info "停止所有服务..."
    
    # 停止所有相关容器
    docker ps -q --filter "name=file-batch" | xargs -r docker stop 2>/dev/null || true
    
    # 停止 docker-compose 服务
    for compose_file in docker-compose.dev.yml docker-compose.prod.yml; do
        if [[ -f "${PROJECT_DIR}/${compose_file}" ]]; then
            docker-compose -f "${PROJECT_DIR}/${compose_file}" down 2>/dev/null || true
        fi
    done
    
    log_success "所有服务已停止"
}

# 清理所有资源
clean_all_resources() {
    if [[ "${FORCE_CLEAN:-}" != "true" ]]; then
        echo -e "${YELLOW}⚠️  这将删除所有容器、镜像和数据卷！${NC}"
        read -p "确定要继续吗？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "操作已取消"
            exit 0
        fi
    fi
    
    log_info "清理所有资源..."
    
    # 停止并删除容器
    stop_all_services
    
    # 删除相关镜像
    docker images | grep "file-batch-processor" | awk '{print $3}' | xargs -r docker rmi 2>/dev/null || true
    
    # 删除数据卷
    docker volume ls -q | grep "file-batch" | xargs -r docker volume rm 2>/dev/null || true
    
    # 删除网络
    docker network ls -q | grep "file-batch" | xargs -r docker network rm 2>/dev/null || true
    
    log_success "所有资源已清理"
}

# 切换环境
switch_environment() {
    local target_env="$1"
    
    log_info "切换到 ${target_env} 环境..."
    
    # 停止当前环境
    stop_all_services
    
    # 部署目标环境
    deploy_environment "${target_env}"
    
    log_success "已切换到 ${target_env} 环境"
    show_environment_info "${target_env}"
}

# 部署环境
deploy_environment() {
    local environment="$1"
    local compose_file="${PROJECT_DIR}/docker-compose.${environment}.yml"
    
    if [[ ! -f "${compose_file}" ]]; then
        log_error "配置文件不存在: ${compose_file}"
        exit 1
    fi
    
    log_info "部署 ${environment} 环境..."
    
    # 创建必要的目录
    mkdir -p "${PROJECT_DIR}/logs"
    mkdir -p "${PROJECT_DIR}/uploads"
    mkdir -p "${PROJECT_DIR}/data"
    
    # 设置环境变量文件
    local env_file="${PROJECT_DIR}/.env.${environment}"
    if [[ ! -f "${env_file}" ]]; then
        log_warning "环境变量文件不存在: ${env_file}，使用默认配置"
        env_file=""
    fi
    
    # 清理旧资源（如果需要）
    if [[ "${CLEAN_BEFORE_DEPLOY:-}" == "true" ]]; then
        docker-compose -f "${compose_file}" down -v 2>/dev/null || true
    fi
    
    # 构建镜像（如果需要）
    if [[ "${BUILD_IMAGE:-}" == "true" ]]; then
        log_info "构建 Docker 镜像..."
        docker-compose -f "${compose_file}" build --no-cache --parallel
    fi
    
    # 启动服务
    log_info "启动服务..."
    if [[ -n "${env_file}" ]]; then
        docker-compose -f "${compose_file}" --env-file "${env_file}" up -d
    else
        docker-compose -f "${compose_file}" up -d
    fi
    
    # 等待服务启动
    wait_for_services "${compose_file}"
    
    log_success "${environment} 环境部署完成"
}

# 等待服务启动
wait_for_services() {
    local compose_file="$1"
    local max_wait=120
    local wait_time=0
    
    log_info "等待服务启动..."
    
    while [[ $wait_time -lt $max_wait ]]; do
        local healthy_count=$(docker-compose -f "${compose_file}" ps | grep -c "Up\|healthy" || true)
        local total_count=$(docker-compose -f "${compose_file}" ps | wc -l)
        
        if [[ $healthy_count -gt 0 ]]; then
            log_success "服务已启动 ($healthy_count/$total_count 健康)"
            break
        fi
        
        sleep 5
        wait_time=$((wait_time + 5))
        
        if [[ $wait_time -ge $max_wait ]]; then
            log_warning "服务启动超时，显示当前状态..."
            docker-compose -f "${compose_file}" ps
            break
        fi
    done
}

# 显示环境信息
show_environment_info() {
    local environment="$1"
    
    echo ""
    log_header "🎯 ${environment^^} 环境信息"
    
    echo -e "${CYAN}📋 服务地址:${NC}"
    echo "  应用: http://localhost:8011"
    echo "  健康检查: http://localhost:8011/actuator/health"
    echo "  API 文档: http://localhost:8011/swagger-ui.html"
    
    if [[ "${environment}" == "dev" ]]; then
        echo "  数据库管理: http://localhost:8080 (adminer)"
        echo "  Redis: localhost:6379"
        echo "  调试端口: localhost:5005"
        echo ""
        echo -e "${CYAN}🔧 开发特性:${NC}"
        echo "  ✅ 热重载支持"
        echo "  ✅ 调试模式"
        echo "  ✅ 详细日志"
        echo "  ✅ YAML 配置"
    else
        echo "  Prometheus: http://localhost:9090"
        echo "  Grafana: http://localhost:3000 (admin/admin123)"
        echo "  AlertManager: http://localhost:9093"
        echo ""
        echo -e "${CYAN}📊 监控特性:${NC}"
        echo "  ✅ 完整监控栈"
        echo "  ✅ 告警系统"
        echo "  ✅ 性能指标"
        echo "  ✅ 日志聚合"
    fi
    
    echo ""
    echo -e "${CYAN}🛠️ 管理命令:${NC}"
    echo "  查看日志: $0 logs ${environment}"
    echo "  重启服务: docker-compose -f docker-compose.${environment}.yml restart"
    echo "  停止服务: $0 stop"
    echo "  切换环境: $0 switch [dev|prod]"
    echo "  查看状态: $0 status"
}

# 显示日志
show_logs() {
    local environment="$1"
    local compose_file="${PROJECT_DIR}/docker-compose.${environment}.yml"
    
    if [[ ! -f "${compose_file}" ]]; then
        log_error "配置文件不存在: ${compose_file}"
        exit 1
    fi
    
    log_info "显示 ${environment} 环境日志..."
    docker-compose -f "${compose_file}" logs -f --tail=100
}

# 测试环境
test_environment() {
    local environment="$1"
    
    log_info "测试 ${environment} 环境..."
    
    # 测试应用健康检查
    if curl -f http://localhost:8011/actuator/health >/dev/null 2>&1; then
        log_success "✅ 应用健康检查通过"
    else
        log_error "❌ 应用健康检查失败"
    fi
    
    # 测试数据库连接
    if docker exec file-batch-postgres-${environment} pg_isready -U postgres -d file_batch >/dev/null 2>&1; then
        log_success "✅ 数据库连接正常"
    else
        log_error "❌ 数据库连接失败"
    fi
    
    # 测试 API 端点
    if curl -f http://localhost:8011/ops/dashboard >/dev/null 2>&1; then
        log_success "✅ API 端点正常"
    else
        log_error "❌ API 端点失败"
    fi
    
    log_success "环境测试完成"
}

# 主函数
main() {
    local command=""
    local environment=""
    local build_image="false"
    local clean_before_deploy="false"
    local force_clean="false"
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -b|--build)
                build_image="true"
                shift
                ;;
            -c|--clean)
                clean_before_deploy="true"
                shift
                ;;
            -v|--verbose)
                set -x
                shift
                ;;
            -f|--force)
                force_clean="true"
                shift
                ;;
            deploy|switch|status|stop|clean|logs|test)
                command="$1"
                shift
                ;;
            dev|prod)
                environment="$1"
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 设置全局变量
    export BUILD_IMAGE="${build_image}"
    export CLEAN_BEFORE_DEPLOY="${clean_before_deploy}"
    export FORCE_CLEAN="${force_clean}"
    
    # 执行命令
    case "${command}" in
        deploy)
            if [[ -z "${environment}" ]]; then
                log_error "请指定环境: dev 或 prod"
                show_help
                exit 1
            fi
            log_header "🚀 部署 ${environment^^} 环境"
            deploy_environment "${environment}"
            show_environment_info "${environment}"
            ;;
        switch)
            if [[ -z "${environment}" ]]; then
                log_error "请指定目标环境: dev 或 prod"
                show_help
                exit 1
            fi
            log_header "🔄 切换到 ${environment^^} 环境"
            switch_environment "${environment}"
            ;;
        status)
            log_header "📊 当前环境状态"
            check_current_status
            ;;
        stop)
            log_header "🛑 停止所有服务"
            stop_all_services
            ;;
        clean)
            log_header "🧹 清理所有资源"
            clean_all_resources
            ;;
        logs)
            if [[ -z "${environment}" ]]; then
                log_error "请指定环境: dev 或 prod"
                show_help
                exit 1
            fi
            show_logs "${environment}"
            ;;
        test)
            if [[ -z "${environment}" ]]; then
                log_error "请指定环境: dev 或 prod"
                show_help
                exit 1
            fi
            log_header "🧪 测试 ${environment^^} 环境"
            test_environment "${environment}"
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
