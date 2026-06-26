# File Batch Processor - 项目架构和技术栈文档

## 📊 **项目概览**

**File Batch Processor** 是一个功能完整的企业级批量处理系统，基于Spring Boot和Spring Batch构建，支持文件导入、数据导出、任务调度和错误恢复等核心功能。

---

## 🏗️ **技术栈**

### **运行环境**
- ✅ **Java 21 (LTS)** - 编译/运行目标(由 maven-enforcer 强制 `[21,)`)
- ✅ **Spring Boot 4.0.3** - 主框架和依赖管理

### **核心框架**
- ✅ **Spring Batch** - 批量处理核心框架(chunk-oriented:Reader/Processor/Writer)
- ✅ **Spring Security** - 角色鉴权(viewer / operator / admin)
- ✅ **Spring Data JPA + JdbcTemplate** - 数据访问层(JPA 为主,批量热点用 JdbcTemplate)
- ✅ **Spring Quartz** - 任务调度框架(JDBC JobStore)

### **数据库和持久化**
- ✅ **PostgreSQL** - 主数据库
- ✅ **Flyway** - 数据库版本管理和迁移
- ✅ **Hibernate** - ORM框架
- ✅ **Spring Transaction** - 事务管理

### **监控和运维**
- ✅ **Spring Actuator** - 应用监控端点
- ✅ **Prometheus** - 指标收集和监控
- ✅ **Micrometer** - 指标注册和收集
- ✅ **自定义监控** - 批量任务监控

### **工具库和依赖**
- ✅ **Lombok 1.18.42** - 代码简化和注解处理
- ✅ **OpenCSV 5.8** - CSV文件处理
- ✅ **Apache Commons Lang3 3.14.0** - 工具类库
- ✅ **Hutool 5.8.32** - Java工具集
- ✅ **SSHJ 0.39.0** - SFTP客户端
- ✅ **Jackson** - JSON序列化和反序列化

### **测试框架**
- ✅ **Spring Boot Test** - 集成测试框架
- ✅ **Spring Batch Test** - 批量处理测试
- ✅ **Testcontainers 1.21.3** - 容器化测试
- ✅ **JUnit 5** - 单元测试框架
- ✅ **Mockito** - Mock测试框架

### **代码质量门禁(CI 强制)**
- ✅ **Spotless (palantir-java-format)** - 统一格式化,`spotless:check` 拦截不合规 PR
- ✅ **SpotBugs (effort=Max)** - 静态分析,带注释的排除过滤器(`config/spotbugs/spotbugs-exclude.xml`)只排除 Spring DI / DTO 公认误报与低价值 perf 模式,门禁聚焦正确性与安全
- ✅ **OWASP Dependency-Check + Trivy** - 依赖与文件系统安全扫描

> CI(`.github/workflows/ci.yml`)的 `code-quality` job 跑 Spotless + SpotBugs;`build-and-unit-test` 跑编译 + 单测;`integration-test` 在真 PostgreSQL 上跑 IT。

---

## 🏛️ **架构设计**

### **1. 整体分层架构**

```
┌─────────────────────────────────────┐
│           Controller Layer          │  ← REST API 接口层
│         (REST Endpoints)            │
├─────────────────────────────────────┤
│            Service Layer            │  ← 业务逻辑层
│         (Business Logic)            │
├─────────────────────────────────────┤
│           Batch Layer               │  ← 批量处理层
│    (Spring Batch Jobs/Steps)        │
├─────────────────────────────────────┤
│          Repository Layer           │  ← 数据访问层
│         (Data Access)               │
├─────────────────────────────────────┤
│           Database Layer            │  ← 数据存储层
│        (PostgreSQL)                 │
└─────────────────────────────────────┘
```

### **2. 批量处理架构**

```
┌─────────────────────────────────────┐
│         Job Orchestration           │  ← 任务编排和调度
│    (DagOrchestratorService)         │
├─────────────────────────────────────┤
│         Job Configuration            │  ← 任务配置管理
│   (FileImportJobConfig, etc.)       │
├─────────────────────────────────────┤
│    Reader → Processor → Writer      │  ← 标准处理链路
│  (ItemReader/Processor/Writer)       │
├─────────────────────────────────────┤
│         Error Handling               │  ← 错误处理机制
│   (DLQ, Retry, Skip)                │
├─────────────────────────────────────┤
│         Monitoring                  │  ← 监控和追踪
│  (Metrics, Traces, Audits)          │
└─────────────────────────────────────┘
```

---

## 📁 **项目结构**

### **核心包结构**

```
com.example.filebatchprocessor/
├── 📁 batch/                    # 批量处理核心
│   ├── 📁 config/              # 任务配置
│   │   ├── FileImportJobConfig.java      # 导入任务配置
│   │   └── DataExportJobConfig.java      # 导出任务配置
│   ├── 📁 processor/           # 数据处理器
│   │   ├── FileImportRecordProcessor.java # 导入处理器
│   │   └── ExportRecordProcessor.java     # 导出处理器
│   ├── 📁 reader/              # 数据读取器
│   │   ├── FileImportRecordReader.java   # 文件读取器
│   │   └── spi/                          # 解析器SPI
│   ├── 📁 writer/              # 数据写入器
│   │   ├── FileImportRecordWriter.java   # 导入写入器(Strategy Context)
│   │   ├── ExportRecordTraceWriter.java  # 追踪写入器
│   │   └── 📁 strategy/                   # 导入持久化策略(Strategy 模式)
│   │       ├── ChunkImportStrategy.java          # 策略接口
│   │       ├── BatchChunkImportStrategy.java     # 批量快路径
│   │       ├── PerRecordChunkImportStrategy.java # 逐条韧性降级
│   │       └── ImportContext.java                # step 内上下文
│   ├── 📁 scheduler/           # 调度器
│   │   ├── FixedDelayScheduler.java      # 固定延迟调度器
│   │   └── [其他调度组件]                 # 调度相关组件
│   ├── 📁 handler/             # 异常处理器
│   └── 📁 listener/            # 监听器
├── 📁 config/                  # 系统配置
│   ├── BatchConfig.java                 # 批量配置
│   ├── SecurityConfig.java              # 安全配置
│   └── TaskOrchestrationConfig.java     # 任务编排配置
├── 📁 service/                 # 业务服务
│   ├── FileReceptionService.java        # 文件接收服务
│   ├── FileExportService.java           # 文件导出服务
│   ├── DlqCompensationService.java      # DLQ补偿服务
│   └── [其他业务服务]                   # 各种业务服务
├── 📁 repository/              # 数据访问
│   ├── DlqRecordRepository.java         # DLQ记录仓库
│   ├── RecordTraceRepository.java       # 追踪记录仓库
│   └── [其他数据仓库]                   # 各种数据仓库
├── 📁 model/                   # 数据模型
│   ├── FileRecord.java                  # 文件记录模型
│   ├── ExportRecord.java                # 导出记录模型
│   ├── DlqRecord.java                   # DLQ记录模型
│   └── [其他数据模型]                   # 各种数据模型
├── 📁 controller/              # REST控制器
├── 📁 scheduler/               # 任务调度
├── 📁 exception/               # 异常定义
├── 📁 params/                  # 参数管理
│   ├── ImportJobParams.java             # 导入任务参数
│   └── ExportJobParams.java             # 导出任务参数
├── 📁 observability/           # 可观测性
├── 📁 util/                    # 工具类
└── 📄 FileBatchProcessorApplication.java # 主应用类
```

---

## 🔄 **数据流转架构**

### **完整数据链路**

```
📁 文件输入 
    ↓
📖 FileImportRecordReader (文件读取)
    ↓
⚙️ FileImportRecordProcessor (数据处理)
    ↓
💾 FileImportRecordWriter (数据库写入)
    ↓
🗄️ PostgreSQL (数据存储)
    ↓
📖 JdbcCursorItemReader (数据库读取)
    ↓
⚙️ ExportRecordProcessor (导出处理)
    ↓
✍️ ExportRecordTraceWriter (文件写入+追踪)
    ↓
📁 文件输出
```

### **错误处理链路**

```
❌ 处理错误
    ↓
🔄 重试机制 (Retry)
    ↓
⏭️ 跳过机制 (Skip)
    ↓
📦 DLQ队列 (死信队列)
    ↓
🔄 DlqCompensationService (补偿服务)
    ↓
✅ 重新处理
```

---

## 🧩 **导入写入设计(Strategy 模式)**

导入写入器 `FileImportRecordWriter` 是 Strategy 模式的 **Context**,对每个 chunk 选择持久化策略:

```
chunk 到达
    ↓
批内去重(有界,batch.import.max-dedup-keys 默认 100w,防超大文件 OOM)
    ↓
┌─ 默认:BatchChunkImportStrategy(快路径)
│     • 一次 INSERT ... ON CONFLICT (business_key,batch_date,partition_key) DO NOTHING
│     • 一次批量回查 id 关联 trace
│     • 3N 次 round-trip → 约 3 次/chunk;幂等交唯一约束兜底,去掉逐条 SELECT
│
└─ 批量失败(非瞬时)自动降级 → PerRecordChunkImportStrategy(韧性路径)
      • 每条 REQUIRES_NEW 独立事务,单条失败不连累整批
      • 坏记录落 DLQ 后上抛,交 Spring Batch skip/retry
      （瞬时异常 TransientImportException 直接上抛由 Batch 重试,不降级）
```

| | 批量快路径 | 逐条韧性路径 |
|---|---|---|
| 何时用 | 默认(健康 chunk) | 批量抛非瞬时异常时降级 |
| round-trip | ~3 次/chunk | 3N 次/chunk |
| 失败粒度 | 整 chunk 回滚后降级 | 单条隔离 → DLQ |
| 幂等 | ON CONFLICT DO NOTHING | importRecord 先查/撞约束回查 |

**落库幂等的唯一真相**:唯一索引 `(business_key, batch_date, partition_key)`——两条路径都靠它兜底,不依赖"先查后插"的原子性。

`chunk-size`(默认 200)、`max-dedup-keys`、`fetch-size` 均可通过配置调整。

## 🧩 **导出流式设计**

导出 `exportReader` 使用 `JdbcCursorItemReaderBuilder` + `setFetchSize`(`batch.export.fetch-size` 默认 500),
让 PostgreSQL 走**服务端游标按批拉取**,内存恒定,避免默认把整个结果集读进内存导致大导出 OOM。

---

## ⚡ **核心特性**

### **1. 批量处理能力**

#### **文件导入**
- ✅ **流式解析** - 逐行读取,CSV / 定长(SPI 可扩展)
- ✅ **分片处理** - 按 shardIndex/shardTotal 切分
- ✅ **批量幂等写入** - chunk 级 `ON CONFLICT DO NOTHING`,失败降级逐条 + DLQ(见上文 Strategy 设计)
- ✅ **数据验证** - 业务规则校验 + 解析错误率门禁

#### **数据导出**
- ✅ **自定义SQL** - 灵活的数据查询
- ✅ **安全检查** - SQL注入防护
- ✅ **格式化输出** - 多种文件格式
- ✅ **条件导出** - 按业务条件筛选

#### **错误恢复**
- ✅ **DLQ机制** - 死信队列处理
- ✅ **重试策略** - 智能重试算法
- ✅ **补偿服务** - 失败数据恢复
- ✅ **状态追踪** - 完整处理记录

### **2. 调度编排**

#### **任务调度**
- ✅ **Quartz集成** - 复杂任务调度
- ✅ **DAG编排** - 任务依赖管理
- ✅ **集群支持** - 分布式调度
- ✅ **固定延迟** - 精确时间控制

#### **容错机制**
- ✅ **故障检测** - 自动故障识别
- ✅ **任务恢复** - 自动重启机制
- ✅ **负载均衡** - 任务分发优化
- ✅ **资源管理** - 内存和线程控制

### **3. 监控运维**

#### **指标监控**
- ✅ **Prometheus集成** - 指标收集
- ✅ **自定义指标** - 业务指标监控
- ✅ **性能追踪** - 任务执行时间
- ✅ **资源监控** - 系统资源使用

#### **运维管理**
- ✅ **健康检查** - Actuator端点
- ✅ **审计日志** - 完整操作记录
- ✅ **告警机制** - 异常自动通知
- ✅ **配置管理** - 动态配置更新

### **4. 安全特性**

#### **认证授权**
- ✅ **Spring Security** - 安全框架
- ✅ **角色鉴权** - viewer / operator / admin 三级(密码带编码前缀,如 `{noop}`/`{bcrypt}`)
- ✅ **变更审批** - Ops 变更请求带审批流(OpsChangeManagementService)
- ✅ **API安全** - 接口访问控制 + 审计

#### **数据安全**
- ✅ **SQL注入防护** - 参数化查询
- ✅ **文件安全** - SFTP安全传输
- ✅ **数据加密** - 敏感信息保护
- ✅ **审计追踪** - 操作记录完整

---

## 🎯 **技术亮点**

### **1. 企业级批量处理**
- **Spring Batch生态** - 成熟的批量处理框架
- **分片并行处理** - 大数据量高性能处理
- **事务一致性** - ACID事务保证
- **容错恢复** - 多层次错误处理

### **2. 高可用设计**
- **集群部署** - 多实例负载均衡
- **故障自愈** - 自动故障检测和恢复
- **状态持久化** - 任务状态不丢失
- **优雅关闭** - 安全停机机制

### **3. 扩展性设计**
- **SPI机制** - 文件解析器可插拔
- **插件化架构** - 处理器动态加载
- **配置驱动** - 运行时参数配置
- **模块化设计** - 功能独立可扩展

### **4. 技术栈**
- **Java 21 (LTS)** - 长期支持版本,工具链兼容性好
- **Spring Boot 4.0.3** - Spring 生态
- **容器化支持** - Docker(eclipse-temurin:21)
- **质量门禁** - Spotless + SpotBugs 进 CI,规范靠机器强制而非自觉

---

## 🔧 **配置和部署**

### **关键配置**

#### **数据库配置**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
```

#### **批量处理配置**
```yaml
spring:
  batch:
    job:
      enabled: false
  quartz:
    job-store-type: jdbc
    threadCount: 8

# 导入/导出调优(均有默认值,可按硬件与数据形态调整)
batch:
  import:
    chunk-size: 200          # 导入 chunk 大小
    max-dedup-keys: 1000000  # 批内去重集合上限,超过仅靠 DB 唯一约束兜底
  export:
    fetch-size: 500          # 导出游标 fetchSize(PG 服务端游标按批拉取)
```

#### **监控配置**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### **部署要求**

#### **环境要求**
- **Java 21 (LTS)** - 运行环境(maven-enforcer 强制 `[21,)`)
- **PostgreSQL 12+** - 数据库
- **内存要求** - 最小2GB，推荐4GB+
- **CPU要求** - 最小2核，推荐4核+

#### **部署方式**
- **单机部署** - 开发和测试环境
- **集群部署** - 生产环境推荐
- **容器化部署** - Docker/Kubernetes
- **云平台部署** - AWS/Azure/GCP

---

## 📈 **性能指标**

> 说明:以下为设计目标/方向,非基准实测值;真实吞吐取决于硬件、PG 配置与数据形态,需以压测为准。

### **处理能力(设计取向)**
- **大文件** - 导入逐行流式读、导出服务端游标,内存随文件大小恒定
- **导入吞吐** - chunk 级批量插入把每条 3 次 round-trip 降到约 3 次/chunk,是主要吞吐杠杆
- **容错** - 批量失败降级逐条 + DLQ,坏数据不阻断整批

### **资源使用**
- **内存效率** - 导入流式 + 导出 fetchSize 游标 + 去重集合有界,避免随数据量线性膨胀
- **I/O优化** - 批量读写(JDBC batchUpdate + chunk 提交)
- **可调** - chunk-size / fetch-size / max-dedup-keys 均可配

---

## 🎉 **总结**

**File Batch Processor** 是一个功能完整、技术先进的企业级批量处理系统，具备：

### ✅ **技术优势**
- **现代化技术栈** - 使用最新稳定版本
- **成熟框架生态** - 基于Spring生态
- **高性能处理** - 支持大数据量处理
- **可扩展架构** - 插件化设计

### ✅ **架构优势**
- **分层清晰** - 职责分离明确
- **容错完善** - 多层次错误处理
- **监控完备** - 全链路追踪监控
- **运维友好** - 丰富的管理接口

### ✅ **业务价值**
- **数据流转** - 完整的导入导出链路
- **错误恢复** - 智能故障处理
- **调度编排** - 复杂任务管理
- **安全可靠** - 企业级安全保障

这是一个**生产就绪的企业级批量处理平台**，能够满足各种复杂的数据处理需求！🚀
