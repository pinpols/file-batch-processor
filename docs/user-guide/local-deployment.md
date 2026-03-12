# 本地部署指南

## 🚀 快速开始

本项目支持 **dev** 和 **prod** 两种本地部署环境，提供一键切换和管理功能。

## 📋 环境对比

| 特性 | 开发环境 (dev) | 生产环境 (prod) |
|------|----------------|----------------|
| **数据库** | PostgreSQL + Redis | PostgreSQL |
| **配置源** | YAML | 数据库 |
| **日志级别** | DEBUG | INFO |
| **热重载** | ✅ 支持 | ❌ 不支持 |
| **调试模式** | ✅ 开启 | ❌ 关闭 |
| **监控工具** | Adminer | Prometheus + Grafana |
| **安全配置** | 基础 | 完整 |
| **资源占用** | 低 | 中等 |

## 🎯 一键部署

### 方式一：使用快速切换脚本（推荐）

```bash
# 切换到开发环境
./scripts/switch-env.sh dev

# 切换到生产环境
./scripts/switch-env.sh prod

# 查看当前状态
./scripts/switch-env.sh

# 停止所有环境
./scripts/switch-env.sh stop

# 查看详细状态
./scripts/switch-env.sh status
```

### 方式二：使用完整部署脚本

```bash
# 部署开发环境
./scripts/local-deploy.sh deploy dev

# 部署生产环境
./scripts/local-deploy.sh deploy prod

# 重新构建并部署
./scripts/local-deploy.sh deploy prod --build

# 清理并部署
./scripts/local-deploy.sh deploy dev --clean
```

## 🔧 环境配置

### 开发环境配置 (.env.dev)

```bash
# 数据库配置
POSTGRES_DB=file_batch
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# 应用配置
SPRING_PROFILES_ACTIVE=dev
CONFIG_SOURCE=yaml
LOG_LEVEL=DEBUG

# JVM 配置（包含调试）
JAVA_OPTS=--XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+UseG1GC -Xdebug
```

### 生产环境配置 (.env.prod)

```bash
# 数据库配置
POSTGRES_DB=file_batch
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password_here

# 应用配置
SPRING_PROFILES_ACTIVE=prod
CONFIG_SOURCE=db
LOG_LEVEL=INFO

# JVM 配置（优化性能）
JAVA_OPTS=--XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+UseG1GC
```

## 🌐 访问地址

### 开发环境

| 服务 | 地址 | 说明 |
|------|------|------|
| **应用** | http://localhost:8011 | 主应用 |
| **健康检查** | http://localhost:8011/actuator/health | 健康状态 |
| **API 文档** | http://localhost:8011/swagger-ui.html | 接口文档 |
| **数据库管理** | http://localhost:8080 | Adminer 管理界面 |
| **Redis** | localhost:6379 | 缓存服务 |
| **调试端口** | localhost:5005 | Java 远程调试 |

### 生产环境

| 服务 | 地址 | 说明 |
|------|------|------|
| **应用** | http://localhost:8011 | 主应用 |
| **健康检查** | http://localhost:8011/actuator/health | 健康状态 |
| **API 文档** | http://localhost:8011/swagger-ui.html | 接口文档 |
| **Prometheus** | http://localhost:9090 | 监控数据 |
| **Grafana** | http://localhost:3000 | 可视化面板 |
| **AlertManager** | http://localhost:9093 | 告警管理 |

## 🛠️ 常用命令

### 环境管理

```bash
# 快速切换环境
./scripts/switch-env.sh dev    # 开发环境
./scripts/switch-env.sh prod   # 生产环境

# 查看环境状态
./scripts/switch-env.sh

# 停止所有环境
./scripts/switch-env.sh stop
```

### 服务管理

```bash
# 查看服务状态
docker-compose -f docker-compose.dev.yml ps
docker-compose -f docker-compose.prod.yml ps

# 查看服务日志
docker-compose -f docker-compose.dev.yml logs -f
docker-compose -f docker-compose.prod.yml logs -f

# 重启服务
docker-compose -f docker-compose.dev.yml restart
docker-compose -f docker-compose.prod.yml restart

# 停止服务
docker-compose -f docker-compose.dev.yml down
docker-compose -f docker-compose.prod.yml down
```

### 数据管理

```bash
# 连接数据库
docker exec -it file-batch-postgres psql -U postgres -d file_batch

# 备份数据库
docker exec file-batch-postgres pg_dump -U postgres file_batch > backup.sql

# 恢复数据库
docker exec -i file-batch-postgres psql -U postgres file_batch < backup.sql

# 查看数据文件
ls -la ~/Library/Containers/com.docker.docker/Data/volumes/file-batch*
```

## 🧪 测试环境

### 自动化测试

```bash
# 测试开发环境
./scripts/local-deploy.sh test dev

# 测试生产环境
./scripts/local-deploy.sh test prod
```

### 手动测试

```bash
# 健康检查
curl http://localhost:8011/actuator/health

# API 测试
curl http://localhost:8011/ops/dashboard

# 文件上传测试
curl -X POST "http://localhost:8011/api/files/upload" \
  -F "file=@scripts/testdata/import_success.csv" \
  -F "taskName=processFileJob"
```

## 🔍 故障排查

### 常见问题

#### 1. 端口冲突
```bash
# 检查端口占用
lsof -i :8011
lsof -i :5432

# 解决方案：修改 docker-compose.yml 中的端口映射
ports:
  - "8012:8011"  # 使用其他端口
```

#### 2. 容器启动失败
```bash
# 查看容器日志
docker logs file-batch-app
docker logs file-batch-postgres

# 重新构建镜像
./scripts/local-deploy.sh deploy dev --build --clean
```

#### 3. 数据库连接问题
```bash
# 检查数据库状态
docker exec file-batch-postgres pg_isready -U postgres -d file_batch

# 重启数据库
docker restart file-batch-postgres
```

#### 4. 内存不足
```bash
# 查看资源使用
docker stats

# 调整 JVM 参数
export JAVA_OPTS="-Xmx1g -Xms512m"
```

### 日志分析

```bash
# 查看应用日志
docker-compose -f docker-compose.dev.yml logs app

# 查看数据库日志
docker-compose -f docker-compose.dev.yml logs postgres

# 实时跟踪日志
docker-compose -f docker-compose.dev.yml logs -f --tail=100
```

## 📊 性能优化

### 开发环境优化

```yaml
# docker-compose.dev.yml
services:
  app:
    environment:
      # 减少内存使用
      JAVA_OPTS: "-Xmx512m -Xms256m"
      # 启用调试
      JAVA_TOOL_OPTIONS: "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
    # 限制资源
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '0.5'
```

### 生产环境优化

```yaml
# docker-compose.prod.yml
services:
  app:
    environment:
      # 优化 JVM
      JAVA_OPTS: "-XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
    # 生产级资源配置
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
        reservations:
          memory: 1G
          cpus: '0.5'
```

## 🔐 安全配置

### 开发环境
- 基础安全配置
- 调试端口开放
- 详细日志输出

### 生产环境
- 非 root 用户运行
- 网络隔离
- 安全密码配置
- 访问日志记录

## 📚 更多文档

- [Docker 部署详解](../docs/docker-deployment.md)
- [环境配置说明](../docs/environment-config.md)
- [故障排查指南](../docs/troubleshooting.md)
- [性能优化指南](../docs/performance-tuning.md)

---

**🎉 现在您可以轻松在 dev 和 prod 环境之间切换了！**
