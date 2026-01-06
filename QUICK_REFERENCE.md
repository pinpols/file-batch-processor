# 🚀 快速参考指南

## 功能 1️⃣ : 文件导入到分区表

### Service 类
- **类名**: `PartitionedImportService`
- **位置**: `service/PartitionedImportService.java`

### 快速用法

```java
// 单条导入
ImportedRecordPartitioned record = partitionedImportService.importRecord(
    businessKey, name, description, batchDate, sourceFileName, checksum
);

// 批量导入
List<ImportedRecordPartitioned> records = partitionedImportService.importRecordsBatch(recordList);

// 查询
List<ImportedRecordPartitioned> data = partitionedImportService.queryByBatchDate("2025-01-06");

// 统计
long count = partitionedImportService.countByBatchDate("2025-01-06");
```

### 关键字段

| 字段 | 说明 | 例值 |
|------|------|------|
| partition_key | 分区键（yyyy_MM） | 2025_01 |
| batch_date | 批次日期 | 2025-01-06 |
| business_key | 业务键 | BIZ_20250106_001 |
| checksum | 数据校验 | abc123def456 |

---

## 功能 2️⃣ : 数据导出到文件

### Service 类
- **类名**: `EnhancedExportService`
- **位置**: `service/EnhancedExportService.java`

### 快速用法

```java
// CSV 导出
String path = exportService.exportToCSV(
    "/export/data",              // 目录
    "export_data.csv",           // 文件名
    data,                        // 二维数组
    headers                      // 表头
);

// 生成带时间戳的名字
String fileName = exportService.generateFileName("export", "csv");
// export_20250106_143025.csv

// 验证导出文件
boolean valid = exportService.validateExportFile(path);
```

### 支持格式

| 格式 | 方法 | 状态 | 依赖库 |
|------|------|------|--------|
| CSV | `exportToCSV()` | ✅ 已实现 | OpenCSV |
| JSON | `exportToJSON()` | 📋 待集成 | Jackson |
| Excel | `exportToExcel()` | 📋 待集成 | Apache POI |
| ZIP | `exportCompressed()` | 📋 待集成 | 内置 |

---

## 功能 3️⃣ : 文件接收和等待

### Service 类
- **类名**: `FileReceptionService`
- **位置**: `service/FileReceptionService.java`

### 快速用法

```java
// 接收文件
FileReceptionQueue queue = receptionService.receiveFile(
    "data.csv",
    "/data/input/data.csv",
    "SFTP_SERVER"
);

// 标记为等待
receptionService.markAsWaiting(queue.getId(), "等待依赖文件");

// 标记为处理中
receptionService.markAsProcessing(queue.getId());

// 标记为完成
receptionService.markAsCompleted(queue.getId());

// 标记为失败
receptionService.markAsFailed(queue.getId(), "处理异常");

// 查找待处理文件
List<FileReceptionQueue> pending = receptionService.findPendingFiles();

// 验证文件完整性
boolean valid = receptionService.verifyFileIntegrity(queue.getId());

// 获取统计
FileReceptionStats stats = receptionService.getStatistics();
```

### 文件状态

```
RECEIVED → [WAITING] → PROCESSING → COMPLETED
                    ↘              ↗
                       → FAILED
```

---

## 功能 4️⃣ : 文件分发和重传

### Service 类
- **类名**: `FileDistributionService`
- **位置**: `service/FileDistributionService.java`

### 快速用法

```java
// 创建分发任务
FileDistributionTask task = distributionService.createDistributionTask(
    "export.csv",
    "/export/data/export.csv",
    "SFTP",
    "192.168.1.100:/remote"
);

// SFTP 分发
distributionService.distributeBySFTP(
    task.getId(),
    "sftp.server.com",
    22,
    "username",
    "password",
    "/remote/incoming"
);

// HTTP 分发
distributionService.distributeByHTTP(
    task.getId(),
    "http://api.example.com/upload",
    "POST"
);

// 标记为成功
distributionService.markAsSuccess(task.getId());

// 标记为失败（自动处理重试）
boolean canRetry = distributionService.markAsFailed(task.getId(), "错误信息");

// 查找可重试任务
List<FileDistributionTask> retriable = distributionService.findRetryableTasks(5);

// 手动重试
distributionService.retryFailedTask(task.getId());

// 获取统计
FileDistributionStats stats = distributionService.getStatistics();
```

### 重试配置

```
默认重试次数: 3 次
默认重试间隔: 300 秒（5分钟）
可在 createDistributionTask 后通过修改 task 对象调整
```

---

## 📊 数据库表对应

| 功能 | 表名 | 模型类 |
|------|------|--------|
| 分区导入 | `imported_records_partition` | `ImportedRecordPartitioned` |
| 导出文件 | （无表） | `EnhancedExportService` |
| 文件接收 | `file_reception_queue` | `FileReceptionQueue` |
| 文件分发 | `file_distribution_task` | `FileDistributionTask` |

---

## 🔗 Service 依赖注入

```java
// 在 Spring Bean 中注入
@Autowired
private PartitionedImportService partitionedImportService;

@Autowired
private EnhancedExportService enhancedExportService;

@Autowired
private FileReceptionService fileReceptionService;

@Autowired
private FileDistributionService fileDistributionService;
```

---

## 📋 检查清单

- [ ] 创建数据库表
- [ ] 注入所有 Service
- [ ] 测试各功能
- [ ] 集成 SFTP 库（JSch）
- [ ] 集成 HTTP 客户端（RestTemplate）
- [ ] 集成 JSON 导出库（Jackson）
- [ ] 集成 Excel 导出库（POI）
- [ ] 添加监控指标
- [ ] 创建定时任务（接收监听、分发重试）

---

## ✅ 编译验证

```bash
# 编译所有代码
mvn clean compile

# 预期输出
# ✅ Compilation successful
```

---

## 💡 常见问题

**Q: 如何处理文件接收的依赖关系？**
A: 使用 `markAsWaiting()` 设置等待原因，当依赖满足时调用 `markAsProcessing()` 继续。

**Q: 分发任务失败了怎么办？**
A: `markAsFailed()` 返回 true 表示会自动重试，否则需要手动调用 `retryFailedTask()`。

**Q: 如何自定义重试策略？**
A: 修改 `FileDistributionTask` 的 `maxRetries` 和 `retryIntervalSeconds` 字段。

**Q: 支持哪些导出格式？**
A: 目前 CSV 已实现，JSON 和 Excel 需要集成相应库后实现。

---

## 🎯 典型流程

### 完整数据处理流程

```
1. 接收阶段 (FileReceptionService)
   ├─ receiveFile()       // 接收文件并注册
   ├─ markAsWaiting()     // 等待依赖（可选）
   └─ verifyFileIntegrity() // 验证完整性

2. 导入阶段 (PartitionedImportService)
   ├─ importRecordsBatch()    // 批量导入数据
   ├─ generatePartitionKey()  // 自动分区
   └─ markAsCompleted()       // 标记处理完成

3. 导出阶段 (EnhancedExportService)
   ├─ queryByBatchDate()    // 查询需要导出的数据
   ├─ exportToCSV()         // 导出为文件
   └─ validateExportFile()  // 验证导出结果

4. 分发阶段 (FileDistributionService)
   ├─ createDistributionTask()  // 创建分发任务
   ├─ distributeBySFTP()        // 分发文件
   ├─ markAsSuccess()           // 成功处理
   └─ markAsFailed()            // 失败处理 + 重试
```

---

**最后更新**: 2025-01-06  
**版本**: v1.0  
**状态**: ✅ 完成
