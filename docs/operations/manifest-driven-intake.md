# 清单驱动入库(Manifest-Driven Intake)

> 适用范围:多文件成组到达、需要"到齐 + 对账后再统一入库"的批次场景(对应需求 #3 + #6)。
> 与逐文件接收路径完全兼容,默认关闭、灰度启用。

## 1. 概述

传统逐文件路径:每个文件到达即独立触发处理。

清单驱动入库引入"**到达组(reception group)**":由一份清单(manifest)声明本批次预期包含哪些文件、各自预期条数与校验和;系统按清单**建组**并登记预期成员,文件陆续到达时**绑定**到组,待**必填成员全部到达**后统一**对账**,全部通过才**触发导入(DISPATCHED)**,任一不符则**置失败并告警**,超时未齐则**过期挂起并告警**。

适用于:一批关联文件必须齐套且校验一致才允许入库(防止半套/错套数据污染下游)。

## 2. Manifest JSON 格式

清单是一个 JSON 文件,通过约定后缀(默认 `.manifest.json`)落入接收目录触发建组。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `manifestId` | string | 清单唯一标识。同 `manifestId` 重复投递为**幂等**,不重建组。 |
| `sourceSystem` | string | 来源系统标识,用于告警归类。 |
| `bizDate` | string | 业务日期(自由格式字符串,如 `2026-06-27`)。 |
| `files[]` | array | 预期文件清单。 |
| `files[].fileName` | string | 预期文件名,绑定时按文件名精确匹配到达队列行。 |
| `files[].expectedRecordCount` | number/null | 预期数据条数。为 null 则不做条数对账。 |
| `files[].checksum` | string/null | 预期校验和(十六进制)。为 null 则不做校验和对账。 |
| `files[].checksumAlgorithm` | string | 校验算法,目前为 `MD5`(复用接收链路已计算的文件 hash)。 |
| `files[].required` | boolean | 是否必填。仅"必填成员"全部到达才推进到齐判定;非必填缺失不阻塞。 |

### 示例

```json
{
  "manifestId": "SETTLE-20260627-001",
  "sourceSystem": "CORE_SETTLEMENT",
  "bizDate": "2026-06-27",
  "files": [
    {
      "fileName": "settle_detail_20260627.csv",
      "expectedRecordCount": 1200000,
      "checksum": "9e107d9d372bb6826bd81d3542a419d6",
      "checksumAlgorithm": "MD5",
      "required": true
    },
    {
      "fileName": "settle_summary_20260627.csv",
      "expectedRecordCount": 42,
      "checksum": null,
      "checksumAlgorithm": "MD5",
      "required": true
    },
    {
      "fileName": "settle_addendum_20260627.csv",
      "expectedRecordCount": null,
      "checksum": null,
      "checksumAlgorithm": "MD5",
      "required": false
    }
  ]
}
```

## 3. 触发建组

- 接收链路检测到文件名以 `manifest-suffix`(默认 `.manifest.json`)结尾时,解析其内容并调用 `ReceptionGroupService.registerFromManifest` 建组、登记预期成员。
- 建组时会**回扫**已先于清单到达的同名队列行并自动绑定(乱序到达安全)。
- 同 `manifestId` 重复建组幂等返回,不重建、不重复登记成员。

## 4. 配置项(`batch.file.reception.group.*`)

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `false` | 总开关。**默认关闭**,灰度启用;关闭时接收链路不识别 manifest,只走逐文件路径。 |
| `manifest-suffix` | `.manifest.json` | 触发建组的文件名后缀。 |
| `poll-rate-ms` | (随监控任务) | 到达组监控任务的轮询频率(由 `reception-group-monitor` 任务的 FIXED_RATE 控制,种子值 120000ms / 2 分钟)。 |
| `ttl-minutes` | `360` | 组存活时长。建组时 `deadline = now + ttl-minutes`,超时未齐则置 EXPIRED。 |

## 5. 组状态机

```
                ┌──────────────────────────────────────────────┐
                │                                              │
   建组 ──►  WAITING_FILES ──(必填全到 + 对账全通过)──► DISPATCHED(触发导入)
                │  │
                │  └──(必填全到 + 任一对账不符)──► FAILED(告警 GROUP_RECONCILE_FAIL)
                │
                └──(必填未全到 + 已过 deadline)──► EXPIRED(告警 GROUP_INCOMPLETE,需人工)
```

- `WAITING_FILES`:初始态,等待文件到齐。
- `COMPLETE` / `DISPATCHED`:到齐且对账通过,统一触发各成员对应队列行的导入。
- `EXPIRED`:超时未齐,**挂起需人工介入**(补文件/重投清单/手工处置)。
- `FAILED`:已到齐但对账不通过(条数或校验和不符),未触发导入,需人工核查。

注:`evaluate` 仅对 `WAITING_FILES` 的组生效,终态(DISPATCHED/EXPIRED/FAILED)不再回退,幂等安全。

## 6. 对账

到齐(必填成员全部到达)后逐成员对账,回填 `reconcile_status`(PASS/FAIL):

1. **文件存在性**:绑定的队列行须存在;缺失即 FAIL。
2. **条数**:`expectedRecordCount` 非空时,按 UTF-8 读取、跳过首行表头、仅计非空白行的口径统计实际条数,回填 `actual_record_count`,与预期比对。
3. **MD5 校验和**:`checksum` 非空时,直接复用接收链路已计算并落库的 `file_hash`(不二次扫文件),大小写不敏感比对。

全部成员 PASS 才置 DISPATCHED 并触发导入;任一 FAIL 即置 FAILED 并告警。

## 7. 监控任务(运维需手动启用)

- 种子任务 `reception-group-monitor`(job `receptionGroupJob`)按 FIXED_RATE(默认 2 分钟)巡检 `WAITING_FILES` 组,执行到齐判定与对账。
- **该任务在 `task_definition` 中 `enabled = FALSE`,默认禁用**。上线灰度时需运维显式启用(并确认 `batch.file.reception.group.enabled=true`)。

## 8. 告警

| 告警类型 | 触发条件 | 严重级 | 处置 |
| --- | --- | --- | --- |
| `GROUP_INCOMPLETE` | 超时(过 deadline)仍未齐 | CRITICAL | 人工补齐文件 / 重投清单 / 决定放弃本批 |
| `GROUP_RECONCILE_FAIL` | 到齐但条数或校验和不符 | CRITICAL | 核查源端文件,修正后重投 |

告警写入 `file_alert_log`,payload 含 `manifestId`、到达/总数或 mismatch 明细,复用既有告警分发渠道。

## 9. 与逐文件路径的兼容性

- 非组文件:`file_reception_queue.reception_group_id` 为 `NULL`,行为不变,走原逐文件处理。
- 组文件:绑定时回填 `reception_group_id`,由到达组统一判定,**到齐对账通过后**才触发导入,避免半套数据提前入库。
- 总开关关闭(`enabled=false`)时,系统忽略 manifest 后缀,全量走逐文件路径,可安全回退。
