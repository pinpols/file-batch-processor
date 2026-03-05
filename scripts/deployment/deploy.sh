#!/usr/bin/env bash
set -euo pipefail

# Docker 部署脚本
# 支持开发和生产环境部署

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# 显示帮助信息
show_help() {
    cat <<EOF
Docker 部署脚本

用法:
    $0 [选项] [环境]

环境:
    dev     开发环境部署
    prod    生产环境部署

选项:
    -h, --help     显示帮助信息
    -b, --build    强制重新构建镜像
    -c, --clean    清理旧的容器和镜像
    -d, --dev       开发模式（默认）
    -p, --prod      生产模式
    -v, --verbose   详细输出

示例:
    $0 dev                    # 部署开发环境
    $0 prod                   # 部署生产环境
    $0 -b -c prod            # 清理并重新构建生产环境
    $0 --build --verbose dev   # 详细模式构建开发环境

EOF
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    # 检查 Docker
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    
    # 检查 Docker Compose
    if ! command -v docker-compose >/dev/null 2>&1; then
        log_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    
    # 检查 Docker 服务状态
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker 服务未运行，请启动 Docker 服务"
        exit 1
    fi
    
    log_success "依赖检查通过"
}

# 清理旧资源
cleanup_resources() {
    log_info "清理旧的容器和镜像..."
    
    # 停止并删除容器
    docker-compose -f "${COMPOSE_FILE}" down --volumes --remove-orphans 2>/dev/null || true
    
    if [[ "${CLEAN_IMAGES:-}" == "true" ]]; then
        # 删除相关镜像
        docker images | grep "file-batch-processor" | awk '{print $3}' | xargs -r docker rmi 2>/dev/null || true
        docker images | grep "none" | awk '{print $3}' | xargs -r docker rmi 2>/dev/null || true
    fi
    
    log_success "资源清理完成"
}

# 构建镜像
build_image() {
    if [[ "${BUILD_IMAGE:-}" == "true" ]]; then
        log_info "构建 Docker 镜像..."
        docker-compose -f "${COMPOSE_FILE}" build --no-cache --parallel
        log_success "镜像构建完成"
    else
        log_info "使用现有镜像（跳过构建）"
    fi
}

# 部署服务
deploy_services() {
    log_info "部署 ${ENVIRONMENT} 环境服务..."
    
    # 创建必要的目录
    mkdir -p "${PROJECT_DIR}/logs"
    mkdir -p "${PROJECT_DIR}/uploads"
    
    # 设置环境变量文件
    local env_file="${PROJECT_DIR}/.env.${ENVIRONMENT}"
    if [[ ! -f "${env_file}" ]]; then
        log_warning "环境变量文件不存在: ${env_file}，使用默认配置"
        env_file=""
    fi
    
    # 启动服务
    if [[ -n "${env_file}" ]]; then
        docker-compose -f "${COMPOSE_FILE}" --env-file "${env_file}" up -d
    else
        docker-compose -f "${COMPOSE_FILE}" up -d
    fi
    
    log_success "服务部署完成"
}

# 等待服务就绪
wait_for_services() {
    log_info "等待服务启动..."
    
    local max_wait=300
    local wait_time=0
    
    while [[ $wait_time -lt $max_wait ]]; do
        if docker-compose -f "${COMPOSE_FILE}" ps | grep -q "Up"; then
            log_success "服务已启动"
            break
        fi
        
        sleep 5
        wait_time=$((wait_time + 5))
        
        if [[ $wait_time -ge $max_wait ]]; then
            log_error "服务启动超时"
            docker-compose -f "${COMPOSE_FILE}" ps
            exit 1
        fi
    done
}

# 显示服务状态
show_status() {
    log_info "服务状态:"
    docker-compose -f "${COMPOSE_FILE}" ps
    
    echo ""
    log_info "访问地址:"
    echo "  应用: http://localhost:8011"
    echo "  健康检查: http://localhost:8011/actuator/health"
    
    if [[ "${ENVIRONMENT}" == "dev" ]]; then
        echo "  数据库管理: http://localhost:8080"
        echo "  Redis: localhost:6379"
    else
        echo "  Prometheus: http://localhost:9090"
        echo "  Grafana: http://localhost:3000 (admin/admin123)"
        echo "  AlertManager: http://localhost:9093"
    fi
}

# 显示日志
show_logs() {
    log_info "显示服务日志..."
    docker-compose -f "${COMPOSE_FILE}" logs -f --tail=100
}

# 主函数
main() {
    local environment=""
    local build_image="false"
    local clean_images="false"
    
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
                clean_images="true"
                shift
                ;;
            -d|--dev)
                environment="dev"
                shift
                ;;
            -p|--prod)
                environment="prod"
                shift
                ;;
            -v|--verbose)
                set -x
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
    
    # 设置默认环境
    if [[ -z "${environment}" ]]; then
        environment="dev"
    fi
    
    # 设置全局变量
    export ENVIRONMENT="${environment}"
    export BUILD_IMAGE="${build_image}"
    export CLEAN_IMAGES="${clean_images}"
    
    if [[ "${environment}" == "prod" ]]; then
        export COMPOSE_FILE="${PROJECT_DIR}/docker-compose.prod.yml"
    else
        export COMPOSE_FILE="${PROJECT_DIR}/docker-compose.dev.yml"
    fi
    
    echo "=========================================="
    echo "🐳 Docker 部署脚本"
    echo "=========================================="
    echo "环境: ${ENVIRONMENT}"
    echo "构建: ${build_image}"
    echo "清理: ${clean_images}"
    echo "=========================================="
    
    # 执行部署流程
    check_dependencies
    
    if [[ "${clean_images}" == "true" ]]; then
        cleanup_resources
    fi
    
    build_image
    deploy_services
    wait_for_services
    show_status
    
    echo "=========================================="
    log_success "部署完成！"
    echo ""
    echo "📋 常用命令:"
    echo "  查看日志: $0 --logs ${environment}"
    echo "  停止服务: docker-compose -f ${COMPOSE_FILE} down"
    echo "  重启服务: docker-compose -f ${COMPOSE_FILE} restart"
    echo "=========================================="
}

# 处理日志参数
if [[ "${1:-}" == "--logs" ]]; then
    environment="${2:-dev}"
    if [[ "${environment}" == "prod" ]]; then
        export COMPOSE_FILE="${PROJECT_DIR}/docker-compose.prod.yml"
    else
        export COMPOSE_FILE="${PROJECT_DIR}/docker-compose.dev.yml"
    fi
    show_logs
    exit 0
fi

# 执行主函数
main "$@"
