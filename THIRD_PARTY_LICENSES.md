# 第三方开源软件声明

本项目使用以下开源软件。版本以 `pom.xml`、容器编排文件和实际构建产物为准；本文档用于说明主要依赖及许可证义务。

## 🏗️ 核心框架

### Spring Framework / Spring Boot
- **名称**：Spring Boot、Spring Batch、Spring Security
- **版本**：Spring Boot 4.1.x 管理版本
- **许可证**：Apache License 2.0
- **用途**：应用核心框架、批处理、安全认证
- **官网**：https://spring.io/

### Spring Batch
- **名称**：Spring Batch
- **版本**：Spring Boot 4.1.x 管理版本
- **许可证**：Apache License 2.0
- **用途**：批处理任务调度和执行
- **官网**：https://spring.io/projects/spring-batch

## 🗄️ 数据库

### PostgreSQL
- **名称**：PostgreSQL
- **版本**：17
- **许可证**：PostgreSQL License
- **用途**：主数据库存储
- **官网**：https://www.postgresql.org/

### Flyway
- **名称**：Flyway
- **版本**：集成版本
- **许可证**：Apache License 2.0
- **用途**：数据库版本管理
- **官网**：https://flywaydb.org/

## 📊 数据处理

### OpenCSV
- **名称**：OpenCSV
- **版本**：5.8
- **许可证**：Apache License 2.0
- **用途**：CSV 文件读写处理
- **官网**：http://opencsv.sourceforge.net/

### Apache Commons Lang
- **名称**：Apache Commons Lang
- **版本**：3.14.0
- **许可证**：Apache License 2.0
- **用途**：Java 工具类库
- **官网**：https://commons.apache.org/proper/commons-lang/

### Jackson
- **名称**：Jackson
- **版本**：集成版本
- **许可证**：Apache License 2.0
- **用途**：JSON 序列化/反序列化
- **官网**：https://github.com/FasterXML/jackson

### Hutool
- **名称**：Hutool
- **版本**：5.8.32
- **许可证**：Apache License 2.0
- **用途**：Java 工具类库
- **官网**：https://hutool.cn/

## 🔧 开发工具

### Lombok
- **名称**：Project Lombok
- **版本**：1.18.46
- **许可证**：MIT License
- **用途**：Java 代码生成工具
- **官网**：https://projectlombok.org/

### SSHJ
- **名称**：SSHJ
- **版本**：0.39.0
- **许可证**：BSD License
- **用途**：SFTP 客户端
- **官网**：https://github.com/hierynomus/sshj

## 🧪 测试框架

### Testcontainers
- **名称**：Testcontainers
- **版本**：2.0.5
- **许可证**：MIT License
- **用途**：集成测试容器化
- **官网**：https://testcontainers.com/

### JUnit
- **名称**：JUnit Jupiter
- **版本**：集成版本
- **许可证**：Eclipse Public License 1.0
- **用途**：单元测试框架
- **官网**：https://junit.org/

## 🐳 容器化

### Docker
- **名称**：Docker
- **版本**：多版本支持
- **许可证**：Apache License 2.0
- **用途**：应用容器化部署
- **官网**：https://www.docker.com/

### Docker Compose
- **名称**：Docker Compose
- **版本**：3.8
- **许可证**：Apache License 2.0
- **用途**：多容器编排
- **官网**：https://docs.docker.com/compose/

## 📊 监控工具

### Prometheus
- **名称**：Prometheus
- **版本**：v3.4.2
- **许可证**：Apache License 2.0
- **用途**：监控指标收集
- **官网**：https://prometheus.io/

### Grafana
- **名称**：Grafana
- **版本**：11.4.0
- **许可证**：Apache License 2.0
- **用途**：监控数据可视化
- **官网**：https://grafana.com/

### Alertmanager
- **名称**：Alertmanager
- **版本**：v0.29.0
- **许可证**：Apache License 2.0
- **用途**：告警管理
- **官网**：https://prometheus.io/docs/alerting/latest/alertmanager/

### Micrometer
- **名称**：Micrometer
- **版本**：集成版本
- **许可证**：Apache License 2.0
- **用途**：应用指标监控
- **官网**：https://micrometer.io/

## 🛠️ 开发工具

### Adminer
- **名称**：Adminer
- **版本**：4.8.1
- **许可证**：Apache License 2.0
- **用途**：数据库管理工具
- **官网**：https://www.adminer.org/

### Redis
- **名称**：Redis
- **版本**：8
- **许可证**：BSD License
- **用途**：缓存和会话存储
- **官网**：https://redis.io/

## 📋 许可证兼容性

所有使用的开源软件许可证都与本项目 Apache-2.0 许可证兼容：

| 许可证 | 兼容性 | 限制 |
|----------|----------|------|
| **Apache License 2.0** | ✅ 完全兼容 | 需要保留版权声明 |
| **MIT License** | ✅ 完全兼容 | 需要保留版权声明 |
| **BSD License** | ✅ 完全兼容 | 需要保留版权声明 |
| **Eclipse Public License 1.0** | ✅ 完全兼容 | 需要保留版权声明 |
| **PostgreSQL License** | ✅ 完全兼容 | 需要保留版权声明 |

## 致谢

感谢以下开源社区和开发者：

- **Spring 团队**：提供了优秀的企业级 Java 开发框架
- **PostgreSQL 社区**：提供了可靠的关系型数据库
- **Docker 团队**：容器化基础设施
- **Prometheus 团队**：现代化的监控解决方案
- **所有开源贡献者**：让软件开发更加高效和可靠

## 📝 使用声明

本项目遵循以下原则：

1. **合规使用**：所有开源软件都按照许可证要求使用
2. **版权保留**：在文档和代码中保留原始版权信息
3. **致谢明确**：明确声明使用的第三方开源软件
4. **透明公开**：所有依赖关系都公开透明

## 联系信息

如有任何关于开源软件使用的问题，请联系：

- **GitHub Issues**：https://github.com/pinpols/file-batch-processor/issues
- **安全或许可证问题**：按仓库安全策略登记的渠道处理
