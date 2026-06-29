# Jobs Parameters Contract
> 中文名：作业参数契约说明

## 1. processFileJob / importJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `input` | 是（外部触发时） | 无 | 外部参数键，必须为可读文件路径 |
| `input.file.name` | 否（内部） | 无 | Spring Batch JobParameter 键（由 handler 内部写入） |
| `batchDate` | 否 | 当天日期 | 推荐 `yyyy-MM-dd` |
| `runMode` | 否 | `normal` | `normal/backfill/replay` |
| `rerunId` | 否 | 空 | 重跑唯一标识 |
| `shard.index` | 否 | `0` | `>=0` |
| `shard.total` | 否 | `1` | `>=1` 且 `index<total` |

## 2. dataExportJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `export.sql` | 是 | 无 | 只允许查询语句 |
| `output.file.name` | 是 | 无 | 输出文件名或相对路径；配置 `batch.io.output-base-dir` 后限定在该目录内 |

## 3. partitionedImportJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `batchDate` | 是 | 无 | `yyyy-MM-dd` |

## 4. fileExportJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `batchDate` | 是 | 无 | `yyyy-MM-dd` |
| `format` | 否 | `csv` | `csv/json` |
| `outputDir` | 否 | 空 | 相对 `batch.io.output-base-dir` 的子目录；为空时直接写入输出基目录 |

## 5. dlqReplayJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `limit` | 否 | `50` | `1~1000` |
| `source` | 否 | 自动筛选 | 仅重放 `retryable=true && manual_required=false && next_retry_at<=now` |

## 6. batchRestartJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `executionId` | 二选一 | 无 | Long > 0 |
| `jobName` | 二选一 | 无 | 非空字符串 |
| `restartMode` | 否 | `FAILED_STEP` | 语义说明：按失败 Step 继续（Spring Batch restart） |

## 7. fileReceptionTimeoutJob / fileDistributionTimeoutJob / fileDistributionRetryJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `timeoutMinutes` | 否 | `1440` | 正整数 |
| `minutesInterval` | 否 | `5` | 正整数（重试扫描窗口） |

## 8. dagOrchestratorJob
| 参数 | 必填 | 默认 | 规则 |
|---|---|---|---|
| `dagId` | 否 | `default-main-pipeline` | DAG 定义 ID（`dag_definition.dag_id`） |
| `batchDate` | 否 | 当天日期 | 推荐 `yyyy-MM-dd` |
| `rerunId` | 否 | 空 | DAG 重跑标识 |

## 非法参数处理约定
- 缺失必填参数: 任务直接失败，写入 `dlq_records` 并记录 `error_code=INVALID_PARAM`。
- 参数格式错误: 不重试，标记为可人工修复后重放。
- 路径不可读/不可写: 记为外部依赖错误，可重试。
