# 设计:声明式映射(#2)+ 成组协调 bundle(#1)

> 缺口 #2 + #1。日期 2026-06-27。状态:已批准设计,待写实现计划。**必须分期**,#2 是地基先做,#1 后置。

## 目标

- **#2**:把"文件字段→表字段"的映射与基础转换从硬编码 Java 改为**配置声明**,让加 feed 不改代码。
- **#1**:把 K 个关联的导入/分发绑成一个逻辑作业单元,有共享状态(整组成功/失败/部分),可作为一个单位观测与重跑。

严格单体单租户,不做规则引擎/脚本引擎,不做组级原子补偿。

## 现状(file:line)

- 导入链贴死固定模型:FileRecord(id/name/description/lineNo)→ `FileImportRecordProcessor.java:27` 硬编码 name.toUpperCase → ImportContext.buildBusinessKey(`ImportContext.java:9` business_key=name:batchDate)→ `BatchChunkImportStrategy.java:52-58` 一对一塞固定四列。dedup/唯一索引 uk_import_biz_batch_part(`ImportedRecordPartitioned.java:23`)/checksum 全靠 name 假设。
- 导出硬编码:`DataExportJobConfig.java:39`(默认 SQL 5 列)、mapRow(:114)、FieldExtractor(:135)、header(:147)。
- **DAG 已是成熟多任务编排**:`DagOrchestratorService` + dag_definition/node/run/node_run,支持依赖/fail-fast/SKIP-FAIL-IGNORE/PARTIAL 汇总/rerunId;节点执行=同步 jobLauncher.run(串行)。
- **business_job_instance** 已是"一次发起=一行"观测模型:run_key(幂等)、request_payload/result_summary(JSONB)、batch_no、related_file_id、状态机(含 PARTIAL_SUCCESS),有子表 job_step_instance/job_execution_log。
- task_parameter 是 EAV(param_value VARCHAR(1000))。最新 Flyway V1_37,新迁移从 V1_38。

## 方案

### #2 声明式映射:新表 + 通用 RowData + 配置驱动 Processor/Writer

不复用 task_parameter EAV(一行只塞一个 1000 字符,结构藏进字符串);不用 JSON 配置列(失去行级唯一约束/单字段改)。**专表最干净**:

```
feed_definition(feed_id, feed_name, format, delimiter, has_header, target_table[v1固定], business_key_fields, enabled)
field_mapping(id, feed_id, source_column, target_field, transform_op, transform_arg, required, order_no, enabled)
  UNIQUE(feed_id, target_field)
```

运行时:Reader 增加"通用行"产出路径(`RowData = Map<String,Object> + lineNo`,旧 FileRecord 路径保留);`MappingEngine`(纯函数)吃 List<FieldMapping> + 原始行 → mappedRow(应用算子 + 必填校验);配置驱动 Writer 按 business_key_fields 拼 business_key 落库。

**算子白名单(6 个,够用即止)**:TRIM / UPPER / LOWER / DATE_FORMAT(arg=源格式) / DEFAULT(arg=缺省值) / REQUIRED(约束)。不做正则/表达式/跨字段/查表 enrich(需要则仍写 Java,留 SPI 逃生口)。

### #1 bundle:复用 DAG + job_instance,不造第二套状态机

**bundle 与 DAG 的关系**:DAG=有向依赖(A→B→C);bundle=无依赖同质 fan-out(K 个导入彼此独立、整体成败)。DagOrchestrator 对无依赖退化兼容(task_dependency 空 → 一轮内并行判定)。**所以 bundle = 一个"扁平 DAG"(全 node 无依赖)**,不另起执行引擎。

- `bundle_definition` + `bundle_member`(薄表,本质=预置的扁平 dag_definition + N node 的业务别名)。
- 一次 bundle 触发 = 写一行 job_instance(trigger_source=BUNDLE,run_key=bundleId:batchDate 幂等),result_summary JSONB 汇总成员状态。
- 执行委托 DagOrchestratorService.executeDag(扁平 dag),dag_run.id 回填 job_instance.request_payload,组状态由 dag_run.status 映射(SUCCESS→COMPLETED/PARTIAL→PARTIAL_SUCCESS/FAILED→FAILED)。
- 组重跑复用 DAG rerunId(v1 整组重跑,v2 只重跑失败节点)。

(与单体外 ADR-046"轻量版=一个 job_instance + K 独立 partition,不加束状态机"结论一致。)

## 范围边界

- #2 动态字段:Reader→Processor 走通用 Map;但 v1 **落库目标仍固定** imported_records_partition 现有列 + 新 `attributes JSONB` 列承接多余字段。不做映射到任意动态表/动态加列。
- #1 v1 **只做"组观测 + 组重跑"**;明确不做跨成员事务原子性、失败回滚已成功成员的补偿(fan-out 幂等,部分成功+重跑补齐更符批处理语义)。

## 核心模型改动 + 兼容性

- **FileRecord 不删不改签名**,旁路新增 RowData;Processor/Writer 各出配置驱动新实现(MappingImportProcessor/MappingImportWriter),旧实现保留。
- **唯一侵入核心表的点(低风险加列)**:imported_records_partition 加 `attributes JSONB NULL`(承接超出 name/description 的字段);唯一索引不变;business_key 构成从写死 name:batchDate 改为按 business_key_fields 配置拼装(集中在 ImportContext 一处)。
- **灰度共存**:feed_definition 存在且 enabled → 走 mapping 链,否则旧硬编码链;FileImportJobConfig 按 job param feedId 选实现,默认旧链,旧 importJob/dataExportJob 零变化。导出对称(field_mapping 反向生成 mapRow/header),v1 可后置。

## 文件清单 + Flyway

新增(#2):model FieldMapping/FeedDefinition/RowData、repository ×2、mapping MappingEngine/TransformOp、batch MappingImportProcessor/MappingImportWriter;改 FileImportRecordReader/FileImportJobConfig/ImportContext。
新增(#1):model BundleDefinition/BundleMember、repository ×2、service BundleOrchestratorService;可选改 DagOrchestratorService(暴露只重跑失败节点入口,v1 可不改)。
Flyway 从 V1_38:`V1_38__feed_and_field_mapping.sql`(+ seed 对齐现有 name/description 的示例 feed)、`V1_39__imported_records_attributes_jsonb.sql`(纯加可空列)、`V1_40__bundle_definition.sql`(不加 bundle 状态表,借 job_instance)。

## 分期建议(每期可独立交付验证)

- **Phase 1(地基,先做,低风险)= 仅 #2 导入侧映射**:feed/field_mapping(V1_38)+ RowData + MappingEngine(6 算子)+ 配置驱动 import processor/writer + attributes JSONB(V1_39)+ business_key 多字段化。**路由默认旧链**;seed 一个复刻现有行为的 feed 做对照。验收门槛=同份 CSV 旧 importJob 与新 feed 路径落库**字节级一致**,且加"两列源 map 到 name+attributes"的新 feed 不改 Java 即跑通。
- **Phase 2 = #1 bundle(组观测 + 组重跑)**:bundle_definition/member(V1_40)+ BundleOrchestratorService(bundle→扁平 dag、写 job_instance、委托 DagOrchestrator、汇总)。v1 整组重跑,失败成员靠 dag_node_run 定位。验收=3 feed 绑一组、1 个坏 → bundle PARTIAL_SUCCESS、一处看全组、整组重跑转 COMPLETED;不碰 Phase 1 import 链。
- **Phase 3(后置,YAGNI 把关)**:导出侧映射配置化 + bundle 增量重跑 + 分发(文件×目标)入组观测。组级原子/补偿仍不做。

## 风险

1. **泛化字段对幂等/dedup/checksum 冲击(最高)**:business_key 从写死改配置拼装,配错字段集 → 幂等口径漂移(重复落库或误判丢数据)。缓解:拼装函数集中 ImportContext 一处;seed feed 复刻旧口径 + 新旧落库 diff 对照测试;attributes JSONB 不参与 business_key(避免序列化顺序影响幂等键)。
2. 动核心模型回归面:改动锁在"旁路 + 默认旧链 + 加列不改列";绝不改 FileRecord 签名/uk_import_biz_batch_part/旧 job bean;Phase 1 验收=旧链字节级一致。
3. bundle 与 DAG 概念重叠(产品风险):文档/UI 明确 DAG=有依赖流程、bundle=无依赖组,bundle 底层是扁平 DAG 对用户隐藏;bundle UI 只暴露加成员/看状态/整组重跑。
4. DagOrchestrator 串行阻塞:executeNode 同步 jobLauncher.run,K 成员串行,大 fan-out 墙钟线性增长(v1 接受低千级;并行是 DagOrchestrator 独立增强)。
5. attributes JSONB:纯加可空列 PG 不重写表;JPA @JdbcTypeCode(SqlTypes.JSON) job_instance 已有先例,低风险。
