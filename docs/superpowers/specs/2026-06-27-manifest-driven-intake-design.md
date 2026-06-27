# 设计:清单(manifest)驱动入库(到达组触发 + 控制文件对账)

> 缺口 #3 + #6 合并。日期 2026-06-27。状态:已批准设计,待写实现计划。

## 目标

上游送一个控制文件(manifest)列出本批期望文件 + 各文件期望条数/校验和;系统等这组文件到齐且校验通过才触发导入,缺文件/条数不符则告警挂起。严格单体单租户。

## 现状(file:line,纠正前提)

- **接收链路 checksum 已是 MD5**:`FileReceptionService.calculateHash` 用 MessageDigest MD5(`:309`),存 `file_reception_queue.file_hash`(`FileReceptionQueue.java:40`)。(`FileImportRecordReader` 的 Adler32 是行质量门,与文件级 MD5 是两套,不冲突。)
- 逐文件流转:receiveFile(RECEIVED)→ monitor `fileReceptionTasklet` 每 10min 轮询 findPendingFiles → verify → completed(`OperationalTaskJobConfig.java:176-221`)。**无"等一组"概念。**
- 任务调度走 DB `task_definition`+`task_trigger`(`V1_0:127-152`)。告警表 `file_alert_log` 有 alert_type CHECK 约束(`V1_33:56`,扩约束才能加组级类型)。最新迁移 V1_37,新迁移从 V1_38。
- ReconcileJobConfig 是事后行内容对账,非入口,不复用。

## 方案

- **manifest 格式 = JSON**(项目重度用 Jackson;嵌套清单 + 可选字段自然)。备选 CSV 留 ManifestParser SPI 扩展位,v1 只做 JSON。manifest 到达靠现有 .done marker / 固定后缀 `.manifest.json`。
- **到达组建模 = 新表 `reception_group` + `reception_group_member`**;给 `file_reception_queue` 加可空 `reception_group_id`(非组文件为 NULL,完全兼容现有逐文件路径)。
- **触发判定 = 轮询**(复用 DB 轮询架构):新增独立 task `reception-group-monitor`(receptionGroupJob,FIXED_RATE,周期可短于 10min);扫 WAITING_FILES 组 → required 成员到齐 → 对账 → 放行触发导入。备选半事件(receive 后同步 evaluate)留接口位,v1 纯轮询。
- **对账时机 = 到齐后、触发导入前**:校验文件存在+ready、实际记录数==expected(NULL 跳过)、实际 MD5==checksum(复用已存 file_hash,无需重算)。

## 范围边界

**做**:manifest 字段(manifestId/sourceSystem/bizDate/files[].{fileName,expectedRecordCount?,checksum?,checksumAlgorithm?,required});缺文件超时 → 组 EXPIRED 挂起(不删)+告警;required 缺任一不触发,optional 缺失不阻塞;checksum v1 只 MD5(其它 fail-fast);记录数对账时单独计数(跳表头/空行);组 COMPLETE 后逐成员触发各自已配置 import job。灰度开关 `batch.file.reception.group.enabled` 默认 false。

**不做(YAGNI)**:通配/正则文件名匹配、文件大小期望、列级 schema、跨组依赖;自动补抓/重传;部分批先导入;SHA-256(留字段扩展);**bundle**——本设计只"等齐+对账+放行",放行后每文件仍独立导入,"一束作为一个执行单元"归 #1。

## 组件/接口

```
manifest/ ManifestParser(SPI) + JsonManifestParser + ParsedManifest(DTO)
model/ ReceptionGroup + ReceptionGroupMember + ReceptionGroupStatus(WAITING_FILES/COMPLETE/DISPATCHED/EXPIRED/FAILED)
repository/ ReceptionGroupRepository + ReceptionGroupMemberRepository
service/ ReceptionGroupService(registerFromManifest 幂等建组 + bindArrivedFile)
service/ ReceptionGroupCompletionService(evaluate(groupId) —— 单入口,轮询与未来事件都调)
service/ ManifestReconcileService(reconcile(member): 存在性 + countDataLines + MD5)
batch/config/ OperationalTaskJobConfig 新增 receptionGroupJob(照搬 fileReceptionTasklet 模式)
```

衔接:FileReceptionService.receiveFile 识别 manifest → parser + registerFromManifest(manifest 本身不进普通处理);数据文件到达后 bind;告警写 file_alert_log(扩 alert_type GROUP_INCOMPLETE/GROUP_RECONCILE_FAIL);组 COMPLETE 后沿用现有按文件导入触发,组置 DISPATCHED。配置 `batch.file.reception.group.{enabled,manifest-suffix,poll-rate-ms,ttl-minutes,reconcile.*}`。

## 文件清单 + Flyway

新增:manifest/(3)、model/(3)、repository/(2)、service/(3)、OperationalTaskJobConfig 改。
Flyway 从 V1_38:
- `V1_38__reception_group_and_manifest.sql`:reception_group + reception_group_member(UNIQUE(group_id, expected_file_name))+ file_reception_queue 加 reception_group_id 列+index + 扩 file_alert_log CHECK(加两类型)+ seed reception-group-monitor task(V1_0 风格 ON CONFLICT/NOT EXISTS 幂等)。

## 风险

1. 轮询延迟:group poll 周期设短(1-2min);留半事件接口位。
2. 组永不齐:必须 deadline + EXPIRED 挂起态(不删)+告警;不自动重建(幂等冲突)。
3. checksum:复用接收的 MD5,零重算天然对齐;manifest 声明非 MD5 v1 fail-fast;别误并入 Adler32 行质量门。
4. 与逐文件兼容:reception_group_id 可空 + enabled 开关 + manifest 独立识别;monitor 只处理 group_id NOT NULL 成员,普通 RECEIVED 仍走 fileReceptionTasklet;数据文件可能先于 manifest 到达 → registerFromManifest 要回扫已到达未绑定的同名 queue 行。
5. 幂等:manifest_id UNIQUE;重复到达忽略+告警,不重建/不复活 DISPATCHED 组;文件名 uk_file_name_received 已有约束,组绑定容忍"已存在"复用。

## 测试计划(IT,Testcontainers PG)

复用 ReconcileJobConfigIT/FileReceptionQueueRepositoryIT 模式,三主场景:① 齐且对账通过 → 组 COMPLETE/DISPATCHED、reconcile PASS、导入触发;② 缺文件 → WAITING_FILES、arrived 1/2,过 ttl 后 EXPIRED + GROUP_INCOMPLETE 告警;③ 条数/checksum 不符 → FAILED + GROUP_RECONCILE_FAIL,记 mismatch,不触发导入。
补单测:JsonManifestParser(合法/缺必填/非法 algorithm)、evaluate(required vs optional)、幂等(同 manifestId 二次 register 不重建)。
