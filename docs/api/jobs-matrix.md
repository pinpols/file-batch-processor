# Jobs Matrix
> 中文名：作业矩阵说明

## 作业对照表
| 作业名 | 入口类 | 触发方式 | 关键参数 |
|---|---|---|---|
| `processFileJob` / `importJob` | `FileImportJobHandler` + `FileImportJobConfig` | `CRON` / `FIXED_RATE` / `ONE_TIME` | `input`, `batchDate`, `shard.index`, `shard.total`, `runMode`, `rerunId`, `priority` |
| `dataExportJob` | `DataExportJobConfig` | `CRON` | `export.sql`, `output.file.name` |
| `partitionedImportJob` | `PartitionedImportJobHandler` | `CRON` | `batchDate` |
| `fileExportJob` | `FileExportJobHandler` | `CRON` | `batchDate`, `format`, `outputDir` |
| `fileReceptionJob` | `FileReceptionJobHandler` | `FIXED_RATE` | `scanWindowMinutes`, `sourceDir` |
| `fileReceptionTimeoutJob` | `FileReceptionJobHandler` | `CRON` | `timeoutMinutes` |
| `fileDistributionJob` | `FileDistributionJobHandler` | `FIXED_RATE` | `targetType`, `targetPath`, `maxRetries`, `retryIntervalSeconds` |
| `fileDistributionRetryJob` | `FileDistributionJobHandler` | `FIXED_RATE` | `minutesInterval` |
| `fileDistributionTimeoutJob` | `FileDistributionJobHandler` | `CRON` | `timeoutHours` |
| `dlqReplayJob` | `FileImportJobHandler` | `FIXED_RATE` / 手工触发 | `limit` |
| `batchRestartJob` | `FileImportJobHandler` | 手工触发 / 可配置定时 | `executionId` 或 `jobName` |
| `dagOrchestratorJob` | `DagOrchestratorJobHandler` | `CRON` / 手工触发 | `dagId`, `batchDate`, `rerunId` |

## 参数示例

### 1) `processFileJob` / `importJob`
```text
input=/data/inbound/customer_20260301.csv&batchDate=2026-03-01&runMode=normal&rerunId=&priority=5&shard.index=0&shard.total=1
```

### 2) `dataExportJob`
```text
export.sql=select id,business_key,name,description,batch_date from imported_records where batch_date='2026-03-01'&output.file.name=export/data_export_20260301.csv
```

### 3) `partitionedImportJob`
```text
batchDate=2026-03-01
```

### 4) `fileExportJob`
```text
batchDate=2026-03-01&format=csv&outputDir=export
```

### 5) `fileReceptionJob`
```text
scanWindowMinutes=10&sourceDir=/data/inbound
```

### 6) `fileReceptionTimeoutJob`
```text
timeoutMinutes=60
```

### 7) `fileDistributionJob`
```text
targetType=http&targetPath=https://downstream.internal/api/upload&maxRetries=3&retryIntervalSeconds=300
```

### 8) `fileDistributionRetryJob`
```text
minutesInterval=15
```

### 9) `fileDistributionTimeoutJob`
```text
timeoutHours=12
```

### 10) `dlqReplayJob`
```text
limit=50
```

### 11) `batchRestartJob`
```text
executionId=12345
```
或
```text
jobName=importJob
```

### 12) `dagOrchestratorJob`
```text
dagId=default-main-pipeline&batchDate=2026-03-04&rerunId=
```
